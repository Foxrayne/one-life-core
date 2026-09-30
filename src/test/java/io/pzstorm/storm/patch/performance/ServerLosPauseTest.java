package io.pzstorm.storm.patch.performance;

import static org.junit.jupiter.api.Assertions.*;

import io.pzstorm.storm.UnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerLosPauseTest implements UnitTest {
    @Test
    void unusedScopeDoesNotPauseLos() {
        try (var pause = new ServerLosPause(() -> fail("suspend"), () -> fail("resume"))) {}
    }

    @Test
    void normalAndNestedEvictionsShareOnePause() {
        List<String> calls = new ArrayList<>();
        try (var pause =
                new ServerLosPause(() -> calls.add("suspend"), () -> calls.add("resume"))) {
            pause.acquire();
            nestedUnload(pause, calls, false);
            nestedUnload(pause, calls, false);
        }
        assertEquals(List.of("suspend", "unload", "unload", "resume"), calls);
    }

    @Test
    void nestedUnloadFailureBeforeReturnStillResumes() {
        List<String> calls = new ArrayList<>();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (var pause =
                            new ServerLosPause(
                                    () -> calls.add("suspend"), () -> calls.add("resume"))) {
                        nestedUnload(pause, calls, true);
                    }
                });
        assertEquals(List.of("suspend", "unload", "resume"), calls);
    }

    @Test
    void drainFailureStillResumes() {
        List<String> calls = new ArrayList<>();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (var pause =
                            new ServerLosPause(
                                    () -> calls.add("suspend"), () -> calls.add("resume"))) {
                        try {
                            nestedUnload(pause, calls, false);
                        } finally {
                            throw new IllegalStateException("drain failed");
                        }
                    }
                });
        assertEquals(List.of("suspend", "unload", "resume"), calls);
    }

    private static void nestedUnload(ServerLosPause pause, List<String> calls, boolean fail) {
        pause.acquire();
        calls.add("unload");
        if (fail) throw new IllegalStateException("unload failed");
    }
}
