package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Files every {@code vehicles.db} row under the chunk containing its saved {@code x,y} instead of
 * the vehicle's chunk pointer, which can be stale or recycled. Rationale and mechanism in {@link
 * VehiclesDbChunkKeyFix}; {@link VehiclesDbChunkKeyRepairPatch} recovers rows already misfiled.
 */
public class VehiclesDbChunkKeyPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.vehiclesdbchunkkey.";

    public VehiclesDbChunkKeyPatch() {
        super("zombie.vehicles.VehiclesDB2$VehicleBuffer");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(typePool.describe(PKG + "VehicleBufferSetAdvice").resolve(), locator)
                        .on(
                                ElementMatchers.named("set")
                                        .and(ElementMatchers.takesArguments(1))
                                        .and(
                                                ElementMatchers.takesArgument(
                                                        0,
                                                        ElementMatchers.named(
                                                                "zombie.vehicles.BaseVehicle")))));
    }
}
