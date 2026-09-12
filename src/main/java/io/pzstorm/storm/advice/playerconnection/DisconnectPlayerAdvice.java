package io.pzstorm.storm.advice.playerconnection;

import net.bytebuddy.asm.Advice;

/** Wraps {@code GameServer.disconnectPlayer(IsoPlayer, IConnection)}; fires before teardown. */
public class DisconnectPlayerAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.Argument(0) Object player, @Advice.Argument(1) Object connection) {
        PlayerConnectionEvents.beforeDisconnectPlayer(player, connection);
    }
}
