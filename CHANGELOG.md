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
