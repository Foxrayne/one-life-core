package io.pzstorm.storm.event;

import io.pzstorm.storm.IntegrationTest;
import io.pzstorm.storm.event.core.StormEventDispatcher;
import io.pzstorm.storm.event.core.SubscribeEvent;
import io.pzstorm.storm.event.lua.OnPlayerDisconnectedEvent;
import io.pzstorm.storm.event.lua.OnPlayerFullyConnectedEvent;
import io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent;
import io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerWorldEventDispatchTest implements IntegrationTest {

    @Test
    void shouldDispatchOnPlayerEnterWorldEvent() {
        EnterHandler handler = new EnterHandler();
        StormEventDispatcher.registerEventHandler(handler);

        OnPlayerEnterWorldEvent event = new OnPlayerEnterWorldEvent(null, null);
        StormEventDispatcher.dispatchEvent(event);

        Assertions.assertSame(event, handler.received);
    }

    @Test
    void shouldDispatchOnPlayerLeaveWorldEvent() {
        LeaveHandler handler = new LeaveHandler();
        StormEventDispatcher.registerEventHandler(handler);

        OnPlayerLeaveWorldEvent event = new OnPlayerLeaveWorldEvent(null, null, true);
        StormEventDispatcher.dispatchEvent(event);

        Assertions.assertSame(event, handler.received);
        Assertions.assertTrue(handler.received.isConnectionClosed());
    }

    @Test
    @SuppressWarnings("deprecation")
    void newEventsShouldNotReachDeprecatedSubscribers() {
        DeprecatedHandler handler = new DeprecatedHandler();
        StormEventDispatcher.registerEventHandler(handler);

        StormEventDispatcher.dispatchEvent(new OnPlayerEnterWorldEvent(null, null));
        StormEventDispatcher.dispatchEvent(new OnPlayerLeaveWorldEvent(null, null, true));

        Assertions.assertFalse(handler.called);
    }

    public static class EnterHandler {
        OnPlayerEnterWorldEvent received;

        @SubscribeEvent
        public void onEnter(OnPlayerEnterWorldEvent event) {
            received = event;
        }
    }

    public static class LeaveHandler {
        OnPlayerLeaveWorldEvent received;

        @SubscribeEvent
        public void onLeave(OnPlayerLeaveWorldEvent event) {
            received = event;
        }
    }

    @SuppressWarnings("deprecation")
    public static class DeprecatedHandler {
        boolean called;

        @SubscribeEvent
        public void onConnected(OnPlayerFullyConnectedEvent event) {
            called = true;
        }

        @SubscribeEvent
        public void onDisconnected(OnPlayerDisconnectedEvent event) {
            called = true;
        }
    }
}
