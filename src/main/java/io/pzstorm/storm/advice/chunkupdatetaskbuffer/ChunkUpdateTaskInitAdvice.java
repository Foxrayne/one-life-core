package io.pzstorm.storm.advice.chunkupdatetaskbuffer;

import net.bytebuddy.asm.Advice;

import zombie.iso.IsoChunk;

import java.nio.ByteBuffer;

/**
 * Inlined at the top of {@code zombie.pathfind.nativeCode.ChunkUpdateTask.init(IsoChunk)}.
 *
 * <p>Vanilla sizes the task's direct {@code ByteBuffer} by climbing a 256-byte ladder: {@code
 * ensureCapacity(bb)} is called once per square, inside {@code for level { for 8 { for 8 } } }, and
 * whenever fewer than 256 bytes remain it allocates a <em>new</em> direct buffer, copies the old
 * contents in and orphans the old one. The buffer is a field on a pooled task, so a recycled task
 * pays nothing — but an evicted cell's reconnect pushes 64 {@code addChunkToWorld} calls at once
 * against a pool that reads 10 to 80 free, so most tasks in a burst are fresh and climb.
 *
 * <p>Measured on a 100-125 player server, 2026-09-06: <b>5.0 direct buffers per chunk add</b>,
 * sawtoothing between roughly 1M and 2.8M live buffers as ZGC majors run their {@code Cleaner}s.
 * Each buffer is a {@code malloc} whose native memory only comes back when its cleaner runs, which
 * needs reference processing, which on a large heap is minutes away.
 *
 * <p>This advice sizes the buffer once, before vanilla runs, from the chunk's own level count.
 * Vanilla's {@code ensureCapacity} then finds the capacity already sufficient and hands the same
 * buffer back every time, so the ladder becomes a no-op and a fresh task allocates exactly once.
 * Measured on the real bytecode: a 3-level chunk goes from 6 direct buffers to 1, a 64-level chunk
 * from 98 to 1.
 *
 * <p><b>Fail-safe by construction.</b> This only ever makes the buffer <em>bigger</em>; vanilla's
 * own {@code ensureCapacity} still runs unmodified behind it. If the size computed here is ever too
 * small — a new build, a layout change, a chunk shape nobody anticipated — the vanilla ladder takes
 * over exactly as it does today. There is no path by which this produces a short buffer.
 *
 * <p>Nothing about the serialised bytes changes: {@code init} ends in {@code flip()}, so the native
 * side reads position..limit and never the capacity. Vanilla already hands it over-sized buffers
 * routinely, because a pooled task keeps whatever high-water mark a previous, deeper chunk left.
 */
public class ChunkUpdateTaskInitAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) IsoChunk chunk,
            @Advice.FieldValue(value = "bb", readOnly = false) ByteBuffer bb) {

        if (chunk == null) {
            // Vanilla dereferences the chunk on its first line; let it throw where it always did.
            return;
        }

        int levels = chunk.maxLevel - chunk.minLevel + 1;
        if (levels < 1) {
            // Degenerate range: vanilla writes only the header and the sloped-square count.
            // Leave the ladder in charge rather than sizing off a nonsense number.
            return;
        }
        if (levels > 64) {
            // LEVELS_PER_CHUNK. A wider range cannot be what init is about to serialise, so clamp
            // rather than allocate off a bad read; the ladder still covers whatever it does write.
            levels = 64;
        }

        // Vanilla's write order for one chunk:
        //    8 bytes  header               putInt(minLevel + 32), putInt(maxLevel + 32)
        //    6 bytes  per square           putInt(bits), putShort(cost)   x  levels * 64
        //    2 bytes  sloped-square count  putShort(i)
        //   12 bytes  per sloped square    appended from the shared bbTemp at the end
        //
        // ensureCapacity(bb) runs BEFORE each square and demands 256 free bytes, so to keep it
        // quiet through the last square the capacity has to cover the position it sees there plus
        // a whole block: 8 + 6 * (squares - 1) + 256, i.e. 6 * squares + 258. Asking for
        // 6 * squares + 266 clears that and leaves 256 bytes past the sloped-square count, room
        // for 21 sloped squares before vanilla's separate tail realloc has anything to do.
        int squares = levels * 64;
        int needed = 8 + squares * 6 + 2 + 256;

        // Round up to a whole 256-byte block, the way vanilla's own bufferSize() does, so the
        // capacities this produces stay on the ladder's grid.
        needed = (needed + 255) / 256 * 256;

        if (bb == null || bb.capacity() < needed) {
            bb = ByteBuffer.allocateDirect(needed);
        }
    }
}
