package io.pzstorm.launcher;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameProcessTrackerTest {

    @TempDir Path tmp;

    private Process spawned;

    public static class Child {
        public static void main(String[] args) throws Exception {
            Thread.sleep(Long.parseLong(args[0]));
        }
    }

    private Process child(long millis) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", Path.of(GameProcessTrackerTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Child.class.getName(), Long.toString(millis)).start();
    }

    @BeforeEach
    void setUp() {
        System.setProperty("storm.launcher.zomboidDir", tmp.resolve("Zomboid").toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("storm.launcher.zomboidDir");
        if (spawned != null) {
            spawned.destroyForcibly();
        }
    }

    @Test
    void reapKillsRecordedProcess() throws Exception {
        spawned = child(60_000);
        GameProcessTracker.record(spawned);
        Assertions.assertTrue(Files.isRegularFile(GameProcessTracker.recordFile()));

        GameProcessTracker.reapLeftover();

        Assertions.assertTrue(spawned.waitFor(5, TimeUnit.SECONDS), "recorded process not killed");
        Assertions.assertFalse(
                Files.exists(GameProcessTracker.recordFile()), "record must be consumed");
    }

    @Test
    void reapSparesProcessWithMismatchedIdentity() throws Exception {
        spawned = child(60_000);
        Properties props = new Properties();
        props.setProperty("pid", Long.toString(spawned.pid()));
        // a recycled pid: same number, different process start time
        props.setProperty("startMillis", "1");
        Files.createDirectories(GameProcessTracker.recordFile().getParent());
        try (Writer writer =
                Files.newBufferedWriter(GameProcessTracker.recordFile(), StandardCharsets.UTF_8)) {
            props.store(writer, null);
        }

        GameProcessTracker.reapLeftover();

        Assertions.assertTrue(spawned.isAlive(), "mismatched identity must never be killed");
    }

    @Test
    void reapWithoutRecordIsNoop() {
        Assertions.assertDoesNotThrow(GameProcessTracker::reapLeftover);
    }

    @Test
    void reapOfExitedProcessConsumesRecord() throws Exception {
        spawned = child(100);
        GameProcessTracker.record(spawned);
        spawned.waitFor(5, TimeUnit.SECONDS);

        GameProcessTracker.reapLeftover();

        Assertions.assertFalse(Files.exists(GameProcessTracker.recordFile()));
    }
}
