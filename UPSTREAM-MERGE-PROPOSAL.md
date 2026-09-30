# One Life Core: population saving and container protection

Proposal updated 30 September 2026. This supersedes the broader release proposals. Scope is limited to these two changes, with current upstream Storm as the implementation baseline. No implementation or publication is authorized by this document alone.

## Baseline

Use the published One Life Core 42.21_2.11.6-rc2 source/payload, not the separate visual-development worktree. Published Core JAR SHA-256: `5bd64213ce9c763d4229052fe835a8c1e906a63f318ebb3a36580a589f150654`.

Selected upstream baseline: `3659e9f5875e1511ec88f1b935390a6ed49aea90` (Storm 2.12.3). Prefer its implementations; document and test only the necessary local integration differences. Do not merge all of main or copy unrelated files wholesale.

Target game: reviewed 42.21 JAR SHA-256 `e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33`. User-confirmed production Linux population library: `C:/Users/matth/Downloads/LinuxLibsNew/linux64/libPZPopMan64.so`, SHA-256 `31d83c5d9e281a40ed040278f567f02b36a85b757b105266a7a4146eff85a100`.

## 1. Population saving

Follow upstream's removal of RequestSaveCellSuppressPatch for this 42.21 release. Remove its registration and obsolete advice/patch code where unused. Restore the game's normal requestSaveCell behavior without replacing or redesigning its save queue.

On this game version, requestSaveCell queues a packed cell key in a deduplicated pending set. Processing applies a five-second per-cell interval and calls the native save under saveLock. Core currently skips the request method. The inspected 42.21 native saveCell routine checks the cell's loaded/changed state and calls Cell::save(false); the older snapshot-adoption sequence is absent from that entry point. This supports following upstream's removal.

Retain World Essentials' existing population controls and native adapter. Its four reviewed native hooks do not replace the save path. Retain Core's all-or-nothing ABI validation and do not enable the optional Java population backend. This proposal targets the reviewed 42.21 combination; if older builds must remain supported, explicitly isolate their compatibility behavior.

Expected outcome: chunk-unload requests can again reach incremental population persistence. Full saves already use a separate route. This change does not disable spawning, change respawn quotas, or establish a fix for previous stalls or mass disconnects.

Acceptance: on isolated Linux copies of the same save, compare published Core against the candidate with identical settings and World Essentials enabled. Exercise repeated leave/return cycles, autosave and orderly restart. Check real/virtual population persistence, unintended duplication/loss, save errors and latency. Keep normal spawning, migration and Zone Director activity distinguishable from persistence effects.

## 2. Container protection

Use upstream commit 3659e9f's ItemContainer/InventoryItem guards, advice, registration and supporting metrics as the starting point. Preserve normal inventory behavior while refusing insertion of a bag into itself or its descendants and preventing corrupt chains from causing unbounded lookups.

Make the minimum necessary integration corrections supported by the audit:

- Core container transfers and floor pickups currently remove the source before confirming destination insertion. Validate before mutation, revalidate at execution, confirm destination membership/insertion before success notifications, and preserve or restore the source on refusal/failure. An insertion guard alone is insufficient.
- Upstream repair can detach a bag without an accessible recovery location. Do not allow a lookup to silently orphan items. Separate safe lookup from server-authoritative recovery, preserving identity/contents and synchronizing every affected inventory. Abort the operation without destructive repair if safe recovery cannot be completed.
- The audit reproduced an incorrect root for a valid 70-level chain. Distinguish traversal-budget exhaustion from a proven cycle; do not return an incorrect successful result. Bound corrupt-chain work and avoid fallback into known recursive/looping vanilla paths.
- Include parent and child relationships, weight invalidation, serialization, and client/server agreement in validation. Do not introduce a persistent inventory scan.

These are evidence-backed adjustments to upstream, not a separate replacement design. Keep the upstream portions recognizable and document each deviation.

Acceptance: ordinary nested bags and outward moves; self/ancestor insertion rejection; deep valid chains; corrupt parent and child links; queued transfers whose relationships change; floor pickups; failed insertion without item loss/duplication or false success; save/reload and two-client replication. Check transformer composition on both server and client. Exercise dependent corpse, economy, quest and event-crate paths.

## Delivery

Implement the two changes in separate reviewable commits, with a concise changelog and traceable upstream origins. Build a staging candidate and retain the published rc2 rollback artifact. Confirm the client-side container protection loads through the launcher as well as the server-side protection.

Run targeted automated checks, then Linux multiplayer acceptance with the confirmed library. Existing audit checks demonstrate specific source/binary behavior; they do not certify a new implementation or replace live staging. Record packet triggers, recipients and invalidation for changed inventory transactions before making performance claims. Any Lua runtime/queue tests must include global next unavailable.

World Essentials and the other five mods should be updated individually only if integration testing demonstrates a necessary change. There is no coordinated six-mod release requirement. Publish only after acceptance passes and publication is explicitly authorized.

## Out of scope

Furniture/painting pickup and placement compatibility, diagnostics-only changes, API removals or migrations, optional packet events, Java Bullet physics, visual changes, launcher changes, and unrelated upstream cleanup are excluded. The painting report remains unresolved and should be tracked separately; this release must not claim to fix it.

## Evidence

The broader review is preserved at `build/upstream-audit-2026-09-30/full-review-before-scope-reduction.md`. Audit artifacts in the same directory include upstream-file-inventory.json, container-results.txt, composition-results.txt, mod-binary-event-scan.json, population-save-*.asm, population-cell-*.asm, and population-write-path.asm.

Completed audit work includes seven helper assertions (including demonstrations of two upstream limitations), four transformer composition smoke checks, reviewed transfer call paths, and static comparison of the supplied old/current native save implementations. Linux execution of the proposed changes remains outstanding. This proposal changes documentation only.
