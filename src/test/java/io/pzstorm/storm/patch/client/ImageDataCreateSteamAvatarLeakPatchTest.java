package io.pzstorm.storm.patch.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import java.io.InputStream;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ImageDataCreateSteamAvatarLeakPatch} routes {@code
 * ImageData.createSteamAvatar(long)} through {@code SteamAvatarImageData.create} and touches no
 * other method.
 *
 * <p>Detection signal: the inlined enter advice calls {@code SteamAvatarImageData.create} via
 * INVOKESTATIC. Vanilla contains no such call, so seeing it after the transform proves the advice
 * landed; seeing none anywhere else in the class proves the matcher did not leak.
 */
class ImageDataCreateSteamAvatarLeakPatchTest implements UnitTest {

    private static final String TARGET_CLASS = "zombie/core/textures/ImageData";
    private static final String TARGET_METHOD = "createSteamAvatar";
    private static final String HELPER_OWNER =
            "io/pzstorm/storm/advice/client/steamavatarleak/SteamAvatarImageData";
    private static final String HELPER_METHOD = "create";

    @Test
    void patchRoutesCreateSteamAvatarThroughHelperOnly() throws Exception {
        byte[] rawClass = readClassBytes(TARGET_CLASS + ".class");
        byte[] transformed = new ImageDataCreateSteamAvatarLeakPatch().transform(rawClass);
        assertNotNull(transformed);
        assertTrue(transformed.length > 0);

        assertEquals(
                0,
                countHelperCalls(rawClass, true) + countHelperCalls(rawClass, false),
                "Vanilla ImageData should not call " + HELPER_OWNER + "." + HELPER_METHOD);
        assertEquals(
                1,
                countHelperCalls(transformed, true),
                "Patched createSteamAvatar(long) must contain exactly one INVOKESTATIC "
                        + HELPER_OWNER
                        + "."
                        + HELPER_METHOD
                        + " (advice not injected)");
        assertEquals(
                0,
                countHelperCalls(transformed, false),
                "Advice must not leak into other ImageData methods");
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countHelperCalls(byte[] classBytes, boolean inTargetMethod) {
        int[] hits = new int[1];
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
                                if (TARGET_METHOD.equals(name) != inTargetMethod) {
                                    return null;
                                }
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String mName,
                                            String mDesc,
                                            boolean isInterface) {
                                        if (opcode == Opcodes.INVOKESTATIC
                                                && HELPER_OWNER.equals(owner)
                                                && HELPER_METHOD.equals(mName)) {
                                            hits[0]++;
                                        }
                                    }
                                };
                            }
                        },
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return hits[0];
    }
}
