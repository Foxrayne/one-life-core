package io.pzstorm.storm.advice.playerconnection;

import net.bytebuddy.asm.Advice;

/**
 * Wraps {@code GameServer.disconnect(UdpConnection, String)} so the nested {@code disconnectPlayer}
 * calls can tell a closing connection from a character swap on a live one.
 */
public class GameServerDisconnectAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Object connection) {
        PlayerConnectionEvents.enterDisconnect(connection);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit() {
        PlayerConnectionEvents.exitDisconnect();
    }
}
