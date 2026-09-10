package io.pzstorm.storm.patch.fixes;

import static io.pzstorm.storm.logging.StormLogger.LOGGER;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import zombie.characters.animals.IsoAnimal;
import zombie.characters.animals.behavior.BehaviorAction;

/**
 * Pure logic behind {@link AnimalWaterSourceFailoverPatch}: lets a thirsty animal fall through to
 * the next water source after a river or puddle approach has failed, instead of retrying the same
 * unreachable water forever.
 *
 * <h2>The bug this heals</h2>
 *
 * <p>{@code BaseAnimalBehavior.checkDrinkBehavior} tries water sources in a fixed order — river,
 * puddle, ground water, trough — and the first branch that returns true wins. {@code
 * tryDrinkFromRiver} returns true for any random square on the zone's {@code nearWaterSquares}
 * list, without asking whether the animal can reach it. When a zone spans two pens and the water is
 * in the other one, or the only route to the river is through a fence, the animal paths toward the
 * water, fails, wall-follows, and arrives too far away to drink. {@code drinkFromRiver} then does
 * nothing, and the next check (500 multiplier units, 150 once thirst passes 0.5) picks the river
 * again. The trough branch at the end of the list never runs, so the animal dies of thirst beside a
 * full trough it would have used had the river not existed.
 *
 * <h2>The fix</h2>
 *
 * <p>Three advices on {@code BaseAnimalBehavior}, all gated on {@code Storm.AnimalZoneContainment}
 * because containment is what turns a formerly thump-through "success" into a path failure:
 *
 * <ul>
 *   <li>{@code doBehaviorAction()} — when the action was a river or puddle drink and afterwards the
 *       animal has no {@code drinkFromRiver} / {@code drinkFromPuddle} square, the approach failed
 *       and that source kind goes on a cooldown for the animal. A successful drink clears it.
 *   <li>{@code update()} — the vanilla failsafe (a behavior pending longer than {@code
 *       behaviorMaxTime}) is treated the same way for a pending river or puddle drink, before the
 *       body nulls the action.
 *   <li>{@code tryDrinkFromRiver()} / {@code tryDrinkFromPuddle()} — skipped (return false) while
 *       the source kind is on cooldown, so {@code checkDrinkBehavior} continues to ground water and
 *       troughs. The cooldown is {@link #RETRY_AFTER_MS} of wall-clock time; after it the river
 *       gets one more try, so water that becomes reachable again is picked back up.
 * </ul>
 *
 * <p>Any throw latches {@link #broken} and every entry point becomes a no-op — vanilla's fixed
 * order returns and the server keeps running.
 */
public final class AnimalWaterSourceFailover {

    /** Wall-clock time a failed river/puddle source is skipped before the animal retries it. */
    public static final long RETRY_AFTER_MS = TimeUnit.MINUTES.toMillis(10);

    /** Permanent fail-soft latch: any throw reverts to vanilla's fixed source order. */
    private static volatile boolean broken;

    private static final LongAdder FAILOVERS = new LongAdder();

    private static final Map<IsoAnimal, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalWaterSourceFailover() {}

    private static final class State {
        long riverRetryAt;
        long puddleRetryAt;
    }

    private static boolean active() {
        return !broken && AnimalZoneContainment.isEnabled();
    }

    /** Enter driver for {@code tryDrinkFromRiver()}: true skips the vanilla body. */
    public static boolean skipRiver(Object parentRef) {
        if (!active()) {
            return false;
        }
        try {
            State state = STATES.get((IsoAnimal) parentRef);
            return state != null && onCooldown(state.riverRetryAt);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** Enter driver for {@code tryDrinkFromPuddle()}: true skips the vanilla body. */
    public static boolean skipPuddle(Object parentRef) {
        if (!active()) {
            return false;
        }
        try {
            State state = STATES.get((IsoAnimal) parentRef);
            return state != null && onCooldown(state.puddleRetryAt);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /**
     * Exit driver for {@code doBehaviorAction()}. {@code action} is the behavior action sampled on
     * entry; the vanilla body has already run and either handed the animal its drink square or not.
     */
    public static void onBehaviorActionDone(Object parentRef, Object action, boolean wasDoing) {
        if (!wasDoing || action == null || !active()) {
            return;
        }
        try {
            IsoAnimal animal = (IsoAnimal) parentRef;
            if (action == BehaviorAction.DRINKFROMRIVER) {
                settle(animal, true, animal.drinkFromRiver != null);
            } else if (action == BehaviorAction.DRINKFROMPUDDLE) {
                settle(animal, false, animal.drinkFromPuddle != null);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Enter driver for {@code update()}: called with the pending action when the vanilla failsafe
     * is about to fire on this pass.
     */
    public static void onFailsafe(Object parentRef, Object action) {
        if (action == null || !active()) {
            return;
        }
        try {
            IsoAnimal animal = (IsoAnimal) parentRef;
            if (action == BehaviorAction.DRINKFROMRIVER) {
                settle(animal, true, false);
            } else if (action == BehaviorAction.DRINKFROMPUDDLE) {
                settle(animal, false, false);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void settle(IsoAnimal animal, boolean river, boolean success) {
        if (success) {
            State state = STATES.get(animal);
            if (state != null) {
                if (river) {
                    state.riverRetryAt = 0L;
                } else {
                    state.puddleRetryAt = 0L;
                }
            }
            return;
        }
        State state = STATES.computeIfAbsent(animal, k -> new State());
        long retryAt = System.currentTimeMillis() + RETRY_AFTER_MS;
        if (river) {
            state.riverRetryAt = retryAt;
        } else {
            state.puddleRetryAt = retryAt;
        }
        FAILOVERS.increment();
    }

    private static boolean onCooldown(long retryAt) {
        return retryAt != 0L && System.currentTimeMillis() < retryAt;
    }

    private static void fail(Throwable t) {
        broken = true;
        LOGGER.error(
                "Storm: animal water-source failover failed; reverting to vanilla behavior", t);
    }

    public static boolean isBroken() {
        return broken;
    }

    public static long getFailovers() {
        return FAILOVERS.sum();
    }

    /** Test hook: clears the fail-soft latch and all per-animal cooldowns. */
    public static void reset() {
        broken = false;
        STATES.clear();
    }
}
