package io.pzstorm.storm.event.lua;

import io.pzstorm.storm.event.core.LuaEvent;

/**
 * Flattened snapshot of a player that finished {@code GameServer.receivePlayerConnect}. Storm does
 * not fire this event; it is a data carrier for mods that dispatch it themselves (historically the
 * extra-logging mod's own {@code GameServer} patch) or trigger it from Lua.
 *
 * @deprecated kept for backwards compatibility only. Subscribe to {@link
 *     io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent}, which Storm fires from the same seam
 *     with the live {@code IsoPlayer} and {@code UdpConnection} instead of copied fields.
 */
@Deprecated
public class OnPlayerFullyConnectedEvent implements LuaEvent {

    public final String username;
    public final String displayName;
    public final String ip;
    public final long steamId;
    public final long ownerId;
    public final String idStr;
    public final String roleName;
    public final long connectedGuid;
    public final int connectionIndex;
    public final short onlineId;
    public final float x;
    public final float y;
    public final float z;

    public OnPlayerFullyConnectedEvent(
            String username,
            String displayName,
            String ip,
            long steamId,
            long ownerId,
            String idStr,
            String roleName,
            long connectedGuid,
            int connectionIndex,
            short onlineId,
            float x,
            float y,
            float z) {
        this.username = username;
        this.displayName = displayName;
        this.ip = ip;
        this.steamId = steamId;
        this.ownerId = ownerId;
        this.idStr = idStr;
        this.roleName = roleName;
        this.connectedGuid = connectedGuid;
        this.connectionIndex = connectionIndex;
        this.onlineId = onlineId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
