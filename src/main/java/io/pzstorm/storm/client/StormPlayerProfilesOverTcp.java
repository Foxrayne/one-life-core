package io.pzstorm.storm.client;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.savefile.ClientPlayerDB;

/**
 * Client half of the player-profile migration to TCP. Called at the top of {@code
 * ClientPlayerDB.clientLoadNetworkPlayer} (loader thread): on success it installs a fully loaded
 * {@code NetworkCharacterProfile}, which the vanilla method's own fast path then returns without
 * sending any {@code LoadPlayerProfile} packets. On any failure it installs nothing and the vanilla
 * UDP request/poll path runs untouched — fallback is automatic, no skip logic needed.
 */
public final class StormPlayerProfilesOverTcp {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StormPlayerProfilesOverTcp() {}

    public static void tryPrefetch() {
        try {
            ClientPlayerDB.NetworkCharacterProfile existing =
                    ClientPlayerDB.getInstance().networkProfile;
            if (existing != null
                    && existing.isLoaded
                    && existing.username != null
                    && existing.username.length > 0
                    && GameClient.username.equals(existing.username[0])
                    && existing.server != null
                    && existing.server.equals(GameClient.ip)) {
                // Vanilla's fast path will already return without network.
                return;
            }
            HttpRequest.Builder builder =
                    StormTcpChannel.authenticatedRequest("/storm/game/player-profiles");
            if (builder == null) {
                return;
            }
            HttpResponse<byte[]> response = StormTcpChannel.send(builder.GET().build());
            if (response.statusCode() != 200) {
                LOGGER.info(
                        "TCP player profiles returned {}; falling back to UDP",
                        response.statusCode());
                return;
            }
            JsonNode profiles =
                    MAPPER.readTree(new String(response.body(), StandardCharsets.UTF_8))
                            .path("profiles");

            ClientPlayerDB.NetworkCharacterProfile profile = decodeProfiles(
                    profiles, GameClient.username, GameClient.ip,
                    profiles.isArray() && profiles.isEmpty() ? IsoWorld.getWorldVersion() : 0);
            ClientPlayerDB.getInstance().networkProfile = profile;
            LOGGER.info("Player profiles ({}) received over TCP", profile.playerCount);
        } catch (Throwable t) {
            LOGGER.error("Player profiles over TCP failed; falling back to UDP", t);
        }
    }

    /** Decode completely before publishing; malformed replies leave the UDP fallback untouched. */
    static ClientPlayerDB.NetworkCharacterProfile decodeProfiles(
            JsonNode profiles, String username, String server, int freshWorldVersion) {
            ClientPlayerDB.NetworkCharacterProfile profile = new ClientPlayerDB.NetworkCharacterProfile();
            profile.username[0] = username;
            profile.server = server;
            if (!profiles.isArray() || profiles.size() > profile.character.length) {
                throw new IllegalArgumentException("Invalid player profile slot count");
            }
            profile.playerCount = profiles.size();
            for (int i = 0; i < profiles.size() && i < profile.character.length; i++) {
                JsonNode p = profiles.get(i);
                profile.character[i] = Base64.getDecoder().decode(p.get("data").asText());
                profile.worldVersion[i] = p.get("worldVersion").asInt();
                profile.x[i] = (float) p.get("x").asDouble();
                profile.y[i] = (float) p.get("y").asDouble();
                profile.z[i] = (float) p.get("z").asDouble();
                profile.isDead[i] = p.get("isDead").asBoolean();
                if (i > 0) {
                    if (!p.has("splitUsername")) {
                        throw new IllegalArgumentException("Server lacks 42.21 split-player names");
                    }
                    profile.username[i] = p.path("splitUsername").asText("");
                }
            }
            if (profile.playerCount == 0) {
                // Same default vanilla applies for a fresh character on this server.
                profile.worldVersion[0] = freshWorldVersion;
            }
            profile.isLoaded = true;
            return profile;
    }
}
