# 42.21_2.13.1-rc4 — brush sync fix and server address (10 October 2026)

- Restore vanilla 42.21 brush placement and client replay. The old wrapper intercepted replay through the class table and attempted to send a client command without a player.
- Keep the updated launcher one-click join target, community card, README, Workshop description and privacy-policy server reference at 198.27.75.78:16261.

# 42.21_2.13.1-rc2 ? upstream brush/container alignment

- Restore Storm main dcc9b06 brush Lua, container guards/advice/metrics and transfer handler exactly.
- Remove One Life-only insertion rollback and container serialization preflight.
- Retain upstream-aligned vanilla population saving (no request suppression).
- No Workshop publication. Brush replay and live multiplayer acceptance remain outstanding.

# 42.21_2.13.1-rc1 ? upstream integration (7 October 2026)

Integrates Storm main through dcc9b065e08b047f76a9122313f4e5220743b3f8 on the population/container candidate. Retains One Life branding, native 42.21 population saving and non-destructive container transfers.

- Five-minute join grace for the send-buffer watchdog; clearer disconnect message.
- Server script-comment parsing, room-ID lookup and vehicle-zone loading optimisations.
- Client XL-tree guard for player-built rooms with no IsoRoom.
- Optional PvpHitsAlwaysDamage sandbox setting, default off, with translations and metrics.
- Experimental parked-vehicle animation, persistent VBO/fence, sprite ring, cutaway and puddle optimisations, gated by storm.experimental.clientperf.
- One Life launcher 1.2.8-rc1: automatic heap capped at 9 GB; experimental-performance settings require renewed opt-in for this generation, including legacy saved configurations. Explicit JVM options still win.
- Local support ZIP export with required description and bounded recent logs. No upstream support webhook or automatic upload.
- One Life launcher setup guidance and targeted bootstrap status warnings.

Not published. Native Linux multiplayer and visual acceptance remain required; this release does not claim to fix random vehicle teleportation.

## 2.11.7-rc1 - population saving and container protection (staging)

- Follow upstream 42.21 population persistence: remove requestSaveCell suppression and retain the vanilla queued native save path. World Essentials population settings and adapter are unchanged.
- Integrate upstream 3659e9f container lookup/insertion protection on server and client.
- Refuse cyclic or malformed inventories without silently detaching bags. Preserve correct roots for deep valid chains; bound corrupt-chain and serialization work.
- Validate Core transfers at acceptance and execution. Confirm insertion before equipment changes, floor-object removal, and success messages; restore original ownership after refusal or partial insertion failure.
- Retain published rc2 fixes and One/Life customizations. No furniture/painting, API migration, extra packet events, physics backend, launcher, or visual changes.
- Local candidate only. Linux multiplayer persistence and inventory acceptance are pending; not published.

## 2.11.6-rc2 - stability-only staging candidate

- Respect join grace before send-buffer kicks and improve kick diagnostics.
- Correct TCP chunk reply bookkeeping and locking, and 42.21 join prewarming.
- Guard client hutch unload/release against null animal entries without healthy-hutch log spam.
- Resume ServerLOS after exceptions during warm-cell eviction or final draining.
- Retain local 42.21 profile, CRC, action, animal-growth, packet and population ABI compatibility fixes.
- Exclude the unfinished visual renderer, shaders, options and lifecycle hooks.
- Local manual-staging candidate only; live multiplayer acceptance remains pending.

# One/Life Core changelog

## 42.20.4_2.11.3 — 2026-09-23

Merged upstream Storm through `09f20605f950575aac0633c7ac5a0e1c35da62e0`, preserving One/Life customisations. Launcher version remains 1.2.7.

### Changes

- Integrate game-port TCP login/world-loading support, with recovery for held logins, stale sessions and failed chunk transfers that need to fall back to UDP.
- Improve stalled-connection cleanup, native pathfinding task draining, client region processing and warm-cell bookkeeping.
- Fix the Steam avatar direct-buffer leak that could stall texture loading and cause missing or checkerboard visuals.
- Include fixes for stuck network timed actions, smashed-window/light-switch hangs, model reload waits, null clothing drops, gun tracers and debug-log formatting.
- Add `Storm.AnimalZoneSafehouseProtection` and its sandbox translations. This optional protection for animal-zone edits defaults to disabled.
- Retain One/Life branding, Workshop identity, frontend fallback and privacy customisations. Automatic upstream Core/launcher replacement remains disabled by default.
- Add local release staging and retain launcher/path-handling improvements from the One/Life integration.

### Validation

- Built against the supplied vanilla Project Zomboid JAR, SHA-256 `80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44`.
- Core, client-patch, bootstrap and launcher suites reported 1,975 tests in total, with four skips and no failures or errors.
- Rechecked the upstream Lua and sandbox sources, all 16 loose media files, and all 11 embedded Lua resources. No missing or differing resources were found against the merged upstream revision.
- All 38 files in the local Steam Workshop installation matched the published Core release package.

### Deployment notes

The Core package was published to Workshop item `3798112270`. Repository publication does not restart the Linux server or change its active sandbox configuration. Live Linux gameplay and deployment verification remain separate from the offline checks above.

The subsequently diagnosed SDAC/World Essentials `PlayerDamagePacket` collision is addressed by a separate World Essentials update. That fix is not part of this Core release or this repository push.
