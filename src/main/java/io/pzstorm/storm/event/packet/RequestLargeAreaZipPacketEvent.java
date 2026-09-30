package io.pzstorm.storm.event.packet;

import zombie.core.raknet.UdpConnection;

/**
 * Legacy event retained for source migration. The corresponding vanilla packet was removed in
 * 42.21, so this event is no longer registered or dispatched. Use the current chunk-request events.
 */
@Deprecated
public class RequestLargeAreaZipPacketEvent extends PacketEvent {

    public RequestLargeAreaZipPacketEvent(Object packet, UdpConnection connection) {
        super(packet, connection);
    }

    public Object getPacket() {
        return getRawPacket();
    }

    @Override
    public String getName() {
        return "RequestLargeAreaZipPacketEvent";
    }
}
