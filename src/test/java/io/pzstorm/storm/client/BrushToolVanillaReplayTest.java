package io.pzstorm.storm.client;

import static org.junit.jupiter.api.Assertions.*;
import io.pzstorm.storm.UnitTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.stdlib.BaseLib;
import se.krka.kahlua.stdlib.StringLib;
import se.krka.kahlua.stdlib.TableLib;
import se.krka.kahlua.stdlib.CoroutineLib;
import se.krka.kahlua.j2se.MathLib;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaThread;

class BrushToolVanillaReplayTest implements UnitTest {
    private void run(KahluaThread thread, KahluaTable env, String source) throws Exception {
        Object[] result = thread.pcall(LuaCompiler.loadstring(source, "brush-replay-test", env), new Object[0]);
        assertEquals(Boolean.TRUE, result[0], java.util.Arrays.toString(result));
    }

    @Test void actualVanillaReplayAndLocalPlacementHaveNoCoreCommandOrHook() throws Exception {
        var platform = new J2SEPlatform();
        var env = platform.newTable();
        env.rawset("_G", env);
        BaseLib.register(env);
        StringLib.register(platform, env);
        TableLib.register(platform, env);
        CoroutineLib.register(platform, env);
        MathLib.register(platform, env);
        env.rawset("next", null);
        var thread = new KahluaThread(platform, env);
        thread.debugOwnerThread = Thread.currentThread();
        run(thread, env, Files.readString(Path.of("build/zomboid-classpath/stdlib.lua")));
        env.rawset("next", null);
        run(thread, env, """
            ISBuildingObject = {derive = function(self, name) return {} end}
            callbacks = {}; registrations = 0; placements = 0
            Events = {
                OnTileObjectAdded = {Add = function(fn) registrations = registrations + 1; callbacks[registrations] = fn end},
                OnClientCommand = {Add = function() error("Unexpected Core brush command") end}
            }
            require = function() end
            isClient = function() return true end
            sendClientCommand = function() error("Server replay sent another client command") end
            showDebugInfoInChat = function() end
            local objects = {size = function() return 0 end}
            square = {getObjects = function() return objects end,
                getX = function() return 12 end, getY = function() return 34 end, getZ = function() return 0 end}
            getCell = function() return {getGridSquare = function(self,x,y,z)
                assert(x == 12 and y == 34 and z == 0); return square end} end
            IsoObject = {new = function(sq, sprite)
                assert(sq == square and sprite == "test_0"); return {getSprite = function() return sprite end} end}
            instanceItem = function(name) assert(name == "Base.Plank"); return {item = name} end
            ISMoveableSpriteProps = {new = function(sprite)
                assert(sprite == "test_0")
                return {placeMoveableInternal = function(self, character, sq, item, spriteName)
                    assert(sq == square and item.item == "Base.Plank" and spriteName == "test_0")
                    placements = placements + 1; lastCharacter = character
                end}
            end}
            """);
        Path vanilla = Path.of(System.getProperty("storm.server.path"),
                "media/lua/server/BuildingObjects/ISBrushToolTileCursor.lua");
        run(thread, env, Files.readString(vanilla));
        run(thread, env, "originalCreate = ISBrushToolTileCursor.create; originalReplay = callbacks[1]");
        String core = Files.readString(Path.of("media/lua/server/StormBrushToolFix.lua"));
        run(thread, env, core);
        run(thread, env, core);
        run(thread, env, """
            assert(ISBrushToolTileCursor.create == originalCreate)
            assert(registrations == 1 and callbacks[1] == originalReplay)
            callbacks[1](square, "test_0")
            assert(placements == 1 and lastCharacter == nil)
            isClient = function() return false end
            local character = {}
            ISBrushToolTileCursor.create({character = character}, 12, 34, 0, false, "test_0")
            assert(placements == 2 and lastCharacter == character)
            assert(next == nil)
            """);
    }
}
