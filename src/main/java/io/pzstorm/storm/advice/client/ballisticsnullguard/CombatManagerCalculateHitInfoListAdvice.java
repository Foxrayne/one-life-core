package io.pzstorm.storm.advice.client.ballisticsnullguard;

import net.bytebuddy.asm.Advice;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoLivingCharacter;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameClient;

/**
 * Advice for {@code CombatManager.calculateHitInfoList(IsoGameCharacter)} that skips the body when
 * the owner's {@code BallisticsController} is {@code null} and the weapon being resolved is ranged.
 *
 * <p>The ranged branch of the body dereferences the controller unguarded while filtering the sorted
 * hit list ({@code ballisticsController.isValidTarget(...)}), after {@code
 * isHittableBallisticsTarget} — which {@link CombatManagerBallisticsNullGuardAdvice} already guards
 * — has run. The weapon is resolved exactly as the body does ({@code AttackVars.getWeapon(owner)}),
 * so melee and shove calls are untouched. On the skip path the hit list is cleared and the target
 * flag refreshed, which is vanilla's own "no targets this frame" outcome. {@code suppress} makes
 * any advice failure resolve to "run vanilla".
 */
public class CombatManagerCalculateHitInfoListAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(@Advice.Argument(0) IsoGameCharacter owner) {
        if (GameClient.client && !owner.isLocal()) {
            return false;
        }
        if (owner.getBallisticsController() != null) {
            return false;
        }
        HandWeapon weapon = owner.getAttackVars().getWeapon((IsoLivingCharacter) owner);
        if (weapon == null || !weapon.isRanged()) {
            return false;
        }
        if (!owner.getHitInfoList().isEmpty()) {
            owner.clearHitInfo();
        }
        owner.updateHasTargetFlag();
        BallisticsNullGuard.onNullControllerHitList();
        return true;
    }
}
