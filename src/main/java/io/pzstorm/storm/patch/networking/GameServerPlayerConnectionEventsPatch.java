package io.pzstorm.storm.patch.networking;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Fires {@link io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent} and {@link
 * io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent} from {@code GameServer}. Three hooks, all
 * on the server main thread:
 *
 * <ul>
 *   <li>{@code receivePlayerConnect} exit: a character was registered on a connection.
 *   <li>{@code disconnectPlayer} entry: a character is leaving the world.
 *   <li>{@code disconnect(UdpConnection, String)} entry/exit: marks the connection being torn down
 *       so the disconnect event can report whether the connection itself closed.
 * </ul>
 *
 * See {@link io.pzstorm.storm.advice.playerconnection.PlayerConnectionEvents}.
 */
public class GameServerPlayerConnectionEventsPatch extends StormClassTransformer {

    private static final String ADVICE_PACKAGE = "io.pzstorm.storm.advice.playerconnection.";

    public GameServerPlayerConnectionEventsPatch() {
        super("zombie.network.GameServer");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                        advice(typePool, locator, "ReceivePlayerConnectAdvice")
                                .on(
                                        ElementMatchers.named("receivePlayerConnect")
                                                .and(ElementMatchers.takesArguments(3))))
                .visit(
                        advice(typePool, locator, "DisconnectPlayerAdvice")
                                .on(
                                        ElementMatchers.named("disconnectPlayer")
                                                .and(ElementMatchers.takesArguments(2))))
                .visit(
                        advice(typePool, locator, "GameServerDisconnectAdvice")
                                .on(
                                        ElementMatchers.named("disconnect")
                                                .and(ElementMatchers.takesArguments(2))
                                                .and(
                                                        ElementMatchers.takesArgument(
                                                                1, String.class))));
    }

    private static Advice advice(TypePool typePool, ClassFileLocator locator, String simpleName) {
        return Advice.to(typePool.describe(ADVICE_PACKAGE + simpleName).resolve(), locator);
    }
}
