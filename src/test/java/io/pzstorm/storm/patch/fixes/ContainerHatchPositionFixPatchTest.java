package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ContainerHatchPositionFixPatch} weaves both halves of the container-hatch
 * repair into {@code Food.checkEggHatch(IsoHutch)} and nowhere else, and unit-tests the pure
 * origin-band decision in {@link CoopHatchPositionFix#isAtOrigin}.
 */
class ContainerHatchPositionFixPatchTest implements UnitTest {

    private static final String TARGET_CLASS = "zombie/inventory/types/Food";
    private static final String HELPER_CLASS = "io/pzstorm/storm/patch/fixes/CoopHatchPositionFix";

    private static final String TARGET_METHOD = "checkEggHatch";
    private static final String TARGET_DESC = "(Lzombie/iso/objects/IsoHutch;)Z";

    @Test
    void patchInjectsBothHalvesIntoCheckEggHatchOnly() throws Exception {
        byte[] rawClass = readClassBytes(TARGET_CLASS + ".class");
        byte[] transformed = new ContainerHatchPositionFixPatch().transform(rawClass);
        assertNotNull(transformed);

        assertTrue(
                helperCalls(rawClass, TARGET_METHOD, TARGET_DESC).isEmpty(),
                "Vanilla checkEggHatch must not reference the Storm helper");
        Set<String> woven = helperCalls(transformed, TARGET_METHOD, TARGET_DESC);
        assertTrue(
                woven.contains("captureHatchContainer"),
                "Patched checkEggHatch must capture the egg container on entry");
        assertTrue(
                woven.contains("ensureContainerHatchPosition"),
                "Patched checkEggHatch must repair positions on exit");
        assertEquals(
                0,
                helperCalls(transformed, "update", null).size(),
                "Advice must not leak into Food.update");
    }

    @Test
    void originBandMatchesConstructedAtZero() {
        assertTrue(CoopHatchPositionFix.isAtOrigin(0.5f, 0.5f));
        assertTrue(CoopHatchPositionFix.isAtOrigin(0.0f, 0.0f));
    }

    @Test
    void positionedAnimalIsOutsideOriginBand() {
        assertFalse(CoopHatchPositionFix.isAtOrigin(7052.5f, 5316.5f));
        assertFalse(CoopHatchPositionFix.isAtOrigin(0.5f, 5316.5f));
        assertFalse(CoopHatchPositionFix.isAtOrigin(7052.5f, 0.5f));
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static Set<String> helperCalls(byte[] classBytes, String method, String desc) {
        Set<String> hits = new HashSet<>();
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
                                if (!method.equals(name)
                                        || (desc != null && !desc.equals(descriptor))) {
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
                                        if (HELPER_CLASS.equals(owner)) {
                                            hits.add(mName);
                                        }
                                    }
                                };
                            }
                        },
                        0);
        return hits;
    }
}
