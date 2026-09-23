package io.pzstorm.storm.patch.fixes;

import io.pzstorm.storm.core.StormClassTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;

/**
 * Attaches enter/exit advice to {@code zombie.network.packets.SyncZonePacket.parse} so the server
 * judges every animal-zone add, rename, and removal against safehouse membership before vanilla's
 * {@code processServer} relays it. The rule and the client-correction protocol live in {@link
 * AnimalZoneSafehouseGuard}.
 *
 * <h2>Why {@code parse}</h2>
 *
 * <p>Vanilla creates or overwrites the zone inside {@code parse} ({@code DesignationZone.load}), so
 * by {@code processServer} an unauthorised add or rename has already landed and the old values are
 * gone. The advice brackets {@code parse} instead: entry peeks the header and snapshots, exit
 * judges and, on a veto, nulls the packet's {@code designationZone} so {@code processServer} does
 * nothing.
 *
 * <h2>Registration</h2>
 *
 * <p>Gated server-only in {@code StormClassTransformers}. The guard is inert unless {@code
 * Storm.AnimalZoneSafehouseProtection} is on, and a throw latches it off permanently.
 */
public class SyncZonePacketSafehouseGuardPatch extends StormClassTransformer {

    private static final String PKG = "io.pzstorm.storm.advice.synczoneguard.";

    public SyncZonePacketSafehouseGuardPatch() {
        super("zombie.network.packets.SyncZonePacket");
    }

    @Override
    public DynamicType.Builder<Object> dynamicType(
            ClassFileLocator locator, TypePool typePool, DynamicType.Builder<Object> builder) {
        return builder.visit(
                Advice.to(typePool.describe(PKG + "SyncZoneParseAdvice").resolve(), locator)
                        .on(ElementMatchers.named("parse").and(ElementMatchers.takesArguments(2))));
    }
}
