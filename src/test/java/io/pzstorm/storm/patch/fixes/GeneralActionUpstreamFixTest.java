package io.pzstorm.storm.patch.fixes;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import net.bytebuddy.jar.asm.*;
import org.junit.jupiter.api.Test;

class GeneralActionUpstreamFixTest implements UnitTest {
    @Test
    void reviewed421KeepsVanillaPlayerIdentityDispatch() throws Exception {
        byte[] original;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "zombie/network/packets/GeneralActionPacket.class")) {
            assertNotNull(stream);
            original = stream.readAllBytes();
        }
        // Version-specific regression: fail rather than silently accepting an unknown update.
        assertEquals("398a8729da87a4c222f17473cc46c9b5cb7fd9b3f74492d283765619c1d57c8a",
                java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(original)));
        byte[] output = new GeneralActionPacketPatch().transform(original);
        int[] calls = new int[3];
        new ClassReader(output).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String method,
                            String descriptor, boolean itf) {
                        if (method.equals("processServerFixed")) calls[0]++;
                        if (name.equals("processServer") && owner.equals("zombie/core/ActionManager")
                                && method.equals("stop")) calls[1]++;
                        if (name.equals("setReject") && method.equals("set")
                                && descriptor.equals("(Lzombie/characters/IsoPlayer;)V")) calls[2]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertArrayEquals(new int[]{0, 1, 1}, calls);
    }
}
