package io.pzstorm.storm.patch.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pzstorm.storm.UnitTest;
import io.pzstorm.storm.advice.playerconnection.PlayerConnectionEvents;
import java.io.InputStream;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link GameServerPlayerConnectionEventsPatch} inlines exactly one advice into each
 * of its three {@code GameServer} hosts and nothing into their neighbours.
 *
 * <p>Detection signal: every inlined advice calls a static on {@link PlayerConnectionEvents}, which
 * vanilla {@code GameServer} never references.
 */
class GameServerPlayerConnectionEventsPatchTest implements UnitTest {

    private static final String GAME_SERVER = "zombie/network/GameServer";
    private static final String OWNER =
            "io/pzstorm/storm/advice/playerconnection/PlayerConnectionEvents";

    private static final String RECEIVE_CONNECT = "receivePlayerConnect";
    private static final String RECEIVE_CONNECT_DESC =
            "(Lzombie/core/network/ByteBufferReader;Lzombie/network/IConnection;Ljava/lang/String;)V";
    private static final String DISCONNECT_PLAYER = "disconnectPlayer";
    private static final String DISCONNECT_PLAYER_DESC =
            "(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V";
    private static final String DISCONNECT = "disconnect";
    private static final String DISCONNECT_DESC =
            "(Lzombie/core/raknet/UdpConnection;Ljava/lang/String;)V";

    private static final String SIBLING = "launchCommandHandler";
    private static final String SIBLING_DESC = "()V";

    @Test
    void patchHooksTheThreeConnectionSeams() throws Exception {
        byte[] raw = readClassBytes(GAME_SERVER + ".class");
        byte[] transformed = new GameServerPlayerConnectionEventsPatch().transform(raw);
        assertNotNull(transformed);
        assertTrue(transformed.length > 0);

        assertEquals(0, count(raw, RECEIVE_CONNECT, RECEIVE_CONNECT_DESC));
        assertEquals(0, count(raw, DISCONNECT_PLAYER, DISCONNECT_PLAYER_DESC));
        assertEquals(0, count(raw, DISCONNECT, DISCONNECT_DESC));

        assertEquals(
                2,
                count(transformed, RECEIVE_CONNECT, RECEIVE_CONNECT_DESC),
                "receivePlayerConnect must snapshot on entry and dispatch on exit");
        assertEquals(
                1,
                count(transformed, DISCONNECT_PLAYER, DISCONNECT_PLAYER_DESC),
                "disconnectPlayer must dispatch on entry");
        assertTrue(
                count(transformed, DISCONNECT, DISCONNECT_DESC) >= 2,
                "disconnect must mark the closing connection on entry and clear it on exit");
        assertEquals(0, count(transformed, SIBLING, SIBLING_DESC), "advice leaked into " + SIBLING);
    }

    @Test
    void helpersAreNoOpsForNonServerConnections() {
        assertEquals(null, PlayerConnectionEvents.snapshotPlayers(null));
        assertEquals(null, PlayerConnectionEvents.snapshotPlayers(new Object()));
        PlayerConnectionEvents.afterPlayerConnect(null, null);
        PlayerConnectionEvents.beforeDisconnectPlayer(null, null);
        PlayerConnectionEvents.enterDisconnect(new Object());
        assertEquals(null, PlayerConnectionEvents.closingConnection);
        PlayerConnectionEvents.exitDisconnect();
        assertEquals(null, PlayerConnectionEvents.closingConnection);
    }

    private byte[] readClassBytes(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, resourcePath + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int count(byte[] classBytes, String method, String desc) {
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
                                        if (opcode == Opcodes.INVOKESTATIC && OWNER.equals(owner)) {
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
