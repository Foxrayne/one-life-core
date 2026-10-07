# Population, container and brush alignment ? 7 October 2026

Supersedes the prior local container/brush design at the user's explicit request. Storm main dcc9b065e08b047f76a9122313f4e5220743b3f8 is the exact source baseline for the brush Lua, container advice, metrics, guards and StormTransferHandler. Production parity was checked directly against git blobs, normalizing line endings only.

Population saving already follows main: RequestSaveCellSuppress is absent and vanilla 42.21 handles incremental save requests. World Essentials is unchanged; native Linux save/persistence acceptance is still outstanding.

Removed the local ContainerInsertion transaction/rollback helper and container save preflight advice. Restored upstream traversal bounds, cycle handling/repair, insertion guard and transfer order. This intentionally removes local deep-chain and non-destructive-transfer deviations. Do not describe this version as containing the removed protections or automatic recovery guarantees.

Restored upstream StormBrushToolFix.lua exactly. It routes client placement through StormBrushTool/placeTile, checks UseBrushToolManager on the server, and replays vanilla creation there. The current 42.21 Kahlua fixture demonstrates that the upstream wrapper also intercepts vanilla OnTileObjectAdded replay and attempts a client command; source parity does not establish that this is resolved in live multiplayer. Test brush placement with two clients, reload and restart before publication. No silent local workaround is applied.

Packet audit: brush emits a command per intercepted create call, with no added rate limiter. Server requires capability, typed coordinates/sprite and a loaded square, then uses vanilla placement replication to relevant clients. Server replay behaviour must be observed for repeated requests. Container transfers use upstream command/event/replication paths; no new periodic inventory scanner was introduced. Population saving adds no packets. The brush test runs with next unavailable.

The earlier rc1 artifact remains a rollback candidate. rc2 is source-aligned with upstream, not a claim of live Linux or graphics validation.

Validation: {"build/test-results/test": {"tests": 1706, "failures": 0, "errors": 0, "skipped": 1}, "build/test-results/clientPatchTest": {"tests": 238, "failures": 0, "errors": 0, "skipped": 0}}. Core/WE/six-mod/SDAC composition verifies 346 target classes in each order with zero errors. Launcher unchanged from the previously tested rc1. Live multiplayer remains untested.
