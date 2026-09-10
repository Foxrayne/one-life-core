package io.pzstorm.storm.advice.client.ballisticsnullguard;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bookkeeping for the frames the ballistics null guards rescued. Counts every skipped call per site
 * and logs the first one, so a report that hits a guard still records the state that would have
 * frozen a vanilla client without turning into its own per-frame wall.
 */
public class BallisticsNullGuard {

    /** Calls that reached the reticle target test with a null controller and were skipped. */
    public static final AtomicLong SKIPPED = new AtomicLong();

    /** Ranged hit-list builds that found a null controller and were skipped. */
    public static final AtomicLong SKIPPED_HIT_LIST = new AtomicLong();

    public static void onNullController() {
        if (SKIPPED.incrementAndGet() == 1) {
            LOGGER.warn(
                    "CombatManagerBallisticsNullGuardPatch: local player's BallisticsController"
                            + " was null during the reticle target test; fell back to the aim-cone"
                            + " check for this frame");
        }
    }

    public static void onNullControllerHitList() {
        if (SKIPPED_HIT_LIST.incrementAndGet() == 1) {
            LOGGER.warn(
                    "CombatManagerBallisticsNullGuardPatch: local player's BallisticsController"
                            + " was null while building a ranged hit list; treated the frame as"
                            + " having no targets");
        }
    }
}
