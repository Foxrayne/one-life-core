package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Wraps {@code zombie.inventory.types.Food.checkEggHatch(IsoHutch)} with an enter/exit advice that
 * re-homes a chick hatched inside a world container from the map origin to that container's square.
 * The fix logic lives in {@link CoopHatchPositionFix#ensureContainerHatchPosition}.
 *
 * <h2>The bug this patches</h2>
 *
 * <p>{@link CoopHatchPositionFixPatch} covers eggs hatching in a nest box. Eggs also hatch from
 * {@code Food.update()}, which calls {@code checkEggHatch(null)}. That branch only resolves x/y/z
 * for a world item, a vehicle trailer, or a player's inventory; an egg stored in a crate, counter
 * or bag keeps {@code (0, 0, 0)}, and the {@code inInv} flag that boxes the chick into the same
 * container also disables the origin sanity guard. Measured on a live 42.20.4 server ten days after
 * the hutch fix shipped: 95 fresh origin chicks boxed in world containers, and one crate holding
 * 152 fertilized eggs about to produce 152 more.
 *
 * <h2>Why an enter/exit pair on {@code checkEggHatch}</h2>
 *
 * <p>The chick only gets a container after {@code AnimalInventoryItem.setAnimal}, and the egg is
 * removed from that container (which nulls its reference) before the method returns. The entry
 * advice therefore captures the egg's container, and the exit advice repairs every
 * origin-positioned boxed animal found in it once the method reports a non-hutch hatch. The
 * position comes from {@code ItemContainer.getSquare()}, which already walks nested bags, vehicle
 * parts and parent objects.
 *
 * <h2>Registration</h2>
 *
 * <p>Gated server-only in {@code StormClassTransformers}, next to {@link
 * CoopHatchPositionFixPatch}.
 */
public class ContainerHatchPositionFixPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.coophatchposition.";

    public ContainerHatchPositionFixPatch() {
        super("zombie.inventory.types.Food");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(
                                typePool.describe(PKG + "ContainerHatchPositionAdvice").resolve(),
                                locator)
                        .on(
                                ElementMatchers.named("checkEggHatch")
                                        .and(ElementMatchers.takesArguments(1))
                                        .and(ElementMatchers.returns(boolean.class))));
    }
}
