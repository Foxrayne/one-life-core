package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import io.pzstorm.storm.core.StormClassTransformers;
import java.lang.reflect.Field;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import org.junit.jupiter.api.Test;

class PopulationSaveQueueTest implements UnitTest {
    @Test
    void actualGameMethodQueuesAndDeduplicatesWithoutCallingNativeCode() throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init();
        zombie.core.random.RandLua.INSTANCE.init();
        Field field =
                zombie.popman.ZombiePopulationManager.class.getDeclaredField("pendingSaveCellKeys");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Integer> pending = (Set<Integer>) field.get(null);
        Set<Integer> saved = new HashSet<>(pending);
        boolean wasClient = zombie.network.GameClient.client;
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        var manager =
                (zombie.popman.ZombiePopulationManager)
                        ((sun.misc.Unsafe) uf.get(null))
                                .allocateInstance(zombie.popman.ZombiePopulationManager.class);
        try {
            pending.clear();
            zombie.network.GameClient.client = false;
            manager.requestSaveCell(10, 20);
            manager.requestSaveCell(10, 20);
            assertEquals(1, pending.size());
            manager.requestSaveCell(11, 20);
            assertEquals(2, pending.size());
        } finally {
            pending.clear();
            pending.addAll(saved);
            zombie.network.GameClient.client = wasClient;
        }
    }

    @Test
    void serverRegistryDoesNotSuppressThe421SaveQueue() throws Exception {
        Field f = StormClassTransformers.class.getDeclaredField("TRANSFORMERS");
        f.setAccessible(true);
        Map<?, ?> patches = (Map<?, ?>) f.get(null);
        assertFalse(patches.values().toString().contains("RequestSaveCellSuppress"));
        byte[] bytes;
        try (var in =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("zombie/popman/ZombiePopulationManager.class")) {
            assertNotNull(in);
            bytes = in.readAllBytes();
        }
        int[] requests = {0}, saves = {0};
        new ClassReader(bytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String desc,
                                    String sig,
                                    String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String method,
                                            String descriptor,
                                            boolean itf) {
                                        if (name.equals("requestSaveCell") && method.equals("add"))
                                            requests[0]++;
                                        if (name.equals("processPendingSaveCells")
                                                && method.equals("n_saveCell")) saves[0]++;
                                    }
                                };
                            }
                        },
                        0);
        assertTrue(requests[0] > 0, "42.21 request method must queue cells");
        assertTrue(saves[0] > 0, "queued requests must reach native save");
    }
}
