# rc2 scope correction ? 7 October 2026

User-directed exact upstream alignment now supersedes prior statements below about local non-destructive container safeguards and the retired brush override. See docs/population-container-integration.md for current behaviour and known validation limits. Current candidate is 42.21_2.13.1-rc2; launcher remains 1.2.8-rc1.

# Storm 2.13.1 integration

Baseline f7ae1eb; upstream delta 3659e9f..dcc9b06. Applied as a three-way delta rather than replacing the whole fork. Population saving, transactional container safeguards, Workshop IDs, disabled upstream self-update destinations, and One Life launcher branding are retained. Separate unfinished visual-development worktree is not included.

## Integration differences

Upstream experimental patches retain their JVM gate. Launcher defaults and missing/legacy clientPerfGeneration settings now leave them disabled; selecting the setting and saving records generation 2. Explicit user JVM properties remain respected. PvpHitsAlwaysDamage defaults false and requires full Java/sandbox/translation deployment. Upstream server-load optimisations are always registered on servers.

The fork had deliberately removed upstream LogReport/SendLogsDialog and its third-party webhook. The required-description workflow is adapted to local export: up to eight explicitly selected log files, at most the last 1 MiB each, a 16,000-character description ceiling, OS-account scrubbing, background ZIP creation, no network requests or saved configuration files. Users review and attach the report to One Life support. This is not upstream upload parity. The original screenshot depicting the upstream launcher is not displayed in fork guidance; written instructions use One Life IDs.

## Packet and scheduling audit

Bootstrap verification: one client command on the first tick, callback immediately removed; one response targeted to its requesting player, no broadcast or retry timer. Server responder ignores unrelated modules/commands. RAM reporting is likewise one first-tick report, callback removed. Existing explicit server-triggered RAM guidance remains. Lua fixture executes both join callbacks and bootstrap dispatch with global next unavailable. This is a focused fixture, not a complete Kahlua/UI simulation.

PvP option adds no packets; it changes decoded hit flags in the existing server parse path only when enabled. Watchdog retains existing per-tick metrics/threshold counting and sends a kick only at enforcement, now exempting the first five minutes after spawn. Server-load work is confined to loading/validation calls, with a per-call room lookup rather than a persistent cache. Client rendering changes add no network recipients; upstream fence/cache invalidation and bounded vehicle-animation fallback are retained and tested offline. Real GPU behaviour remains an in-game acceptance item.

## Acceptance

Offline server/client/launcher tests, new report and configuration migration tests, packaged source resources, SDAC composition both orders and WE injury test must pass. Check the packaged manifest and exactly one Core JAR. Live Linux startup, join/rejoin, container transfer/save, population unload/reload, PvP on/off, XL trees and player-built rooms, and each optional graphics feature need staging acceptance. No new claim about SDAC runtime policy, native ABI execution, or vehicle-teleport prevention.

## Completed offline validation

{
  "build/test-results/test": {
    "tests": 1720,
    "failures": 0,
    "errors": 0,
    "skipped": 1
  },
  "build/test-results/clientPatchTest": {
    "tests": 238,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "launcher/build/test-results/test": {
    "tests": 202,
    "failures": 0,
    "errors": 0,
    "skipped": 4
  }
}

SDAC composition: 346 targets in each order, zero errors or lost SDAC calls. WE injury-scope and packet-composition checks pass. Lua next-unavailable fixture passes. Full formatter was blocked by an unrelated shared Gradle P2 lock; Java formatter completed. No live session or publication performed.
