package io.pzstorm.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * The Storm core the game will actually load, read the same way the bootstrap picks it: the
 * workshop item's newest versioned jar, overridden by a CDN-staged {@code storm.jar} under {@code
 * ~/Zomboid/storm/core/stage} when that carries a strictly higher stormVersion and the item isn't a
 * SNAPSHOT (see {@code io.pzstorm.storm.StormCoreUpdate#resolve}). The version comes from the
 * {@code storm-version.properties} resource Gradle writes into every core jar, falling back to the
 * filename when the jar can't be opened.
 */
public final class StormInstallVersion {

    private static final String VERSION_RESOURCE = "storm-version.properties";

    /** Matches {@code <pzVersion>_<stormVersion>[-SNAPSHOT]} as written by the Storm build. */
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("([0-9.]+)_([0-9.]+)(-SNAPSHOT)?", Pattern.CASE_INSENSITIVE);

    private StormInstallVersion() {}

    /** A located Storm core: its full {@code pz_storm} version string and the jar it came from. */
    public record Installed(String pzVersion, String stormVersion, boolean snapshot, Path jar) {

        public String stormLabel() {
            return snapshot ? stormVersion + "-SNAPSHOT" : stormVersion;
        }

        public String fullVersion() {
            return pzVersion + "_" + stormLabel();
        }
    }

    /** Null when no Storm core can be found; never throws. */
    public static Installed find(Path bootstrapDir) {
        try {
            Installed item = itemJar(bootstrapDir);
            if (item == null || item.snapshot()) {
                return item;
            }
            Installed staged = stagedJar();
            if (staged != null
                    && StaleStormJarCleanup.compareDottedNumeric(
                                    staged.stormVersion(), item.stormVersion())
                            > 0) {
                return staged;
            }
            return item;
        } catch (Exception e) {
            Log.warn("Could not determine the installed Storm version: " + e);
            return null;
        }
    }

    private static Installed itemJar(Path bootstrapDir) throws IOException {
        Path libDir = StaleStormJarCleanup.libDirOf(bootstrapDir);
        if (libDir == null || !Files.isDirectory(libDir)) {
            return null;
        }
        Path best = null;
        StaleStormJarCleanup.JarVersion bestVer = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(libDir)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                StaleStormJarCleanup.JarVersion ver =
                        StaleStormJarCleanup.parse(entry.getFileName().toString());
                if (ver != null
                        && (bestVer == null
                                || StaleStormJarCleanup.compareVersions(ver, bestVer) > 0)) {
                    best = entry;
                    bestVer = ver;
                }
            }
        }
        if (best == null) {
            return null;
        }
        Installed fromResource = readResource(best);
        return fromResource != null
                ? fromResource
                : new Installed(
                        bestVer.pzVersion(), bestVer.stormVersion(), bestVer.snapshot(), best);
    }

    private static Installed stagedJar() throws IOException {
        Path stageRoot =
                LauncherPaths.zomboidDir().resolve("storm").resolve("core").resolve("stage");
        if (!Files.isDirectory(stageRoot)) {
            return null;
        }
        Installed best = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stageRoot)) {
            for (Path entry : entries) {
                Path jar = entry.resolve("storm.jar");
                if (!Files.isRegularFile(jar)) {
                    continue;
                }
                Installed candidate = readResource(jar);
                if (candidate != null
                        && !candidate.snapshot()
                        && (best == null
                                || StaleStormJarCleanup.compareDottedNumeric(
                                                candidate.stormVersion(), best.stormVersion())
                                        > 0)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** The version stamped inside the jar, or null when the jar is unreadable or unstamped. */
    static Installed readResource(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry(VERSION_RESOURCE);
            if (entry == null) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream in = jarFile.getInputStream(entry)) {
                props.load(in);
            }
            return parse(props.getProperty("version", ""), jar);
        } catch (IOException e) {
            return null;
        }
    }

    static Installed parse(String version, Path jar) {
        Matcher m = VERSION_PATTERN.matcher(version.trim());
        if (!m.matches()) {
            return null;
        }
        return new Installed(m.group(1), m.group(2), m.group(3) != null, jar);
    }

    /**
     * Header text: the Storm version with its snapshot tag, or a hint when nothing is installed.
     */
    public static String label(Installed installed) {
        return installed == null ? "Storm not installed" : "Storm " + installed.stormLabel();
    }
}
