package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Boot-time repair pass over {@code vehicles.db}: rewrites {@code wx/wy} for every row whose key
 * disagrees with its {@code x,y}, before {@code SQLStore.initUsedIDs} builds the seen-chunks set
 * from those columns. Recovers vehicles that earlier sessions misfiled (they load at their real
 * position on the next chunk load). Rationale in {@link VehiclesDbChunkKeyFix}.
 */
public class VehiclesDbChunkKeyRepairPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.vehiclesdbchunkkey.";

    public VehiclesDbChunkKeyRepairPatch() {
        super("zombie.vehicles.VehiclesDB2$SQLStore");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(typePool.describe(PKG + "SqlStoreInitUsedIdsAdvice").resolve(), locator)
                        .on(ElementMatchers.named("initUsedIDs")));
    }
}
