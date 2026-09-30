# One Life Core 42.21_2.11.7-rc1 staging

This manual candidate contains only population-saving and container-protection changes over published rc2. The source branch is feature/population-container-safety. It is not installed or published.

## Install on isolated staging only

1. Stop the test server and clients; back up their Core folders and clone the save/configuration.
2. Verify the extracted package with `py verify-package.py` (Windows) or `python3 verify-package.py` (Linux).
3. Replace the complete staging Core folder with `One Life Core/Contents/mods/storm`. Keep the existing bootstrap/launcher setup and mod ID one-life-core-b42. Do not leave the rc2 Core JAR alongside the candidate. Prevent Workshop synchronization from overwriting the manual test copy.
4. Use the reviewed 42.21 game and Linux population library (hashes in VALIDATION.json), with World Essentials enabled. Both server and test clients must load 42.21_2.11.7-rc1; check the logs for the new container transformers. No game JAR replacement is supplied.

## Acceptance

- Startup: no transformer failures, missing methods or verification errors; joining/reconnecting works.
- Inventories: ordinary nested bags, outward transfers, floor pickup, equipped-item transfers, full/restricted destinations and queued actions. Rejection must preserve items and not report success.
- Persistence: compare identical cloned saves against rc2, with fixed population settings. Travel away/return repeatedly, autosave, then restart. Record counts, errors and save latency; distinguish natural spawn/migration from persistence effects.
- Two clients: verify item ownership/counts, equipment, inventory weight, source/destination updates and reconnect persistence. Exercise corpse recovery, economy sales, quest deliveries and event crates.
- Existing corrupt inventory: getters do not silently detach items. Transfers/saves of invalid or over-budget trees are refused with a rate-limited diagnostic. This is containment, not automatic recovery; retain the original save for explicit repair. Do not create corruption in production.
- Control regression: ranged combat and normal player lifecycle continue to work. Painting/furniture issues are outside this candidate.

## Limits and rollback

Live Linux acceptance is pending. Local tests do not establish a cure for earlier server stalls or mass disconnects. There is no new recurring inventory scan. Parent lookup cap: 1024; descendant preflight cap: 65536 items and 128 nesting edges. Budget exhaustion refuses work, not items.

Stop staging processes, restore the complete rc2 Core folders and pre-test save snapshot, then restart. The original rc2 archive in the sibling One Life Core Staging/build folder is retained. Do not reuse a candidate-mutated test save as the production rollback snapshot.
