package io.pzstorm.storm.metrics;

import io.prometheus.metrics.core.metrics.CounterWithCallback;
import java.util.concurrent.atomic.LongAdder;

/** Upstream metric names retained; unsafe trees are blocked, never detached automatically. */
public final class ContainerChainGuardMetrics {
    private static final LongAdder blocked = new LongAdder();
    private static final LongAdder refused = new LongAdder();
    private static final CounterWithCallback BLOCKED =
            CounterWithCallback.builder()
                    .name("storm_container_chain_operations_blocked_total")
                    .help(
                            "Unsafe or over-budget inventory operations blocked without deleting items.")
                    .callback(callback -> callback.call((double) blocked.sum()))
                    .register(StormPrometheus.registry());
    private static final CounterWithCallback REPAIRED =
            CounterWithCallback.builder()
                    .name("storm_container_chain_cycles_repaired_total")
                    .help(
                            "Upstream compatibility metric; automatic item detachment is disabled (always zero).")
                    .callback(callback -> callback.call(0.0))
                    .register(StormPrometheus.registry());
    private static final CounterWithCallback REFUSED =
            CounterWithCallback.builder()
                    .name("storm_container_chain_nestings_refused_total")
                    .help("Unsafe AddItem operations refused; repeated warnings are rate limited.")
                    .callback(callback -> callback.call((double) refused.sum()))
                    .register(StormPrometheus.registry());

    private ContainerChainGuardMetrics() {}

    public static void recordBlockedLookup() {
        blocked.increment();
    }

    public static void recordRefusedNesting() {
        refused.increment();
    }
}
