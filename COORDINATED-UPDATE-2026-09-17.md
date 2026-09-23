# One/Life coordinated update — local only

## Subsequent authorised Core publication

After the local-only work below, the user explicitly requested a Core build and upload. The Core/launcher verification tasks were rerun successfully, all 14 staged Core JAR hashes matched the existing manifest, and SteamCMD reported `Committing update...Success.` with exit code 0 for Workshop item `3798112270` (One/Life Core, unlisted). Published Core 2.11.0 and launcher 1.2.7 from `build/local-update/One Life Core/Contents`. No other mods, game JAR or CDN artifacts were uploaded, and no live server was restarted. The original local-only manifest records the earlier coordinated build, not this subsequent Core-only publication. Linux runtime limitations below still apply.

## Inputs and changes

- Reviewed upstream Storm commit: `c1d0ec3043caf9fa6d237bd05eb3abc797d7d034`.
- Core 2.11.0, launcher 1.2.7, game 42.20.4.
- Game input: `C:\Users\matth\Downloads\Updated JAR\projectzomboid.jar`.
- Game SHA-256: `2bce6dc1fe23fe8c475045276506a5ec1da5257fd146f9f8b072251e953c5a10`.
- Rebuilt World Essentials, Server Leaderboard, Server Economy, Zone Director and Random Events against this exact game JAR and the merged Core.
- Kept One/Life branding, frontend fallback, privacy text, disabled developer log-upload and original local shell-newline changes. Adapted frontend fallback to upstream's new workshop-directory join handoff.
- Core CDN replacement now defaults OFF for this fork, preventing a future upstream release from silently replacing the reviewed branded Core. An explicitly configured `storm.core.updateUrl` remains supported. The launcher no longer assumes an upstream CDN Core will be used by default.
- Added explicit game/Core JAR parameters to mod build scripts and a local-only coordinated build/staging command.

No Workshop/CDN publication, live deployment, sandbox/save changes or Steam game-JAR replacement was performed. Changes are uncommitted; the Git merge is resolved but pending a commit, preserving the upstream merge ancestry for review. The pre-merge local-edit stash remains as a backup.

## Reproduce locally

From this Core repository:

```powershell
powershell.exe -NoProfile -File tools/Build-CoordinatedUpdate.ps1 -GameJar 'C:\Users\matth\Downloads\Updated JAR\projectzomboid.jar'
```

This checks the game hash, builds/tests Core and all five Java mods, runs the packaged respawn gate and creates `build/local-update/artifact-manifest.json`. It does not invoke upload/publish/deploy/installStorm. The five mods' existing local `Contents` JARs are updated as normal build outputs.

- `build/local-update/One Life Core`: complete locally staged Core mod payload.
- `build/local-update/Java-mod-artifacts`: the five rebuilt Java artifacts in their matching `Contents` paths, NOT complete standalone Workshop packages. Keep each mod's existing Lua/resources when using these overlays.
- `build/local-update-backups/20260917212713`: previous five local mod JARs, retained before rebuilding.
- `build/coordinated-release.log`: coordinated build output.

## Zombie respawning: strict compatibility retained

The actual packaged World Essentials compatibility gate accepts this game JAR. All 19 pinned game classes match; all 78 injected Java hooks validate. The native library SHA-256 allowlist and fixed instruction-byte checks have NOT been weakened, replaced or bypassed.

Previously checked `Downloads\linux\libPZPopMan64.so` SHA-256:
`baa9213172885e82310a40886359fd831c766dc726469f60dd75831a51a2bbe0`.

There is no need for another manual native hash audit if that same verified library is retained. A different server/native-library update needs its own review. Native startup continues to check the loaded library automatically, including the existing instruction-byte checks.

Keep `storm.popman.java` false/unset: World Essentials' respawn companion integrates with the native population manager, not Storm's optional Java replacement. No respawn percentage, unseen hours, safehouse buffer, pacing or catch-up settings changed in this update.

