package io.pzstorm.storm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StormPathsTest {

    @TempDir Path tmp;

    @AfterEach
    void clearProperty() {
        System.clearProperty(StormPaths.WORKSHOP_DIR_PROPERTY);
    }

    @Test
    void configuredWorkshopDirWinsOverTheSteamappsWalk() {
        Path configured = tmp.resolve("lib/steamapps/workshop/content/108600");
        System.setProperty(StormPaths.WORKSHOP_DIR_PROPERTY, configured.toString());
        assertEquals(configured.toAbsolutePath(), StormPaths.getWorkshopDirectory());
    }

    @Test
    void blankPropertyFallsBackToTheSteamappsWalk() throws IOException {
        System.setProperty(StormPaths.WORKSHOP_DIR_PROPERTY, "  ");
        String userDir = System.getProperty("user.dir");
        Path steamapps = tmp.resolve("steamapps");
        Files.createDirectories(steamapps);
        Path game = tmp.resolve("game");
        Files.createDirectories(game);
        System.setProperty("user.dir", game.toString());
        try {
            Path resolved = StormPaths.getWorkshopDirectory();
            assertTrue(
                    resolved.endsWith(Path.of("steamapps", "workshop", "content", "108600")),
                    resolved.toString());
            assertEquals(steamapps.toAbsolutePath(), resolved.getParent().getParent().getParent());
        } finally {
            System.setProperty("user.dir", userDir);
        }
    }
}
