package io.pzstorm.storm.patch.fixes;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.metrics.StormPerformanceSandboxMetrics;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.RakNetPeerInterface;
import zombie.core.raknet.UdpConnection;
import zombie.iso.areas.DesignationZone;
import zombie.iso.areas.DesignationZoneAnimal;
import zombie.iso.areas.SafeHouse;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.packets.SyncZonePacket;

/**
 * Server-side authority over animal-zone edits, gated on {@code
 * Storm.AnimalZoneSafehouseProtection} (default off = vanilla).
 *
 * <h2>The vanilla gap</h2>
 *
 * <p>A {@code DesignationZoneAnimal} has no owner. {@code SyncZonePacket.parse} applies an add or a
 * rename the moment it reads it, and {@code processServer} relays every edit and applies every
 * removal with no check beyond {@code Capability.LoginOnServer}. Removal also cascades through
 * every zone touching the target edge to edge, so any player can delete any pen, and a player
 * deleting their own pen deletes the neighbour's.
 *
 * <h2>The rule</h2>
 *
 * <p>A zone whose rectangle overlaps a safehouse is editable only by that safehouse's owner and
 * members, plus roles holding {@code Capability.CanGoInsideSafehouses}. A zone overlapping several
 * safehouses is editable by anyone allowed in at least one of them. A zone overlapping no safehouse
 * stays editable by everyone.
 *
 * <h2>Mechanism</h2>
 *
 * <p>{@link #beforeParse} peeks the packet header ahead of vanilla's {@code parse} and snapshots
 * what {@code parse} is about to overwrite. {@link #afterParse} then judges the edit and returns
 * {@code true} to veto it; the advice nulls the packet's {@code designationZone} field, which turns
 * vanilla's {@code processServer} into a no-op.
 *
 * <ul>
 *   <li><b>Removal</b> drops only the zones of the connected group the requester may edit. Clients
 *       can only express a whole-group cascade, so the server relays the removal and then re-adds
 *       the protected survivors to every client.
 *   <li><b>Add</b> inside a foreign safehouse is undone on the server. The requester is told to
 *       remove the zone, which cascades on their client, so the rest of the group is re-added to
 *       them.
 *   <li><b>Rename / resize</b> of a protected zone is reverted on the server and the true values
 *       are sent back to the requester.
 * </ul>
 *
 * <p>{@code SyncZone} is {@code RELIABLE} but unordered, and a re-add that overtakes its removal
 * would leave clients without the protected zone. Corrections therefore go out as {@code
 * RELIABLE_ORDERED} on one channel.
 *
 * <p>Packet instances are reused per connection and vanilla's non-designation parse branch leaves
 * {@code designationZone} stale, so a replayed grass-zone packet would re-run the previous edit
 * unjudged. The guard vetoes that branch too; the server never acts on it.
 *
 * <p>Any throw latches {@link #broken} and the packet proceeds as vanilla.
 */
public final class AnimalZoneSafehouseGuard {

    public static final boolean DEFAULT_ENABLED = false;

    private static final byte CORRECTION_ORDERING_CHANNEL = 0;

    /** Header bytes peeked ahead of parse: two booleans and the zone id. */
    private static final int HEADER_BYTES = 2 + Double.BYTES;

    private static volatile boolean enabled = DEFAULT_ENABLED;

    private static volatile boolean broken;

    private static final LongAdder DENIED = new LongAdder();

    private AnimalZoneSafehouseGuard() {}

