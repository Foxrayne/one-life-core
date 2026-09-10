package io.pzstorm.storm.advice.animalwatersourcefailover;

import io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailover;
import net.bytebuddy.asm.Advice;

/**
 * Inlined around {@code BaseAnimalBehavior.doBehaviorAction()}. Entry samples the pending action
 * and whether a behavior was actually in progress; exit lets the fix judge, from the animal's
 * drink-square fields, whether a river or puddle approach succeeded — see {@link
 * io.pzstorm.storm.patch.fixes.AnimalWaterSourceFailoverPatch}.
 */
public class DoBehaviorActionAdvice {

    @Advice.OnMethodEnter
    public static Object onEnter(
            @Advice.FieldValue("behaviorAction") Object action,
            @Advice.FieldValue("isDoingBehavior") boolean doing) {
        return doing ? action : null;
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.FieldValue("parent") Object parent, @Advice.Enter Object pendingAction) {
        if (pendingAction != null) {
            AnimalWaterSourceFailover.onBehaviorActionDone(parent, pendingAction, true);
        }
    }
}
