package io.pzstorm.storm.advice.animalwatersourcefailover;

import io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailover;
import net.bytebuddy.asm.Advice;

/**
 * Inlined at the top of the private {@code BaseAnimalBehavior.tryDrinkFromRiver()}. Returning true
 * skips the vanilla body, and a skipped boolean method returns false, so {@code checkDrinkBehavior}
 * moves on to the next water source — see {@link
 * io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailoverPatch}.
 */
public class TryDrinkFromRiverAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.FieldValue("parent") Object parent) {
        return AnimalWaterSourceFailover.skipRiver(parent);
    }
}
