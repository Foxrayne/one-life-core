package io.pzstorm.storm.advice.animalwatersourcefailover;

import io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailover;
import net.bytebuddy.asm.Advice;
import zombie.GameTime;

/**
 * Inlined at the top of {@code BaseAnimalBehavior.update()}. Replays the vanilla failsafe test (a
 * behavior pending longer than {@code behaviorMaxTime}) before the body nulls the action, so a
 * river or puddle drink that timed out counts as a failed approach — see {@link
 * io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailoverPatch}.
 */
public class BehaviorUpdateAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
            @Advice.FieldValue("parent") Object parent,
            @Advice.FieldValue("behaviorAction") Object action,
            @Advice.FieldValue("isDoingBehavior") boolean doing,
            @Advice.FieldValue("behaviorFailsafe") float failsafe,
            @Advice.FieldValue("behaviorMaxTime") float maxTime) {
        if (doing
                && action != null
                && failsafe + GameTime.getInstance().getMultiplier() > maxTime) {
            AnimalWaterSourceFailover.onFailsafe(parent, action);
        }
    }
}
