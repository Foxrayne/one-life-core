package io.pzstorm.storm.client;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import zombie.iso.WorldStreamer;

/** Exercises real vanilla bookkeeping when TCP wins the race against the next UDP update. */
class StormTcpChunkDeliveryTest implements UnitTest {
    @Test
    void earlyUnchangedReplyRetiresTheSentRequest() throws Exception {
        exercise("applyNotRequired", new Class<?>[] {int.class, boolean.class}, true);
    }

    @Test
    void earlyDataReplyRetiresTheSentRequestAndKeepsItsBytes() throws Exception {
        exercise("applyData", new Class<?>[] {int.class, byte[].class}, new byte[] {9, 8, 7});
    }

    @SuppressWarnings("unchecked")
    private static void exercise(String name, Class<?>[] signature, Object reply) throws Exception {
        WorldStreamer previous = WorldStreamer.instance;
        try {
            WorldStreamer streamer = new WorldStreamer();
            WorldStreamer.instance = streamer;
            var request = new WorldStreamer.ChunkRequest();
            request.requestNumber = 123;
            ((Collection<Object>) field(WorldStreamer.class, "sentRequests").get(streamer))
                    .add(request);
            var delivery = StormChunksOverTcp.class.getDeclaredMethod(name, signature);
            delivery.setAccessible(true);
            delivery.invoke(null, 123, reply);
            assertTrue(
                    ((Collection<?>) field(WorldStreamer.class, "sentRequests").get(streamer))
                            .isEmpty());
            assertTrue(
                    ((Collection<?>) field(WorldStreamer.class, "pendingRequests").get(streamer))
                            .isEmpty());
            assertEquals(
                    16,
                    field(request.getClass(), "flagsUdp").getInt(request) & 16,
                    "TCP reply must complete the request even before the UDP thread updates");
            if (reply instanceof byte[] bytes) {
                ByteBuffer received = (ByteBuffer) field(request.getClass(), "bb").get(request);
                assertEquals(bytes.length, received.position());
                for (int i = 0; i < bytes.length; i++) assertEquals(bytes[i], received.get(i));
            }
            assertFalse(StormChunksOverTcp.RECEIVE_LOCK.isLocked());
        } finally {
            WorldStreamer.instance = previous;
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
