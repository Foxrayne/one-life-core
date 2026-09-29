package io.pzstorm.launcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entry for the {@code --steam-update} child process. Assumes cwd = game install (parent guarantees
 * it). Prints one progress line per event to stdout; the parent relays them into the launcher log.
 */
final class SteamUpdateChild {

    static final int EXIT_SOME_FAILED = 1;
    static final int EXIT_STEAM_UNAVAILABLE = 2;

    /**
     * Marks an id the parent already proved current against the published workshop metadata: it
     * gets an instant local state check instead of the per-item DownloadItem confirm, escalating to
     * the full update only when the state is not join-ready.
     */
    static final String VERIFY_PREFIX = "verify:";

    /**
     * Marks an id whose install record Steam refuses to refresh (content current, install timestamp
     * desynced — see {@link SteamUgc#repairItem}): the child cycles the item's subscription before
     * the forced download so Steam commits fresh install metadata.
     */
    static final String REPAIR_PREFIX = "repair:";

    /**
     * Marks an id the anonymous Web API can't see (private or friends-only items). The child looks
     * up all such ids' published {@code time_updated} in one logged-in ISteamUGC query and skips
     * the DownloadItem confirm for those whose install stamp already matches. If the query is
     * unavailable, every {@code check:} id gets the full update.
     */
    static final String CHECK_PREFIX = "check:";

    private SteamUpdateChild() {}

    static int run(String[] itemIds) {
        Path gameDir = Paths.get("").toAbsolutePath();
        try (SteamUgc steam = SteamUgc.connect(gameDir)) {
            List<Long> checkIds = new ArrayList<>();
            for (String id : itemIds) {
                if (id.startsWith(CHECK_PREFIX)) {
                    try {
                        checkIds.add(Long.parseLong(id.substring(CHECK_PREFIX.length()).trim()));
                    } catch (NumberFormatException ignored) {
                        // reported again, identically, by the main loop below
                    }
                }
            }
            Optional<Map<Long, Long>> published = Optional.of(Map.of());
            if (!checkIds.isEmpty()) {
                try {
                    published = steam.publishedTimes(checkIds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    published = Optional.empty();
                }
                if (published.isEmpty()) {
                    System.out.println(
                            "check: published-version query unavailable; full update for "
                                    + checkIds.size()
                                    + " item(s)");
                }
            }
            Map<Long, Long> publishedTimes = published.orElse(Map.of());

            int failures = 0;
            for (String id : itemIds) {
                boolean check = id.startsWith(CHECK_PREFIX);
                boolean verifyOnly = id.startsWith(VERIFY_PREFIX);
                boolean repair = id.startsWith(REPAIR_PREFIX);
                String rawId =
                        check
                                ? id.substring(CHECK_PREFIX.length())
                                : verifyOnly
                                        ? id.substring(VERIFY_PREFIX.length())
                                        : repair ? id.substring(REPAIR_PREFIX.length()) : id;
                long itemId;
                try {
                    itemId = Long.parseLong(rawId.trim());
                } catch (NumberFormatException e) {
                    System.out.println("item " + rawId + " FAILED (not a workshop id)");
                    failures++;
                    continue;
                }
                try {
                    boolean ok =
                            check
                                    ? steam.checkItem(
                                            itemId, publishedTimes.get(itemId), System.out::println)
                                    : repair
                                            ? steam.repairItem(itemId, System.out::println)
                                            : verifyOnly
                                                    ? steam.verifyItem(itemId, System.out::println)
                                                    : steam.updateItem(itemId, System.out::println);
                    if (!ok) {
                        failures++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("item " + itemId + " FAILED (interrupted)");
                    return EXIT_SOME_FAILED;
                }
            }
            return failures == 0 ? 0 : EXIT_SOME_FAILED;
        } catch (SteamUgc.SteamException e) {
            System.out.println("steam unavailable: " + e.getMessage());
            return EXIT_STEAM_UNAVAILABLE;
        }
    }
}
