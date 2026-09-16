# Benchmark harness

The no-added-plugin local bot harness and dated results are in
[quantum-testbench](../quantum-testbench/README.md) and the
[active-load investigation](results/2026-09-16-local-active.md).
No sample numbers in the master specification are measurements from this
project. The optional plugin-based workflow below is a separate collector.

1. Build the optional test plugin: place `include(":test-plugin")` in
   `test-plugin.settings.gradle.kts`, then run `./gradlew :test-plugin:build`.
2. Prepare identical separate Paper/Purpur/Quantum server directories with
   the same world fixture, plugins, Java, heap and configuration. Accept the
   Minecraft EULA as the operator. Do not run variants concurrently.
3. Install the same test-plugin jar in each fixture. Disable empty-server
   pausing for idle measurements. Run `/quantumtest baseline` after world
   startup. It warms up for 600 ticks, then records 1200 tick-end events.
4. Copy the resulting CSV and its metadata from the plugin data folder into
   a uniquely named directory under `benchmarks/results`.
5. Run `node benchmarks/scripts/summarize.mjs path/to/ticks.csv`.
6. Repeat three times per variant and scenario. Keep raw data and JFR/Spark
   evidence alongside a report using `results/TEMPLATE.md`.

The test plugin measures `ServerTickEndEvent#getTickDuration` identically on
each fork. That event excludes later event handlers and post-tick work, so it
must not be silently equated with Quantum's complete upstream tick accounting.
The collector stores a bounded array and performs file IO asynchronously after
capture. It does not emulate players or create synthetic workloads.

Use `scenarios/matrix.json` as the workload checklist. Bot drivers, representative
plugins, world fixtures and latency probes are operator-supplied prerequisites.
An idle run alone is a smoke baseline, not proof of an optimization.
