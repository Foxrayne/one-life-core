package io.pzstorm.storm.advice.vehiclesdbchunkkey;

import io.pzstorm.storm.patch.fixes.VehiclesDbChunkKeyFix;
import net.bytebuddy.asm.Advice;

/**
 * Inlined at the end of {@code VehiclesDB2$VehicleBuffer.set(BaseVehicle)}. Replaces the {@code
 * wx/wy} the body copied from the vehicle's (possibly stale or recycled) chunk pointer with the
 * chunk that contains the {@code x,y} the same body just captured. See {@link
 * io.pzstorm.storm.patch.fixes.VehiclesDbChunkKeyPatch}.
 *
 * <p>No {@code onThrowable}: if the body throws (vanilla NPEs on a null chunk), the buffer is never
 * queued and there is nothing to fix.
 */
public class VehicleBufferSetAdvice {

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.FieldValue("id") int id,
            @Advice.FieldValue("x") float x,
            @Advice.FieldValue("y") float y,
            @Advice.FieldValue(value = "wx", readOnly = false) int wx,
            @Advice.FieldValue(value = "wy", readOnly = false) int wy) {
        int realWx = VehiclesDbChunkKeyFix.chunkCoord(x);
        int realWy = VehiclesDbChunkKeyFix.chunkCoord(y);
        if (wx != realWx || wy != realWy) {
            VehiclesDbChunkKeyFix.logRefiled(id, wx, wy, realWx, realWy, x, y);
            wx = realWx;
            wy = realWy;
        }
    }
}
