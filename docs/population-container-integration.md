# Population and container integration notes

Upstream baseline: 3659e9f (Storm 2.12.3); published One/Life baseline: 42.21_2.11.6-rc2. Only request-save suppression removal and container protection are selected. Existing launcher, loose Lua, sandbox settings and other mod binaries are retained.

## Deviations from upstream container guard

- Non-destructive failure replaces getter-triggered item detachment. Corrupt or excessive structures require explicit recovery; this build never claims automatic repair.
- Allocation-free bounded Floyd detection distinguishes cycles from excessive depth. Valid 70-level chains return the real root. Parent-chain checks do not call recursive contains on corrupt child links.
- Descendant preflight checks identity duplicates, backlinks and bounded depth/count before bag insertion and container serialization. Serialization validates a tree once per synchronous save scope; advice releases that scope even if the body throws. No cache survives the call.
- Core uses confirmed insertion before removing world objects or reporting transfer success. Vanilla AddItem handles source removal, with ownership/list rollback for refusal or a RuntimeException in insertion. Floor pickup re-registers the accepted item for processing after world removal cancels that registration. No rollback can undo arbitrary external side effects of a third-party OnAddedToContainer callback; staging must exercise those consumers.
- Direct rollback list changes invalidate inventory-weight epochs. Successful transfers retain existing game mutation/invalidation and relevant-client broadcasts.

## Event and packet audit

Triggers remain the existing per-item StormTransfer client commands and the existing pending-action execution path. No new event subscription, timer, persistent OnTick handler, client command or packet type is introduced. Acceptance and execution preflight are bounded, uncached and repeated to catch changed relationships. They do add per-transfer tree validation work; no performance improvement is claimed.

Rejected insertion sends the existing rejection to the requesting player, with no source/destination success broadcasts. Successful container transfer sends existing remove/add packets to the game's relevant connections; floor pickup sends the existing world removal and destination addition only after insertion. The upper bound on recipients remains the number of relevant connected clients for each affected location, plus the requester completion. Existing equipment synchronization is retained after insertion succeeds. No batching or rate-limit settings are changed; repeated corruption warnings are limited to one per 30 seconds with aggregate counters.

The existing OnContainerLooted event now occurs after successful insertion, so a rejected action has no false loot event. OnItemTransferCompleted remains after commit. Consumers that depend on the old pre-move event timing need staging coverage.

Population saving adds no Storm network messages: it restores the engine's deduplicated save requests, five-second per-cell eligibility and locked native path. World Essentials' four native population detours remain unchanged.

No production save, Workshop item or dependent mod is changed by staging-package creation.
