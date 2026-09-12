package io.pzstorm.storm.event;

import io.pzstorm.storm.UnitTest;
import io.pzstorm.storm.event.zomboid.OnPlayerEnterWorldEvent;
import io.pzstorm.storm.event.zomboid.OnPlayerLeaveWorldEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerWorldEventTest implements UnitTest {

    @Test
    void onPlayerEnterWorldEvent_shouldStoreFields() {
        OnPlayerEnterWorldEvent event = new OnPlayerEnterWorldEvent(null, null);

        Assertions.assertNull(event.getPlayer());
        Assertions.assertNull(event.getConnection());
        Assertions.assertNull(event.getUsername());
        Assertions.assertEquals("OnPlayerEnterWorld", event.getName());
    }

    @Test
    void onPlayerLeaveWorldEvent_shouldStoreFields() {
        OnPlayerLeaveWorldEvent closed = new OnPlayerLeaveWorldEvent(null, null, true);
        OnPlayerLeaveWorldEvent swapped = new OnPlayerLeaveWorldEvent(null, null, false);

        Assertions.assertNull(closed.getPlayer());
        Assertions.assertNull(closed.getConnection());
        Assertions.assertNull(closed.getUsername());
        Assertions.assertTrue(closed.isConnectionClosed());
        Assertions.assertFalse(swapped.isConnectionClosed());
        Assertions.assertEquals("OnPlayerLeaveWorld", closed.getName());
    }
}
