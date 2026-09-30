# One Life Core 2.11.6-rc2: staging instructions

This is the stability-only replacement for rc1. Use rc2 for the staging session.
The unfinished client visual renderer, shader resources, options Lua, and lifecycle
hooks are absent, not merely disabled. Existing published rendering fixes remain.

## What is included

- Join-grace protection and clearer send-buffer kick diagnostics.
- TCP chunk delivery bookkeeping/locking and 42.21 join-prewarm correction.
- Client hutch null-slot crash guard, with corrected logging.
- ServerLOS resume after warm-cell eviction/drain exceptions.
- Required local 42.21 compatibility work: player profiles, reviewed CRC/action
  fixes, animal growth, packet registration, population ABI checks, and brush replay.
- Existing One/Life identity, privacy behavior, and launcher/bootstrap defaults.

This is a manual staging package. Its mod ID remains `one-life-core-b42` so dependent
mods resolve normally. It is not a separate Workshop staging listing; no listing
has been created and nothing has been published. Do not load this alongside another
copy of Core. The test server and test clients must use the same candidate.

## Prepare the staging session

1. Use an isolated 42.21 staging server and test clients with the normal mod set.
   Clone the relevant save/configuration if reproducing production behavior.
   Stop those processes and back up their current Core folder and staging save.
2. Extract the archive and run `python verify-package.py` (or `py verify-package.py`
   on Windows) from its top-level folder. It verifies every payload file and the
   absence of the unfinished visual code in the packaged Core JAR and loose media.
3. In the staging environment only, replace the complete existing Core mod folder
   with `One Life Core/Contents/mods/storm` from the archive. Keep the expected path
   used by that environment's existing bootstrap/launcher setup. Replace the folder;
   do not merge `42/lib` and leave the old versioned Core JAR beside the candidate.
4. Keep the existing server startup and bootstrap configuration. Do not replace the
   game JAR. The package contains `storm-42.21_2.11.6-rc2.jar` and matching loose media.
   Check that Steam/Workshop synchronization does not replace the manual candidate
   before launch. Confirm the loaded Core version on server and clients is
   `42.21_2.11.6-rc2`, and that no second Core JAR is being loaded.

## Acceptance checks

Record the server/client logs and timestamps for each check.

| Check | Expected result |
| --- | --- |
| Startup with the normal mod set | Server becomes available; no new transformer failures, VerifyError, missing-method errors, or visual renderer initialization. |
| Existing and fresh characters join; disconnect and rejoin | Profiles and character selection work; no premature send-buffer kick during vanilla join grace. |
| Move through newly streamed areas | Chunks arrive; no persistent loading holes or stuck joining. |
| Controlled TCP failure on staging | Existing UDP fallback completes outstanding chunk requests; do not disrupt production networking. |
| Approach/leave a hutch and release animals | No null-animal crash; valid animals remain. A normal hutch produces no null-slot warning. |
| Warm-cell eviction under normal travel | Server continues ticking and LOS resumes after unload. Capture logs/thread dumps if an unload throws; do not deliberately corrupt a live save. |
| Existing gameplay | Player actions, saving/reloading, brush placement, and custom mods still behave normally. |

Local regression tests exercise the pause exception path with controlled callbacks;
they do not prove that a live, partially unloaded cell is repaired. One expensive
unload can still stall the main thread. The earlier mass-disconnect root cause is
not established, and this package is not claimed to fix it conclusively.

## Rollback

Stop the staging server and clients, restore the complete backed-up Core folders,
and restore the staging save snapshot if the session changed it. Confirm only the
restored Core JAR remains before restarting. Production files are not part of this
procedure.

## Validation evidence

See `VALIDATION.json` for the test counts and game/artifact hashes, and
`SHA256-MANIFEST.json` for the payload checksums. Linux multiplayer acceptance is
pending; automated results are not a substitute for the checks above.
