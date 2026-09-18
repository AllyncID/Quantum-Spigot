# Performance status

Matched local diagnostics show Quantum ahead of the retained Purpur run. The
final Phase 2 build passes the 200-player mixed-workload gate at 20 TPS with a
37.8 ms worst rolling MSPT window; 300 active players still exceeds this
shared i3-12100F host's survival-safe headroom. Quantum now exposes opt-in
native tuning in `quantum-performance.yml`; it is disabled by default. Controls
can reduce work by limiting chunk throughput or changing mechanics, but do not
constitute new asynchronous tracking/pathfinding. See [configuration](CONFIGURATION.md)
for per-option gameplay costs, inheritance and survival defaults.

Local tuning and active-player measurements are tracked in the
[2026-09-16 investigation](../benchmarks/results/2026-09-16-local-active.md).
That report separates configuration tradeoffs, workload/driver failures,
upstream comparison and remaining limits. A 20-TPS arena result must not be
generalized to unrestricted human play or a speed advantage over Purpur.

Recording stores tick duration and timestamp in a fixed ring (20 entries per
configured history second). A 1-ms histogram has 1002 buckets, including an
overflow bucket. Commands use exact nearest-rank quantiles from retained
samples; they also display sample count and wall-clock coverage. Tick sprinting
can shorten coverage because storage remains bounded.

Synchronous scheduled-task instrumentation makes two monotonic clock reads.
Only threshold breaches allocate incident records; history is capped at 64.
Warnings and reports are rate-limited. Entity and packet counters are constant
time. Counts use existing world counters when commands or reports request them.

The diagnostics overhead itself must be measured against the pinned upstream.
Post-tick stack captures show the reporting location, not the source of the
stall. GC values are cumulative JVM counters, not individual pause samples.
Use Spark/JFR to identify causes before proposing an optimization.
