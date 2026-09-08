package io.pzstorm.storm.advice.animalwatersourcefailover;

import io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailover;
import net.bytebuddy.asm.Advice;

/**
 * Inlined at the top of the private {@code BaseAnimalBehavior.tryDrinkFromPuddle()}. Returning true
 * skips the vanilla body so the method returns false — see {@link
 * io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailoverPatch}.
 */
public class TryDrinkFromPuddleAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.FieldValue("parent") Object parent) {
        return AnimalWaterSourceFailover.skipPuddle(parent);
    }
}
