# Benchmarking

See `benchmarks/README.md` for the executable collection and summarization
workflow. An idle smoke test proves startup and observation; it cannot prove
real-world throughput, chunk safety, or plugin compatibility.

Before every optimization record: problem, hypothesis, upstream overlap,
thread-safety boundary, config/fallback, CPU/allocation cost, correctness tests,
exact commits, Java/heap/GC, workload, warmup, run length, and repeated results.
Measure Paper and pinned Purpur on the same fixtures alongside Quantum.

Retain raw data. Report nearest-rank p50/p95/p99 and max, allocation rate,
GC pauses, main/total CPU, queues and user-visible latency. Mark unavailable
metrics rather than imputing values. Repeat at least three runs per variant
and investigate variation before retaining an optimization patch.
