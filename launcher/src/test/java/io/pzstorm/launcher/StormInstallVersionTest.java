package io.pzstorm.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StormInstallVersionTest {

    @TempDir Path tmp;

    private Path libDir;
    private Path bootstrapDir;

    @BeforeEach
    void layout() throws IOException {
        Path storm = tmp.resolve("mods").resolve("storm");
        libDir = Files.createDirectories(storm.resolve("42").resolve("lib"));
        bootstrapDir = Files.createDirectories(storm.resolve("bootstrap"));
        System.setProperty("storm.launcher.zomboidDir", tmp.resolve("Zomboid").toString());
    }

    @AfterEach
    void clearOverride() {
        System.clearProperty("storm.launcher.zomboidDir");
    }

    private static Path stampedJar(Path path, String version) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("storm-version.properties"));
            jar.write(("version=" + version + "\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return path;
    }

    private Path stagedJar(String hash, String version) throws IOException {
        return stampedJar(
                tmp.resolve("Zomboid")
                        .resolve("storm")
                        .resolve("core")
                        .resolve("stage")
                        .resolve(hash)
                        .resolve("storm.jar"),
                version);
    }

    @Test
    void readsVersionStampedInsideTheItemJar() throws IOException {
        Path jar = stampedJar(libDir.resolve("storm-42.20.4_2.9.2.jar"), "42.20.4_2.9.2");

        StormInstallVersion.Installed installed = StormInstallVersion.find(bootstrapDir);

        assertEquals("42.20.4_2.9.2", installed.fullVersion());
        assertEquals(jar, installed.jar());
        assertEquals("Storm 2.9.2", StormInstallVersion.label(installed));
    }

    @Test
    void fallsBackToTheFilenameWhenTheJarIsNotAJar() throws IOException {
        Files.write(libDir.resolve("storm-42.20.4_2.9.2-SNAPSHOT.jar"), new byte[] {0x50, 0x4b});

        StormInstallVersion.Installed installed = StormInstallVersion.find(bootstrapDir);

        assertEquals("42.20.4_2.9.2-SNAPSHOT", installed.fullVersion());
        assertEquals("Storm 2.9.2-SNAPSHOT", StormInstallVersion.label(installed));
    }

    @Test
    void picksTheNewestItemJarWhenSteamLeftAStaleOne() throws IOException {
        stampedJar(libDir.resolve("storm-42.19.0_2.3.1-SNAPSHOT.jar"), "42.19.0_2.3.1-SNAPSHOT");
        Path current = stampedJar(libDir.resolve("storm-42.20.2_2.5.5.jar"), "42.20.2_2.5.5");

        StormInstallVersion.Installed installed = StormInstallVersion.find(bootstrapDir);

        assertEquals(current, installed.jar());
    }

    @Test
    void stagedCdnCoreWinsWhenStrictlyNewer() throws IOException {
        stampedJar(libDir.resolve("storm-42.20.4_2.9.1.jar"), "42.20.4_2.9.1");
        Path staged = stagedJar("a7a0a3b1f4225de9", "42.20.4_2.9.3");

        StormInstallVersion.Installed installed = StormInstallVersion.find(bootstrapDir);

        assertEquals("42.20.4_2.9.3", installed.fullVersion());
        assertEquals(staged, installed.jar());
    }

    @Test
    void staleStagedCoreLosesToANewerItem() throws IOException {
        Path item = stampedJar(libDir.resolve("storm-42.20.4_2.9.4.jar"), "42.20.4_2.9.4");
        stagedJar("a7a0a3b1f4225de9", "42.20.4_2.9.3");

        assertEquals(item, StormInstallVersion.find(bootstrapDir).jar());
    }

    @Test
    void snapshotItemIgnoresTheStage() throws IOException {
        Path item =
                stampedJar(
                        libDir.resolve("storm-42.20.4_2.9.2-SNAPSHOT.jar"),
                        "42.20.4_2.9.2-SNAPSHOT");
        stagedJar("a7a0a3b1f4225de9", "42.20.4_2.9.9");

        assertEquals(item, StormInstallVersion.find(bootstrapDir).jar());
    }

    @Test
    void nullWhenNothingIsInstalled() {
        assertNull(StormInstallVersion.find(bootstrapDir));
        assertNull(StormInstallVersion.find(null));
        assertEquals("Storm not installed", StormInstallVersion.label(null));
    }
}
