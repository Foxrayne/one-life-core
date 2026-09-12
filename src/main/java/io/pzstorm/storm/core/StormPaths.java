package io.pzstorm.storm.core;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import zombie.Lua.LuaManager;

public class StormPaths {

    private static final File luaCacheDir = new File(LuaManager.getLuaCacheDir());

    /** Directory to store persisted global data */
    @Getter private static final File stormDataDirectory = initStormDataDirectory();

    private static File initStormDataDirectory() {
        File dir = new File(luaCacheDir, "storm");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    public static Path findFileInParents(String targetFileName) throws RuntimeException {
        Path currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        while (currentDir != null) {
            Path targetPath = currentDir.resolve(targetFileName);

            if (Files.exists(targetPath)) {
                LOGGER.debug("Found file at: {}", targetPath);
                return targetPath;
            }

            currentDir = currentDir.getParent();
        }

        throw new RuntimeException(
                "Could not find '" + targetFileName + "' in any parent directory.");
    }

    /**
     * Steam's workshop content dir for PZ ({@code …/steamapps/workshop/content/108600}). The Storm
     * Launcher passes it through the join handoff file; keep the name in sync with {@code
     * io.pzstorm.launcher.GameLaunch#WORKSHOP_DIR_PROPERTY}. Without it the dir is derived from the
     * first {@code steamapps} entry found walking up from {@code user.dir}, which a stray file or
     * folder of that name inside the game dir silently redirects.
     */
    public static final String WORKSHOP_DIR_PROPERTY = "storm.workshop.dir";

    public static Path getWorkshopDirectory() {
        String configured = System.getProperty(WORKSHOP_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path workshopDir = Path.of(configured.trim()).toAbsolutePath();
            LOGGER.info("Workshop content dir from -D{}: {}", WORKSHOP_DIR_PROPERTY, workshopDir);
            return workshopDir;
        }
        Path steamappsDirectory = findFileInParents("steamapps");
        Path workshopDir =
                steamappsDirectory.resolve("workshop").resolve("content").resolve("108600");
        LOGGER.info(
                "Workshop content dir from the steamapps entry above user.dir {}: {}",
                System.getProperty("user.dir"),
                workshopDir);
        return workshopDir;
    }

    public static Path getModsDirectory() {
        return Path.of(System.getProperty("user.home"), "Zomboid", "mods");
    }

    public static Path getLocalWorkshopDirectory() {
        return Path.of(System.getProperty("user.home"), "Zomboid", "Workshop");
    }

    /**
     * Set by the Storm Launcher on the game JVM it spawns; points at the per-server directory of
     * mods it synced before launch. Keep the property name in sync with {@code
     * io.pzstorm.launcher.GameLaunch#MODS_DIR_PROPERTY}.
     */
    public static final String LAUNCHER_MODS_PROPERTY = "storm.launcher.mods";

    /** Launcher-synced mods root, or {@code null} when not launched via the launcher. */
    public static Path getLauncherModsDirectory() {
        String dir = System.getProperty(LAUNCHER_MODS_PROPERTY);
        return dir == null || dir.isEmpty() ? null : Path.of(dir);
    }
}
