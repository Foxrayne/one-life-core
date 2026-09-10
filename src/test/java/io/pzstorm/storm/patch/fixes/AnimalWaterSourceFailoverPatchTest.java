package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Verifies that {@link AnimalWaterSourceFailoverPatch} weaves the failover helper into the four
 * {@code BaseAnimalBehavior} methods it targets and nowhere else, and unit-tests the fail-soft
 * latch and the containment gate in {@link AnimalWaterSourceFailover}.
 */
class AnimalWaterSourceFailoverPatchTest implements UnitTest {

    private static final String TARGET_CLASS =
            "zombie/characters/animals/behavior/BaseAnimalBehavior";
    private static final String HELPER_CLASS =
            "io/pzstorm/storm/patch/fixes/AnimalWaterSourceFailover";

    @Test
    void patchInjectsHelperIntoDrinkPathsOnly() throws Exception {
        byte[] rawClass = readClassBytes(TARGET_CLASS + ".class");
        byte[] transformed = new AnimalWaterSourceFailoverPatch().transform(rawClass);
        assertNotNull(transformed);

        assertEquals(0, countHelperCalls(rawClass, "tryDrinkFromRiver", "()Z"));
        assertEquals(1, countHelperCalls(transformed, "tryDrinkFromRiver", "()Z"));
        assertEquals(1, countHelperCalls(transformed, "tryDrinkFromPuddle", "()Z"));
        assertEquals(1, countHelperCalls(transformed, "doBehaviorAction", "()V"));
        assertEquals(1, countHelperCalls(transformed, "update", "()V"));
        assertEquals(0, countHelperCalls(transformed, "checkDrinkBehavior", null));
        assertEquals(0, countHelperCalls(transformed, "tryDrinkFromTrough", null));
        assertEquals(0, countHelperCalls(transformed, "tryDrinkFromGround", null));
        assertEquals(0, countHelperCalls(transformed, "checkBehavior", null));
    }

    @Test
    void disabledContainmentMeansVanillaOrder() {
        boolean before = AnimalZoneContainment.isEnabled();
        try {
            AnimalWaterSourceFailover.reset();
            AnimalZoneContainment.setEnabled(false);
            assertFalse(AnimalWaterSourceFailover.skipRiver(new Object()));
            assertFalse(AnimalWaterSourceFailover.skipPuddle(new Object()));
            assertFalse(AnimalWaterSourceFailover.isBroken());
        } finally {
            AnimalZoneContainment.setEnabled(before);
            AnimalWaterSourceFailover.reset();
        }
    }

    @Test
    void nonAnimalLatchesOffInsteadOfThrowing() {
        boolean before = AnimalZoneContainment.isEnabled();
        try {
            AnimalWaterSourceFailover.reset();
            AnimalZoneContainment.setEnabled(true);
            assertFalse(AnimalWaterSourceFailover.skipRiver(new Object()));
            assertTrue(AnimalWaterSourceFailover.isBroken());
            // Latched: further calls must be silent no-ops, never a throw into the behavior.
            AnimalWaterSourceFailover.onFailsafe(new Object(), new Object());
            AnimalWaterSourceFailover.onBehaviorActionDone(new Object(), new Object(), true);
            assertFalse(AnimalWaterSourceFailover.skipPuddle(new Object()));
        } finally {
            AnimalZoneContainment.setEnabled(before);
            AnimalWaterSourceFailover.reset();
        }
        assertFalse(AnimalWaterSourceFailover.isBroken());
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countHelperCalls(byte[] classBytes, String method, String desc) {
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
                                            hits[0]++;
                                        }
                                    }
                                };
                            }
                        },
                        0);
        return hits[0];
    }
}
