package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Fixes the server tick-loop crash triggered when an animal that is due to grow stands on a water
 * square (or next to a duplicate of its own animal ID):
 *
 * <pre>NullPointerException: Cannot invoke "AnimalData.setAge(int)" because the return value of
 *     "IsoAnimal.getData()" is null
 *     at AnimalData.grow(AnimalData.java:1509)
 *     at AnimalData.checkStages(AnimalData.java:137)
 *     at AnimalData.update(AnimalData.java:167)
 *     at IsoAnimal.updateInternal(IsoAnimal.java:509)</pre>
 *
 * <p>{@code grow} constructs the grown replacement on the parent's square. The {@code IsoAnimal}
 * constructor returns early, without creating {@code AnimalData}, when that square is water or when
 * {@code checkForChickenpocalypse()} finds another animal with the same ID nearby. Vanilla only
 * checks those two conditions after it has already dereferenced the new animal's data, so the NPE
 * escapes to {@code IngameState.updateInternal} and, because {@code checkStages()} fires every
 * tick, the world stops advancing until the parent is moved by hand.
 *
 * <p>The patch installs an {@code @Advice.OnMethodEnter(skipOn = OnNonDefaultValue.class)} on
 * {@code AnimalData.grow(String)} that, on the server only, skips the body when either condition
 * holds. That matches what vanilla does for the same cases once it gets past the crash: the new
 * animal is discarded and growth is retried on a later tick.
 */
public class AnimalDataGrowWaterGuardPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.animaldatagrowwaterguard.";

    public AnimalDataGrowWaterGuardPatch() {
        super("zombie.characters.animals.datas.AnimalData");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(
                                typePool.describe(PKG + "AnimalDataGrowWaterGuardAdvice").resolve(),
                                locator)
                        .on(
                                ElementMatchers.named("grow")
                                        .and(ElementMatchers.takesArguments(String.class))));
    }
}