Native policy, mocked ABI adapter, continuation and FIFO pacing tests pass. These cover one-interval quota, fractional carry, unseen/claim vetoes, loaded/unloaded transitions, delayed completions, failures and 16-per-cell/64-per-server addition pacing. The Linux companion cross-compiles, but Windows cannot execute the live Linux population library/server.

## Verification scope and exclusions

- Core offline suite: 1,500 tests; client-patch suite: 226; bootstrap: 14; launcher: 200, including four platform/configuration skips. Zero reported test failures. Instrumentation-only cases explicitly skipped internally by upstream's bytecode suite still require a real instrumented JVM.
- World Essentials bytecode/hash gates, native policy/ABI fixtures, LoadGridsquare ordering/skip/fail-open and Core resource-loader tests.
- Leaderboard: 9,478 Java assertions.
- Economy: storage, transaction/recovery, real Kahlua and complete Lua/catalog validation (UTF-8 without BOM, no `next()`).
- Zone Director: actual game loot bytecode plus randomized scheduling/spatial tests and real Kahlua bridge tests with `next` unavailable.
- Random Events: engine compatibility, runtime and real Kahlua tests.

`-PofflineTests` explicitly excludes live-server tests and two upstream tests that parse arbitrary existing local population saves (`ZpopCellTest.realSaveFilesRoundTripByteIdentically`, `ZpopVirtualFileTest.realVirtualFilesRoundTripByteIdentically`). An initial unfiltered run found failures in 155/1,268 local cell files and 14/44 virtual files. These are NOT counted as passes and do not certify Java-popman save compatibility. Original saves were not changed. Full tests remain available without the offline flag.

Platform-specific launcher tests are skipped on Windows where appropriate. Process tests now spawn a Java child instead of requiring Unix `sleep`/`true`. The branded privacy assertion matches the retained One/Life document.

The audited game JAR's chunk saver already uses a local CRC in one path; its exact class hash is accepted by the test, and the runtime patch correctly makes no redundant substitution. No general hash bypass was introduced.

## Supplied game-JAR findings (left unchanged)

The JAR contains four misplaced files whose declared package is `zombie.network.packets` but whose ZIP path is `zombie/network/`:

- ObjectChangePacket.class
- ObjectChangePacketSDAC.class
- RequestItemsForContainerPacket.class
- RequestItemsPacketSDAC.class

They are not loadable by their declared names from those misplaced entries. The canonical packet classes remain the ones checked by our tests. Coverage excludes these four misplaced entries to avoid duplicate-class analysis errors; the supplied JAR itself was not repackaged. `tools/InspectJarPaths.java` reproduces this inspection. Do not assume the misplaced SDAC additions are active merely because the files are present.

The supplied SDAC policy table enables bans for validation violations under ChangePlayerStats, ClientCommand:object, InvMngGetItem, ItemStatsPacket, MessageFromPlayerPacket, RemoveItemBurstExploit, SendItemListNet, SyncRadioData and WaveSignalPacket. This does not mean every such packet bans a player, but it does require live false-positive review. VehicleEnterPacket's table policy is drop/log, not ban. The updated JAR's vehicle-enter validator still has no same-occupant exemption in its occupied-seat check. We have not weakened/replaced these security policies. `tools/InspectGameSecurity.java` reads the table offline without invoking enforcement.

## Network/performance impact of this integration

No new Lua event handlers, gameplay polling loops, custom command protocols or per-player broadcasts were added by our compatibility/build changes. Core update checks no longer contact the upstream Core CDN by default. Existing World Essentials pacing, event certification and packet bounds remain unchanged. Upstream's own networking changes are included; offline verification does not measure their performance with 100 live players.

## Before live deployment (not performed here)

Use a copied Linux server/save for a full boot and multiplayer smoke test. Check `/wesafety` for `native-installed`, `/wegrid` for actual skip counters or a clear per-handler fallback, then `/wechunk` after leaving an area unseen. Test login/reconnect, vehicle enter/exit, inventory/loot actions, economy transactions, leaderboard, Zone Director and Random Events. Review SDAC logs for legitimate-action rejects before exposing players to the new game JAR's ban policies. This is the remaining live validation, not a requirement to redo the already completed native hash comparison.
