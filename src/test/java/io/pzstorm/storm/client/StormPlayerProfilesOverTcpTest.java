package io.pzstorm.storm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.pzstorm.storm.UnitTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StormPlayerProfilesOverTcpTest implements UnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ArrayNode payload(int count) {
        ArrayNode slots = JSON.createArrayNode();
        for (int i = 0; i < count; i++) {
            slots.addObject().put("data", "AQID").put("worldVersion", 241)
                .put("x", 100.5 + i).put("y", 200.25).put("z", -1)
                .put("isDead", i == 2).put("splitUsername", "split-" + i);
        }
        return slots;
    }
    @Test void freshPlayerUsesVanillaDefaults() {
        var p = StormPlayerProfilesOverTcp.decodeProfiles(payload(0), "owner", "server", 241);
        assertTrue(p.isLoaded); assertEquals(0, p.playerCount);
        assertEquals(241, p.worldVersion[0]); assertEquals("owner", p.username[0]);
        assertEquals("server", p.server); assertNull(p.character[0]);
    }
    @Test void allFourSlotsPreserveBytesNamesAndCoordinates() {
        var p = StormPlayerProfilesOverTcp.decodeProfiles(payload(4), "owner", "server", 0);
        assertTrue(p.isLoaded); assertEquals(4, p.playerCount);
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(new byte[]{1,2,3}, p.character[i]);
            assertEquals(i == 0 ? "owner" : "split-" + i, p.username[i]);
            assertEquals(100.5f + i, p.x[i]); assertEquals(200.25f, p.y[i]);
            assertEquals(-1f, p.z[i]); assertEquals(i == 2, p.isDead[i]);
            assertEquals(241, p.worldVersion[i]);
        }
    }
    @Test void malformedAndOldRepliesCannotProduceLoadedProfiles() {
        assertThrows(IllegalArgumentException.class, () ->
            StormPlayerProfilesOverTcp.decodeProfiles(payload(5), "owner", "server", 0));
        var old = payload(2); ((com.fasterxml.jackson.databind.node.ObjectNode) old.get(1)).remove("splitUsername");
        assertThrows(IllegalArgumentException.class, () ->
            StormPlayerProfilesOverTcp.decodeProfiles(old, "owner", "server", 0));
        var corrupt = payload(2); ((com.fasterxml.jackson.databind.node.ObjectNode) corrupt.get(1)).put("data", "!");
        assertThrows(IllegalArgumentException.class, () ->
            StormPlayerProfilesOverTcp.decodeProfiles(corrupt, "owner", "server", 0));
        assertThrows(IllegalArgumentException.class, () ->
            StormPlayerProfilesOverTcp.decodeProfiles(JSON.createObjectNode(), "owner", "server", 0));
    }
}
