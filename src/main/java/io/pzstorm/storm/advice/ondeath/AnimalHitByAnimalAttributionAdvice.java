package io.pzstorm.storm.advice.ondeath;

import net.bytebuddy.asm.Advice;

/**
 * Inlined at the exit of {@code IsoAnimal.HitByAnimal(IsoAnimal, boolean)} to stamp {@code
 * attackedBy} when the blow was fatal. Animal fights ({@code AnimalAttackState}) drain health
 * through this method without ever recording the opponent, so the state-machine death that follows
 * — {@code die()} → {@code Kill(getAttackedBy())} — would otherwise arrive with a null killer. No
 * event is fired here; the normal {@code OnDeath} path handles that.
 */
public class AnimalHitByAnimalAttributionAdvice {

    @Advice.OnMethodExit
    public static void onExit(@Advice.This Object self, @Advice.Argument(0) Object attacker) {
        AnimalDeathEvents.attributeKill(self, attacker);
    }
}
