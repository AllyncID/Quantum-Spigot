package dev.quantumspigot.server.threading;

import dev.quantumspigot.server.config.QuantumConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkerBudgetTest {
    @Test
    void allocationNeverExceedsBudgetEvenOnSmallMachines() {
        Map<String, Integer> weights = Map.of("chunk", 20, "path", 25, "tracking", 20, "network", 20, "io", 15);
        for (int processors = 1; processors <= 256; processors++) {
            var budget = WorkerBudget.plan(processors, new QuantumConfig.Workers(2, 1, -1), true);
            var allocation = budget.allocate(weights);
            assertEquals(budget.total(), 1 + allocation.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(budget.total() <= Math.max(1, processors - 3));
            assertTrue(allocation.values().stream().allMatch(value -> value >= 0));
        }
    }

    @Test
    void explicitMaximumAndDisabledDiagnosticsAreHonored() {
        var budget = WorkerBudget.plan(16, new QuantumConfig.Workers(2, 1, 4), false);
        assertEquals(4, budget.total());
        assertEquals(0, budget.diagnostics());
        assertEquals(Map.of(), budget.allocate(Map.of()));
        assertEquals(Map.of("a", 2, "b", 2), budget.allocate(Map.of("b", 1, "a", 1)));
    }
}
