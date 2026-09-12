package io.pzstorm.storm.advice.playerconnection;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import io.pzstorm.storm.event.core.StormEventDispatcher;
import io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent;
import io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;

/**
 * Builds and dispatches {@link OnPlayerEnterWorldEvent} and {@link OnPlayerLeaveWorldEvent} for the
 * {@code GameServer} advice. Every entry point runs on the server main thread, which is the only
 * thread that calls {@code receivePlayerConnect}, {@code disconnectPlayer} and {@code disconnect},
 * so the closing-connection marker needs no synchronisation.
 *
 * <p>Nothing here may throw into the game: a handler failure is already caught by the dispatcher,
 * and everything else is caught and logged so a bad event never breaks a connect or disconnect.
 */
public final class PlayerConnectionEvents {

    /** The connection {@code GameServer.disconnect} is currently tearing down, else null. */
    public static UdpConnection closingConnection;

    private PlayerConnectionEvents() {}

    /** Copies the connection's player slots so the exit advice can see which one was filled. */
    public static IsoPlayer[] snapshotPlayers(Object connection) {
        try {
            if (connection instanceof UdpConnection udp && udp.players != null) {
                return udp.players.clone();
            }
        } catch (Throwable t) {
            LOGGER.error("[Storm] player-connect snapshot failed", t);
        }
        return null;
    }

    /** Fires {@link OnPlayerEnterWorldEvent} for every slot that went from empty to filled. */
    public static void afterPlayerConnect(IsoPlayer[] before, Object connection) {
        try {
            if (before == null || !(connection instanceof UdpConnection udp)) {
                return;
            }
            IsoPlayer[] after = udp.players;
            int slots = Math.min(before.length, after.length);
            for (int i = 0; i < slots; i++) {
                if (before[i] == null && after[i] != null) {
                    StormEventDispatcher.dispatchEvent(new OnPlayerEnterWorldEvent(after[i], udp));
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Storm] OnPlayerEnterWorld dispatch failed", t);
        }
    }

    /** Fires {@link OnPlayerLeaveWorldEvent} before {@code disconnectPlayer} tears down. */
    public static void beforeDisconnectPlayer(Object player, Object connection) {
        try {
            if (!(player instanceof IsoPlayer iso) || !(connection instanceof UdpConnection udp)) {
                return;
            }
            StormEventDispatcher.dispatchEvent(
                    new OnPlayerLeaveWorldEvent(iso, udp, closingConnection == udp));
        } catch (Throwable t) {
            LOGGER.error("[Storm] OnPlayerLeaveWorld dispatch failed", t);
        }
    }

    public static void enterDisconnect(Object connection) {
        closingConnection = connection instanceof UdpConnection udp ? udp : null;
    }

    public static void exitDisconnect() {
        closingConnection = null;
    }
}
