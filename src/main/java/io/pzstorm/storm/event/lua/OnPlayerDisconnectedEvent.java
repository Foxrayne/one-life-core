package io.pzstorm.storm.event.lua;

import io.pzstorm.storm.event.core.LuaEvent;

/**
 * Flattened snapshot of a player entering {@code GameServer.disconnectPlayer}. Storm does not fire
 * this event; it is a data carrier for mods that dispatch it themselves (historically the
 * extra-logging mod's own {@code GameServer} patch) or trigger it from Lua.
 *
 * @deprecated kept for backwards compatibility only. Subscribe to {@link
 *     io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent}, which Storm fires from the same seam
 *     with the live {@code IsoPlayer} and {@code UdpConnection}, and which can tell a closing
 *     connection from a character swap.
 */
@Deprecated
public class OnPlayerDisconnectedEvent implements LuaEvent {

    public final String username;
    public final String displayName;
    public final String ip;
    public final long steamId;
    public final String idStr;
    public final String roleName;
    public final long connectedGuid;
    public final short onlineId;
    public final float x;
    public final float y;
    public final float z;

    public OnPlayerDisconnectedEvent(
            String username,
            String displayName,
            String ip,
            long steamId,
            String idStr,
            String roleName,
            long connectedGuid,
            short onlineId,
            float x,
            float y,
            float z) {
        this.username = username;
        this.displayName = displayName;
        this.ip = ip;
        this.steamId = steamId;
        this.idStr = idStr;
        this.roleName = roleName;
        this.connectedGuid = connectedGuid;
        this.onlineId = onlineId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
