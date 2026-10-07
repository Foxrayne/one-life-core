package io.pzstorm.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SupportReportTest {
    @TempDir Path tmp;

    @Test
    void requiresDescriptionAndBoundsLogTail() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> SupportReport.export(tmp, "short", List.of()));
        Path log = tmp.resolve("game.log");
        Files.writeString(log, "x".repeat(1100000) + "LAST EVENT");
        Path report =
                SupportReport.export(
                        tmp, "Disconnected while driving", List.of(log, tmp.resolve("missing")));
        try (ZipFile zip = new ZipFile(report.toFile())) {
            assertEquals(2, zip.size());
            String text =
                    new String(
                            zip.getInputStream(zip.getEntry("1-game.log")).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.endsWith("LAST EVENT"));
            assertTrue(text.length() <= 1048576);
            assertNotNull(zip.getEntry("description.txt"));
        }
    }
}
