package dev.quantumspigot.server.threading;

import dev.quantumspigot.server.config.QuantumConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A Quantum-only ceiling, not a replacement for Paper's worker configuration. */
public record WorkerBudget(int processors, int total, int diagnostics, int unallocated) {
    public static WorkerBudget plan(int processors, QuantumConfig.Workers settings, boolean needsDiagnostics) {
        if (processors < 1) throw new IllegalArgumentException("processors must be positive");
        int available = Math.max(1, processors - settings.mainReserve() - settings.jvmReserve());
        int total = settings.maximumTotal() == -1 ? available : Math.min(available, settings.maximumTotal());
        if (total < 1) throw new IllegalArgumentException("worker maximum must be -1 or positive");
        int diagnostics = needsDiagnostics ? 1 : 0;
        return new WorkerBudget(processors, total, diagnostics, total - diagnostics);
    }

    /** Deterministic largest-remainder allocation, for future explicitly enabled compute domains. */
    public Map<String, Integer> allocate(Map<String, Integer> weights) {
        long sum = 0;
        for (int weight : weights.values()) {
            if (weight < 1) throw new IllegalArgumentException("weights must be positive");
            sum += weight;
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Long> remainders = new LinkedHashMap<>();
        int assigned = 0;
        for (String name : weights.keySet().stream().sorted().toList()) {
            long weighted = (long) this.unallocated * weights.get(name);
            int share = (int) (weighted / sum);
            result.put(name, share);
            remainders.put(name, weighted % sum);
            assigned += share;
        }
        for (String name : remainders.keySet().stream()
            .sorted((a, b) -> Long.compare(remainders.get(b), remainders.get(a))).toList()) {
            if (assigned == this.unallocated) break;
            result.put(name, result.get(name) + 1);
            assigned++;
        }
        return Collections.unmodifiableMap(result);
    }
}
