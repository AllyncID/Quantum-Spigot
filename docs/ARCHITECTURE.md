# QuantumSpigot foundation

Scope: bootstrap, instrumentation and explicit native performance controls.
Performance overrides are opt-in and disabled by default. The supplied master
specification is a roadmap, not a statement of implemented features.

## Repository and branches

The base is Purpur `ver/26.2`, commit
`3f5d9c0e80b56812c1694e014d44fabf540a4cad`, pinning Paper
`e5fe71723e2ffde7cc9fafc085ac3bb73e63175e`.
The local development branch is `quantum/ver/26.2`; the `purpur` remote is
read-only upstream. No hosting repository or release destination is assumed.
Use topic branches for individual changes and update upstream separately.

Keep `purpur-api`, `purpur-server`, and `purpur-checkstyle` module paths to
preserve the current Paperweight build. New internal sources live in
`purpur-server/src/main/java/dev/quantumspigot/server`. Existing upstream
sources are changed through Paperweight patches; generated source trees
are never the source of truth. Do not relocate Bukkit/Paper/Purpur packages.

## First milestone gates

1. Apply all upstream patches and compile with Java 25.
2. Add Quantum artifact identity and retain upstream notices.
3. Add strict, versioned, commented configuration and safe mode.
4. Record tick durations, bounded history, and lag incidents.
5. Expose permission-gated diagnostics and scheduler stalls.
6. Describe a globally bounded worker allocation; create only workers needed
   for diagnostics in this phase.
7. Compile, run unit tests, reapply patches, and smoke-test a fresh world.
8. Record an honest baseline with workload and hardware details. Stop before
   Phase 2 until comparative workloads support a specific optimization.

## Configuration

Load YAML once at startup into immutable Java records. Validate primitive
types, finite numbers, enums, versions, and ranges before publishing any
configuration. Malformed input aborts startup with the file and key named;
it must never be overwritten. Add missing defaults only after all files
validate, preserving existing values, unknown keys, and comments. Back up
existing files before rewriting them and replace through a same-directory
temporary file. Future schema versions are rejected rather than downgraded.

Configuration exposes diagnostics, profile, worker budget, command colors and
implemented performance overrides. Global chunk/Spark values are applied at
Quantum initialization. World values are applied in the Level constructor after
native configuration loads and before Anti-Xray/entity state captures it. Armor
stand gravity is a cached world flag read by the native gravity method; entity
NBT is preserved. There is no YAML access in the tick path. Compatibility and
safe mode bypass tuning and leave diagnostics available. No executor hot reload.

## Measurement boundaries

The authoritative server thread records upstream `TickTime.tickLength()` plus
`intermediateTaskExecutionTime()`, matching Paper's work-duration boundary and
excluding idle waits, into a bounded ring of at most history-seconds * 20 ticks.
Accelerated tick rates may therefore provide shorter wall-clock coverage.
Quantiles are calculated on demand from actual samples, outside
the normal tick path. Reports identify window coverage and sample counts.
TPS is obtained from the existing upstream rolling averages.

Synchronous scheduled tasks use two monotonic-clock reads and a `finally`
hook. Only slow tasks enter a bounded incident history. Detection never
changes execution order, catches plugin failures, or cancels plugin work.
Rate-limited warnings name the task owner supplied by the scheduler.

Chunk/entity measurements use existing counters and constant-time hooks.
Entity counts distinguish active and inactive invocation paths. Player chunk
queues are sampled inside the existing loader loop; sums include duplicate
chunks requested by different players and are not global IO/save queue depths.
Unsupported queue measurements are explicitly unavailable, never represented as zero.
No per-tick full scan or Bukkit call is made from a worker.

Lag reports contain immutable measurements, configuration values from a fixed
allowlist, and bounded task/stack information. No player identities, addresses,
chat, world names, plugin configuration, or authentication values are collected.
A bounded diagnostics executor writes reports. Saturation drops diagnostic
reports with a counter and warning, never blocking the tick for disk I/O.
Shutdown drains diagnostics for a finite timeout; world saving remains upstream.

## Global worker budget

Budget = max(1, logical processors - main reserve - JVM reserve), limited by
an explicit maximum. This is a planning ceiling for Quantum workers only;
Paper's existing chunk/network/IO workers remain under upstream control; their
queue/latency measurements are not implemented. The optional thread dump can
inspect JVM thread names and stacks. This budget does not cap every JVM thread.

Diagnostic IO consumes one slot. Future pools must receive allocations from
the remaining budget using a deterministic weighted allocation. A zero
allocation means disabled/deferred, never an implicit extra thread. Every
pool must have a bounded queue, visible rejection counts, finite shutdown,
and no caller-runs policy that could perform disk work on the main thread.
No simulation offload is installed in this phase.

## Benchmark design

Use identical Java, heap, CPU affinity, world copies, seeds, plugins, player
scripts, warmup, and observation durations for Paper, Purpur, and Quantum.
Separate idle startup smoke checks from performance claims. Record raw tick
samples/JFR, p50/p95/p99/max, allocation/GC data, and correctness observations.
Repeat each scenario; reject gains within run-to-run noise. Record failures
and missing measurements rather than filling them with estimates.