    public static boolean setEnabled(boolean value) {
        enabled = value;
        StormPerformanceSandboxMetrics.setAnimalZoneSafehouseProtection(value);
        return value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isBroken() {
        return broken;
    }

    public static long getDenied() {
        return DENIED.sum();
    }

    /** Test hook. */
    public static void resetBroken() {
        broken = false;
    }

    /** What {@code parse} is about to do, captured before it mutates server state. */
    public static final class Pending {
        final boolean designation;
        final boolean added;
        final DesignationZone existing;
        final String name;
        final int w;
        final int h;

        Pending(boolean designation, boolean added, DesignationZone existing) {
            this.designation = designation;
            this.added = added;
            this.existing = existing;
            this.name = existing == null ? null : existing.name;
            this.w = existing == null ? 0 : existing.w;
            this.h = existing == null ? 0 : existing.h;
        }
    }

    /** Returns the snapshot for {@link #afterParse}, or {@code null} to leave the packet alone. */
    public static Object beforeParse(Object readerRef) {
        if (broken || !enabled) {
            return null;
        }
        try {
            ByteBuffer bb = ((ByteBufferReader) readerRef).bb;
            int p = bb.position();
            if (bb.remaining() < 1 || bb.get(p) == 0) {
                return new Pending(false, false, null);
            }
            if (bb.remaining() < HEADER_BYTES) {
                return null;
            }
            boolean added = bb.get(p + 1) != 0;
            DesignationZone existing = DesignationZone.getZoneById(bb.getDouble(p + 2));
            return new Pending(true, added, existing);
        } catch (Throwable t) {
            latch(t);
            return null;
        }
    }

    /** Returns {@code true} when the edit is vetoed and the packet must not be processed. */
    public static boolean afterParse(Object pendingRef, Object zoneRef, Object connectionRef) {
        if (broken) {
            return false;
        }
        try {
            Pending pending = (Pending) pendingRef;
            if (!pending.designation) {
                return true;
            }
            if (!(zoneRef instanceof DesignationZoneAnimal zone)) {
                return false;
            }
            UdpConnection connection = (UdpConnection) connectionRef;
            List<String> usernames = usernamesOf(connection);
            boolean bypass = bypasses(connection);

            if (!pending.added) {
                return judgeRemoval(zone, connection, usernames, bypass);
            }
            if (pending.existing == null) {
                return judgeAdd(zone, connection, usernames, bypass);
            }
            return judgeUpdate(pending, zone, connection, usernames, bypass);
        } catch (Throwable t) {
            latch(t);
            return false;
        }
    }

    private static boolean judgeRemoval(
            DesignationZoneAnimal target,
            UdpConnection requester,
            List<String> usernames,
            boolean bypass) {
        ArrayList<DesignationZoneAnimal> group =
                DesignationZoneAnimal.getAllDZones(null, target, null);
        ArrayList<DesignationZoneAnimal> kept = protectedFrom(group, usernames, bypass);
        if (kept.isEmpty()) {
            return false;
        }

        boolean removedAny = false;
        for (int i = 0; i < group.size(); i++) {
            DesignationZoneAnimal zone = group.get(i);
            if (!kept.contains(zone)) {
                detach(zone);
                removedAny = true;
            }
        }
        deny("remove", target, requester, kept.size(), group.size());

        if (removedAny) {
            forEachClient(requester, true, c -> sendOrdered(c, target, false));
            forEachClient(requester, false, c -> sendAll(c, kept));
        } else {
            sendAll(requester, kept);
        }
        return true;
    }

    private static boolean judgeAdd(
            DesignationZoneAnimal created,
            UdpConnection requester,
            List<String> usernames,
            boolean bypass) {
        if (mayEdit(usernames, bypass, created.x, created.y, created.w, created.h)) {
            return false;
        }
        ArrayList<DesignationZoneAnimal> group =
                DesignationZoneAnimal.getAllDZones(null, created, null);
        group.remove(created);
        detach(created);
        deny("add", created, requester, 1, 1);

        sendOrdered(requester, created, false);
        sendAll(requester, group);
        return true;
    }

    private static boolean judgeUpdate(
            Pending pending,
            DesignationZoneAnimal zone,
            UdpConnection requester,
            List<String> usernames,
            boolean bypass) {
        if (mayEdit(usernames, bypass, zone.x, zone.y, pending.w, pending.h)
                && mayEdit(usernames, bypass, zone.x, zone.y, zone.w, zone.h)) {
            return false;
        }
        zone.name = pending.name;
        zone.w = pending.w;
        zone.h = pending.h;
        deny("update", zone, requester, 1, 1);

        sendOrdered(requester, zone, true);
        return true;
    }

    /**
     * The zones of {@code group} the requester may not edit, in group order. Package-visible for
     * tests.
     */
    static ArrayList<DesignationZoneAnimal> protectedFrom(
            List<DesignationZoneAnimal> group, List<String> usernames, boolean bypass) {
        ArrayList<DesignationZoneAnimal> kept = new ArrayList<>();
        for (int i = 0; i < group.size(); i++) {
            DesignationZoneAnimal zone = group.get(i);
            if (!mayEdit(usernames, bypass, zone.x, zone.y, zone.w, zone.h)) {
                kept.add(zone);
            }
        }
        return kept;
    }

    /** Package-visible for tests. */
    static boolean mayEdit(List<String> usernames, boolean bypass, int x, int y, int w, int h) {
        if (bypass) {
            return true;
        }
        boolean overlapsSafehouse = false;
        ArrayList<SafeHouse> safehouses = SafeHouse.getSafehouseList();
        for (int i = 0; i < safehouses.size(); i++) {
            SafeHouse safe = safehouses.get(i);
            if (x < safe.getX2()
                    && x + w > safe.getX()
                    && y < safe.getY2()
                    && y + h > safe.getY()) {
                overlapsSafehouse = true;
                for (int j = 0; j < usernames.size(); j++) {
                    if (safe.playerAllowed(usernames.get(j))) {
                        return true;
                    }
                }
            }
        }
        return !overlapsSafehouse;
    }

    private static List<String> usernamesOf(UdpConnection connection) {
        ArrayList<String> usernames = new ArrayList<>(4);
        for (IsoPlayer player : connection.players) {
            if (player != null && player.getUsername() != null) {
                usernames.add(player.getUsername());
            }
        }
        if (usernames.isEmpty() && connection.getUserName() != null) {
            usernames.add(connection.getUserName());
        }
        return usernames;
    }

    private static boolean bypasses(UdpConnection connection) {
        return connection.getRole() != null
                && connection.getRole().hasCapability(Capability.CanGoInsideSafehouses);
    }

    /** Drops one zone without vanilla's connected-group cascade. */
    private static void detach(DesignationZoneAnimal zone) {
        DesignationZoneAnimal.designationAnimalZoneList.remove(zone);
        DesignationZone.allZones.remove(zone);
    }

    private interface ClientAction {
        void accept(UdpConnection connection);
    }

    private static void forEachClient(
            UdpConnection requester, boolean skipRequester, ClientAction action) {
        for (UdpConnection connection : GameServer.udpEngine.connections) {
            if (!connection.isFullyConnected()) {
                continue;
            }
            if (skipRequester && connection.getConnectedGUID() == requester.getConnectedGUID()) {
                continue;
            }
            action.accept(connection);
        }
    }

    private static void sendAll(UdpConnection connection, List<DesignationZoneAnimal> zones) {
        for (int i = 0; i < zones.size(); i++) {
            sendOrdered(connection, zones.get(i), true);
        }
    }

    private static void sendOrdered(UdpConnection connection, DesignationZone zone, boolean added) {
        SyncZonePacket packet = new SyncZonePacket();
        packet.setData(zone, added);
        ByteBufferWriter b = connection.startPacket();
        try {
            PacketTypes.PacketType.SyncZone.doPacket(b);
            packet.write(b);
            connection.endPacket(
                    PacketTypes.PacketType.SyncZone.packetPriority,
                    RakNetPeerInterface.PacketReliability_RELIABLE_ORDERED,
                    CORRECTION_ORDERING_CHANNEL);
        } catch (Throwable t) {
            connection.cancelPacket();
            throw t;
        }
    }

    private static void deny(
            String action,
            DesignationZone zone,
            UdpConnection requester,
            int protectedCount,
            int groupSize) {
        DENIED.increment();
        LOGGER.info(
                "Storm: animal zone {} denied for {} (steamId {}): zone '{}' id={} at {},{},{} {}x{},"
                        + " {} of {} zones safehouse-protected",
                action,
                requester.getUserName(),
                requester.getSteamId(),
                zone.name,
                zone.id,
                zone.x,
                zone.y,
                zone.z,
                zone.w,
                zone.h,
                protectedCount,
                groupSize);
    }

    private static void latch(Throwable t) {
        broken = true;
        LOGGER.error("Storm: animal zone safehouse guard failed; reverting to vanilla", t);
    }
}
