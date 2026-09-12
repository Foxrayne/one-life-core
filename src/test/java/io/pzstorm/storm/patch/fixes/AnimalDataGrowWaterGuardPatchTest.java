package io.pzstorm.storm.patch.fixes;

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
 * Verifies that {@link AnimalDataGrowWaterGuardPatch} injects the water/duplicate guard into {@code
 * AnimalData.grow(String)} and only into that method.
 *
 * <p>Detection signal: the inlined advice calls {@code IsoAnimal.checkForWater()}. Vanilla {@code
 * grow} calls it once; the patched method must call it at least twice. {@code checkStages}, which
 * never calls it, must stay untouched.
 */
class AnimalDataGrowWaterGuardPatchTest implements UnitTest {

    private static final String ANIMAL_DATA = "zombie/characters/animals/datas/AnimalData";
    private static final String ISO_ANIMAL = "zombie/characters/animals/IsoAnimal";
    private static final String CHECK_FOR_WATER = "checkForWater";

    private static final String TARGET_METHOD = "grow";
    private static final String TARGET_DESC = "(Ljava/lang/String;)V";

    private static final String SIBLING_METHOD = "checkStages";
    private static final String SIBLING_DESC = "()V";

    @Test
    void patchInjectsAdviceIntoGrowOnly() throws Exception {
        byte[] rawClass = readClassBytes(ANIMAL_DATA + ".class");
        byte[] transformed = new AnimalDataGrowWaterGuardPatch().transform(rawClass);
        assertNotNull(transformed);
        assertTrue(transformed.length > 0);

        int targetBefore = countWaterChecks(rawClass, TARGET_METHOD, TARGET_DESC);
        int targetAfter = countWaterChecks(transformed, TARGET_METHOD, TARGET_DESC);
        int siblingBefore = countWaterChecks(rawClass, SIBLING_METHOD, SIBLING_DESC);
        int siblingAfter = countWaterChecks(transformed, SIBLING_METHOD, SIBLING_DESC);

        assertEquals(1, targetBefore, "Vanilla grow calls checkForWater exactly once");
        assertTrue(
                targetAfter >= 2,
                "Patched grow must contain the advice's extra checkForWater call; got "
                        + targetAfter);
        assertEquals(0, siblingBefore, "Vanilla checkStages never calls checkForWater");
        assertEquals(siblingBefore, siblingAfter, "Advice must not leak into checkStages");
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countWaterChecks(byte[] classBytes, String method, String desc) {
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
                                if (!method.equals(name) || !desc.equals(descriptor)) {
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
                                        if (ISO_ANIMAL.equals(owner)
                                                && CHECK_FOR_WATER.equals(mName)) {
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
