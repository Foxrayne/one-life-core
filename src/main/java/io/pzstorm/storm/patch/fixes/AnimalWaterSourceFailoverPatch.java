package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Advises {@code zombie.characters.animals.behavior.BaseAnimalBehavior} so a thirsty animal whose
 * river or puddle approach failed falls through to ground water and troughs instead of retrying the
 * same unreachable water until it dies. The fix logic lives in {@link AnimalWaterSourceFailover}.
 *
 * <h2>The bug this patches</h2>
 *
 * <p>{@code checkDrinkBehavior} tries river, puddle, ground water, trough in that order and stops
 * at the first branch that returns true. The river branch returns true for any random square on the
 * zone's water list with no reachability check, so a zone whose water sits in a different pen (or
 * behind a fence) never lets the animal reach the trough branch. Containment makes this worse
 * because the animal can no longer chew through the fence to the water.
 *
 * <h2>Registration</h2>
 *
 * <p>Gated server-only in {@code StormClassTransformers}; animal behavior runs on the authoritative
 * side. Gated at runtime on {@code Storm.AnimalZoneContainment} — off means vanilla order. A throw
 * in the fix latches it off permanently.
 */
public class AnimalWaterSourceFailoverPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.animalwatersourcefailover.";

    public AnimalWaterSourceFailoverPatch() {
        super("zombie.characters.animals.behavior.BaseAnimalBehavior");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                        advice(typePool, locator, "TryDrinkFromRiverAdvice")
                                .on(
                                        ElementMatchers.named("tryDrinkFromRiver")
                                                .and(ElementMatchers.takesArguments(0))))
                .visit(
                        advice(typePool, locator, "TryDrinkFromPuddleAdvice")
                                .on(
                                        ElementMatchers.named("tryDrinkFromPuddle")
                                                .and(ElementMatchers.takesArguments(0))))
                .visit(
                        advice(typePool, locator, "DoBehaviorActionAdvice")
                                .on(
                                        ElementMatchers.named("doBehaviorAction")
                                                .and(ElementMatchers.takesArguments(0))))
                .visit(
                        advice(typePool, locator, "BehaviorUpdateAdvice")
                                .on(
                                        ElementMatchers.named("update")
                                                .and(ElementMatchers.takesArguments(0))));
    }

    private static Advice advice(TypePool typePool, ClassFileLocator locator, String name) {
        return Advice.to(typePool.describe(PKG + name).resolve(), locator);
    }
}
