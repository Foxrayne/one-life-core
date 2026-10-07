# One Life Core 42.21_2.13.1-rc1 staging

Unpublished candidate, launcher 1.2.8-rc1, upstream main dcc9b06. Includes the earlier population/container work. See CHANGELOG.md and docs/upstream-2.13.1-integration.md.

Use an isolated Linux server and save copy, matching PZ 42.21 game/native files, current World Essentials and the audited SDAC Update JAR. Preserve the deployed rc2 package as rollback. Install the complete Contents/mods/storm payload, including media, sandbox translations, launcher and bootstrap; do not combine old/new Core JARs. Replace the staging mod directory as a unit while stopped. This package contains no server configuration or save files.

Start server and connect real clients using the staged launcher. Check actual joins, UDP health, damage protection/expiry, looting/nested bags/safehouse access, vehicles/passengers/towing, zombie populations through unload/save/restart and dependent quest/event/economy/zone actions. Enable PvP damage override only for a deliberate comparison.

Experimental client performance is off for new and legacy unversioned launcher settings until selected again. Existing explicit JVM overrides remain authoritative. Test optional graphics individually, including GPU fallback, puddles/rain, cutaway transitions, parked/moving vehicles, and player-built rooms. Verify report export and launcher UI interactively. No live Linux/GPU acceptance has been claimed by offline tests.

Do not publish until live acceptance and explicit publication instruction. No deployment or automatic report upload is performed by the build.
