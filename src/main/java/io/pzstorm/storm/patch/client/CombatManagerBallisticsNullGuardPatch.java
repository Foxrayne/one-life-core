package io.pzstorm.storm.patch.client;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Client-only. Guards the two null {@code BallisticsController} dereferences on the reticle path in
 * {@code CombatManager}:
 *
 * <pre>NullPointerException: Cannot invoke
 *   "zombie.core.physics.BallisticsController.getIsoAimingPosition()"
 *   because "ballisticsController" is null
 *   at CombatManager.isHittableBallisticsTarget ... calculateHitInfoList ... updateReticle
 *
 * NullPointerException: Cannot invoke
 *   "zombie.core.physics.BallisticsController.isValidTarget(int)"
 *   because "ballisticsController" is null
 *   at CombatManager.calculateHitInfoList ... updateReticle
 *   at CombatManager.update at GameWindow.logic</pre>
 *
 * <p>The throw latches rather than recovering. {@code GameWindow.logic} calls {@code
 * CombatManager.update()} <em>before</em> {@code states.update()}, and the controller is only
 * (re)allocated by {@code IsoGameCharacter.updateBallistics()} inside the character update that
 * {@code states.update()} drives. Once the reticle path throws, {@code states.update()} is skipped
 * for that frame, the controller is never rebuilt, and the same NPE repeats on every subsequent
 * frame while the main loop keeps running; {@code IsoWorld.frameNo} stops advancing.
 *
 * <p>Producer: the local player dying while aiming a ranged weapon. {@code IsoDeadBody} calls
 * {@code removeFromWorld()} on the character, which is the only caller of {@code
 * releaseBallisticsController()}; the aiming flag survives, so the next frame's {@code
 * updateReticle} takes the ranged branch against a released controller. Field bundle: the server
 * chat kill message lands milliseconds before the first guard warning.
 *
 * <p>Two sites. The 3-arg {@code isHittableBallisticsTarget} dereferences the controller unguarded
 * while its sibling {@code calculateBallistics} null-checks the identical field; Storm skips the
 * 3-arg body when the controller is null so it returns {@code false}, and the 4-arg caller falls
 * through to its existing {@code isPointWithinDistance} cone test. A few statements later {@code
 * calculateHitInfoList} reads the same field and dereferences it inside its ranged target filter;
 * Storm skips that body when the controller is null and the resolved weapon is ranged, clearing the
 * hit list so the frame reports no targets. The character update on the same frame (or the respawn)
 * reallocates the controller.
 *
 * <p>Why a client bytecode patch: the throw is inside the client's per-frame combat update, a
 * private Java method with no Lua event and no server-observable state, so none of the cheaper
 * tiers reach it. Fail-soft: each advice is a null test on the value the vanilla body is about to
 * dereference, {@code suppress = Throwable.class} resolves any advice failure to "run vanilla", and
 * a missing target method fails the transform loudly at weave time (logged, class left vanilla)
 * rather than silently no-opping.
 */
public class CombatManagerBallisticsNullGuardPatch extends StormClassTransformer {

    private static final String TARGET = "zombie.CombatManager";
    private static final String ADVICE_PACKAGE =
            "io.pzstorm.storm.advice.client.ballisticsnullguard.";

    private static final String RETICLE_TEST = "isHittableBallisticsTarget";
    private static final String RETICLE_TEST_ADVICE =
            ADVICE_PACKAGE + "CombatManagerBallisticsNullGuardAdvice";

    private static final String HIT_LIST = "calculateHitInfoList";
    private static final String HIT_LIST_ADVICE =
            ADVICE_PACKAGE + "CombatManagerCalculateHitInfoListAdvice";

    public CombatManagerBallisticsNullGuardPatch() {
        super(TARGET);
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        TypeDescription target = typePool.describe(TARGET).resolve();
        ElementMatcher.Junction<MethodDescription> reticleTest =
                ElementMatchers.named(RETICLE_TEST).and(ElementMatchers.takesArguments(3));
        ElementMatcher.Junction<MethodDescription> hitList =
                ElementMatchers.named(HIT_LIST).and(ElementMatchers.takesArguments(1));
        requireDeclared(target, reticleTest, "the 3-arg " + RETICLE_TEST + " overload");
        requireDeclared(target, hitList, "the 1-arg " + HIT_LIST);
        return builder.visit(
                        Advice.to(typePool.describe(RETICLE_TEST_ADVICE).resolve(), locator)
                                .on(reticleTest))
                .visit(
                        Advice.to(typePool.describe(HIT_LIST_ADVICE).resolve(), locator)
                                .on(hitList));
    }

    private static void requireDeclared(
            TypeDescription target,
            ElementMatcher<? super MethodDescription> matcher,
            String description) {
        if (target.getDeclaredMethods().filter(matcher).isEmpty()) {
            throw new IllegalStateException(
                    "CombatManagerBallisticsNullGuardPatch: CombatManager no longer declares "
                            + description
                            + " — the hook would silently no-op and reintroduce the"
                            + " null-controller NPE latch. Re-verify the patch against the"
                            + " current game source.");
        }
    }
}
