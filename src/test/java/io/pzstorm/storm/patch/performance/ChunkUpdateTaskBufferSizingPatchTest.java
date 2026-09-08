package io.pzstorm.storm.patch.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

/**
 * Verifies the patched {@code ChunkUpdateTask} bytecode sizes the task buffer up front and leaves
 * the vanilla ladder in place behind it.
 *
 * <p>The advice is fail-safe only because both halves are true at once: it adds one sizing
 * allocation at method entry, and vanilla's own {@code ensureCapacity} still runs, so a size that
 * ever turns out to be too small degrades to today's behaviour instead of producing a short buffer.
 * A patch that inlined correctly but dropped the fallback would pass a "did it transform" check and
 * fail this one.
 *
 * <p>Uses ByteBuddy's bundled ASM (via {@code net.bytebuddy.jar.asm.*}) because the standalone
 * {@code org.ow2.asm:asm:9.1} test dependency is too old to read Java 25 class files.
 */
class ChunkUpdateTaskBufferSizingPatchTest implements UnitTest {

    private static final String CHUNK_UPDATE_TASK = "zombie/pathfind/nativeCode/ChunkUpdateTask";
    private static final String BYTE_BUFFER = "java/nio/ByteBuffer";
    private static final String ISO_CHUNK = "zombie/iso/IsoChunk";

    @Test
    void patchSizesTheBufferOnEntryAndKeepsTheVanillaLadder() throws Exception {
        byte[] rawClass = readClass();
        Counts vanilla = count(rawClass);
        byte[] transformed = new ChunkUpdateTaskBufferSizingPatch().transform(rawClass);
        assertNotNull(transformed);
        assertTrue(transformed.length > 0);
        Counts patched = count(transformed);

        assertEquals(
                vanilla.initAllocateDirect + 1,
                patched.initAllocateDirect,
                "init should gain exactly one ByteBuffer.allocateDirect - the up-front sizing");

        assertEquals(
                vanilla.initEnsureCapacity,
                patched.initEnsureCapacity,
                "every vanilla ensureCapacity call must survive as the fallback; the sizing is an"
                        + " optimisation, not a replacement");

        // Vanilla already reads both fields (the header, and the loop bound each iteration), so
        // the advice showing up means one more read of each, not merely the presence of one.
        for (String field : List.of("minLevel", "maxLevel")) {
            int expected = vanilla.levelReads.getOrDefault(field, 0) + 1;
            int actual = patched.levelReads.getOrDefault(field, 0);
            assertEquals(
                    expected,
                    actual,
                    "the size has to come from the chunk's own level range; IsoChunk."
                            + field
                            + " reads");
        }

        assertTrue(
                patched.initWritesBufferField,
                "the sized buffer has to be stored back into ChunkUpdateTask.bb");

        assertEquals(
                vanilla.otherMethodAllocateDirect,
                patched.otherMethodAllocateDirect,
                "the advice must not leak outside init");
    }

    private static byte[] readClass() throws Exception {
        try (InputStream is =
                ChunkUpdateTaskBufferSizingPatchTest.class
                        .getClassLoader()
                        .getResourceAsStream(CHUNK_UPDATE_TASK + ".class")) {
            assertNotNull(is, "ChunkUpdateTask.class must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static Counts count(byte[] classBytes) {
        Counts counts = new Counts();
        new ClassReader(classBytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions) {
                                boolean isInit = "init".equals(name);
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String mName,
                                            String mDesc,
                                            boolean isInterface) {
                                        if (opcode == Opcodes.INVOKESTATIC
                                                && BYTE_BUFFER.equals(owner)
                                                && "allocateDirect".equals(mName)) {
                                            if (isInit) counts.initAllocateDirect++;
                                            else counts.otherMethodAllocateDirect++;
                                        }
                                        if (isInit
                                                && opcode == Opcodes.INVOKESTATIC
                                                && CHUNK_UPDATE_TASK.equals(owner)
                                                && "ensureCapacity".equals(mName)) {
                                            counts.initEnsureCapacity++;
                                        }
                                    }

                                    @Override
                                    public void visitFieldInsn(
                                            int opcode, String owner, String fName, String fDesc) {
                                        if (!isInit) {
                                            return;
                                        }
                                        if (opcode == Opcodes.GETFIELD && ISO_CHUNK.equals(owner)) {
                                            counts.levelReads.merge(fName, 1, Integer::sum);
                                        }
                                        if (opcode == Opcodes.PUTFIELD
                                                && CHUNK_UPDATE_TASK.equals(owner)
                                                && "bb".equals(fName)) {
                                            counts.initWritesBufferField = true;
                                        }
                                    }
                                };
                            }
                        },
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return counts;
    }

    private static final class Counts {
        int initAllocateDirect;
        int otherMethodAllocateDirect;
        int initEnsureCapacity;
        boolean initWritesBufferField;
        final Map<String, Integer> levelReads = new LinkedHashMap<>();
    }
}
