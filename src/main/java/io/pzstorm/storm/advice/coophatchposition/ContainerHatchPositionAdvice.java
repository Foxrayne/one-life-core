package io.pzstorm.storm.advice.coophatchposition;

import io.pzstorm.storm.patch.fixes.CoopHatchPositionFix;
import net.bytebuddy.asm.Advice;

/**
 * Wraps {@code zombie.inventory.types.Food.checkEggHatch(IsoHutch)}. The entry half captures the
 * egg's container (the hatch removes the egg from it), the exit half hands that container to {@link
 * CoopHatchPositionFix#ensureContainerHatchPosition(Object, boolean, Object)} so a chick hatched
 * inside a crate, counter or bag is re-homed from the origin to the container's square. See {@link
 * io.pzstorm.storm.patch.fixes.ContainerHatchPositionFixPatch} for the rationale.
 *
 * <p>All game-typed values are passed as {@code Object} for the same elided-checkcast reason
 * documented on {@link CoopHatchPositionAdvice}.
 */
public class ContainerHatchPositionAdvice {

    @Advice.OnMethodEnter
    public static Object onEnter(@Advice.This Object egg, @Advice.Argument(0) Object hutch) {
        if (hutch != null) {
            return null;
        }
        return CoopHatchPositionFix.captureHatchContainer(egg);
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(0) Object hutch,
            @Advice.Return boolean hatched,
            @Advice.Enter Object container) {
        CoopHatchPositionFix.ensureContainerHatchPosition(hutch, hatched, container);
    }
}
