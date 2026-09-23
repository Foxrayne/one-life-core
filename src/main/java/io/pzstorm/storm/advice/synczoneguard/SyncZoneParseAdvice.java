package io.pzstorm.storm.advice.synczoneguard;

import io.pzstorm.storm.patch.fixes.AnimalZoneSafehouseGuard;
import net.bytebuddy.asm.Advice;
import zombie.iso.areas.DesignationZone;

/**
 * Inlined around {@code zombie.network.packets.SyncZonePacket.parse}. Vanilla's body applies an add
 * or a rename as it reads it, so the entry advice snapshots the state it is about to overwrite and
 * the exit advice hands the parsed edit to {@link AnimalZoneSafehouseGuard}. A vetoed edit nulls
 * {@code designationZone}, which makes vanilla's {@code processServer} a no-op.
 *
 * <p>Reader and connection are typed {@code Object} so the inlined call site does not encode a
 * checkcast against a game class.
 */
public class SyncZoneParseAdvice {

    @Advice.OnMethodEnter
    public static Object onEnter(@Advice.Argument(0) Object reader) {
        return AnimalZoneSafehouseGuard.beforeParse(reader);
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Enter Object pending,
            @Advice.Argument(1) Object connection,
            @Advice.FieldValue(value = "designationZone", readOnly = false) DesignationZone zone) {
        if (pending != null && AnimalZoneSafehouseGuard.afterParse(pending, zone, connection)) {
            zone = null;
        }
    }
}
