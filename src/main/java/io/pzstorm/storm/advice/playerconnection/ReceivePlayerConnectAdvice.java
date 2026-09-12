package io.pzstorm.storm.advice.playerconnection;

import net.bytebuddy.asm.Advice;
import zombie.characters.IsoPlayer;

/**
 * Wraps {@code GameServer.receivePlayerConnect(ByteBufferReader, IConnection, String)}. The player
 * index is read from the buffer inside the method, so the advice diffs the connection's player
 * slots across the call instead. Exit advice is skipped when the method throws, so a
 * half-registered character never fires the event.
 */
public class ReceivePlayerConnectAdvice {

    @Advice.OnMethodEnter
    public static IsoPlayer[] onEnter(@Advice.Argument(1) Object connection) {
        return PlayerConnectionEvents.snapshotPlayers(connection);
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Enter IsoPlayer[] before, @Advice.Argument(1) Object connection) {
        PlayerConnectionEvents.afterPlayerConnect(before, connection);
    }
}
