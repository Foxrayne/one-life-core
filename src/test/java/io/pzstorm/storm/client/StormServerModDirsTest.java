package io.pzstorm.storm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StormServerModDirsTest {

    @TempDir Path tmp;

    private Path modDir(Path itemDir, String modName, String infoSubdir, String... infoLines)
            throws IOException {
        Path modDir = itemDir.resolve("mods").resolve(modName);
        Path infoDir = infoSubdir.isEmpty() ? modDir : modDir.resolve(infoSubdir);
        Files.createDirectories(infoDir);
        Files.write(
                infoDir.resolve("mod.info"),
                String.join("\n", infoLines).getBytes(StandardCharsets.UTF_8));
        return modDir;
    }

    @Test
    void readsIdFromCommonModInfo() throws IOException {
        Path mod = modDir(tmp, "TrueActionsDancing", "common", "name=TAD", "id=TrueActionsDancing");
        assertEquals(List.of("TrueActionsDancing"), StormServerModDirs.readModIds(mod));
    }

    @Test
    void readsIdFromVersionDirModInfo() throws IOException {
        Path mod = modDir(tmp, "SomeMod", "42.9", "id=SomeMod42");
        assertEquals(List.of("SomeMod42"), StormServerModDirs.readModIds(mod));
    }

    @Test
    void readsIdFromB41RootModInfo() throws IOException {
        Path mod = modDir(tmp, "OldMod", "", "id=OldMod");
        assertEquals(List.of("OldMod"), StormServerModDirs.readModIds(mod));
    }

    @Test
    void ignoresBomAndTrimsId() throws IOException {
        Path mod = modDir(tmp, "BomMod", "common", "\uFEFFid=BomMod  ");
        assertEquals(List.of("BomMod"), StormServerModDirs.readModIds(mod));
    }

    @Test
    void ignoresLinesMerelyContainingId() throws IOException {
        // vanilla only honors lines that START with id= — description=... id=Wrong must not match
        Path mod = modDir(tmp, "Tricky", "common", "description=has id=Wrong inside", "id=Right");
        assertEquals(List.of("Right"), StormServerModDirs.readModIds(mod));
    }

    @Test
    void missingOrIdlessModInfoYieldsNothing() throws IOException {
        Path noInfo = tmp.resolve("mods").resolve("Empty");
        Files.createDirectories(noInfo);
        assertTrue(StormServerModDirs.readModIds(noInfo).isEmpty());

        Path noId = modDir(tmp, "NoId", "common", "name=NoId");
        assertTrue(StormServerModDirs.readModIds(noId).isEmpty());
    }

    @Test
    void readsEveryDistinctIdAcrossRootAndVersionDirs() throws IOException {
        // Yaki's Hair Salon layout: B41 root mod.info keeps the old id, 42.0/ declares the B42 one
        Path mod = modDir(tmp, "Yaki's Hair Salon - BASE", "", "id=YakiHSBasegameTextureB41");
        modDir(tmp, "Yaki's Hair Salon - BASE", "42.0", "id=YakiHSBasegameTextureB42");
        modDir(tmp, "Yaki's Hair Salon - BASE", "common", "id=YakiHSBasegameTextureB42");

        List<String> ids = StormServerModDirs.readModIds(mod);

        assertEquals(2, ids.size());
        assertEquals("YakiHSBasegameTextureB41", ids.get(0));
        assertTrue(ids.contains("YakiHSBasegameTextureB42"));
    }

    @Test
    void scanPinsEveryIdOfADualIdModToTheSameDir() throws IOException {
        Path item = tmp.resolve("2761200458");
        Path mod = modDir(item, "Yaki's Hair Salon - BASE", "", "id=YakiHSBasegameTextureB41");
        modDir(item, "Yaki's Hair Salon - BASE", "42.0", "id=YakiHSBasegameTextureB42");

        Map<String, String> dirs = StormServerModDirs.scanItems(List.of(item));

        assertEquals(mod.toAbsolutePath().toString(), dirs.get("YakiHSBasegameTextureB41"));
        assertEquals(mod.toAbsolutePath().toString(), dirs.get("YakiHSBasegameTextureB42"));
    }

    @Test
    void scanMapsEveryModUnderEveryItem() throws IOException {
        Path itemA = tmp.resolve("3650071729");
        Path itemB = tmp.resolve("2392709985");
        Path tad = modDir(itemA, "TrueActionsDancing", "common", "id=TrueActionsDancing");
        Path tsar = modDir(itemB, "tsarslib", "common", "id=tsarslib");

        Map<String, String> dirs = StormServerModDirs.scanItems(List.of(itemA, itemB));

        assertEquals(2, dirs.size());
        assertEquals(tad.toAbsolutePath().toString(), dirs.get("TrueActionsDancing"));
        assertEquals(tsar.toAbsolutePath().toString(), dirs.get("tsarslib"));
    }

    @Test
    void firstItemWinsDuplicateModId() throws IOException {
        Path itemA = tmp.resolve("111");
        Path itemB = tmp.resolve("222");
        Path first = modDir(itemA, "Dupe", "common", "id=Dupe");
        modDir(itemB, "Dupe", "common", "id=Dupe");

        Map<String, String> dirs = StormServerModDirs.scanItems(List.of(itemA, itemB));

        assertEquals(first.toAbsolutePath().toString(), dirs.get("Dupe"));
    }

    @Test
    void itemWithoutModsDirIsSkipped() throws IOException {
        Path bare = tmp.resolve("333");
        Files.createDirectories(bare);
        assertTrue(StormServerModDirs.scanItems(List.of(bare)).isEmpty());
    }
}
