# Commands and permissions

`/quantum` defaults to health. Aliases: `/qspigot`, `/qs`; Bukkit's command map
preserves existing registrations and supplies `/quantumspigot:quantum` for
namespaced access. Normal `/tps` is left under upstream control.

Each subcommand requires `quantum.command.<subcommand>`, default OP.
`quantum.admin` contains all Quantum command permissions.

Commands use an aqua `[Quantum]` header, gray labels, white values and green /
yellow / red TPS/MSPT values. TPS thresholds: >=19.9 green, >=18 yellow, otherwise
red; MSPT: <40 green, <50 yellow, otherwise red. Set `commands.colored-output:
false` in `quantum-global.yml` for plain text. Player MSPT output is split into
short rows; console TPS/MSPT/raw heap/GC lines preserve the benchmark parser
format with a `[Quantum]` prefix. Heap summaries use MiB. No user/plugin text
is interpreted as formatting markup.

| Subcommand | Output |
| --- | --- |
| help | Permission-filtered commands with click-to-fill suggestions |
| version | Build, pinned upstream, Java, profile, safe mode |
| tps | Upstream 5-second and 1/5/15-minute TPS and Quantum 60-second MSPT |
| mspt | Mean, p50/p75/p95/p99, max, last, sample count and observed coverage for 1/5/15 minute windows |
| mspt histogram | Grouped counts from the retained 1 ms buckets; explicit retention and sample-cap limits |
| health | Tick-based health classification, counts, incidents and worker state |
| threads | Main thread, Quantum worker plan and diagnostics queue/rejections/errors |
| threads dump | Explicit snapshot, limited to 64 threads and 12 frames each |
| chunks | Loaded chunks, submitted chunk-packet count, and per-player load/generation/send/ticking queue entries from the previous tick |
| entities | Loaded entities and active/inactive entity tick invocations from the previous tick |
| plugins | Last 10 slow task incidents from a 64-entry buffer; not a compatibility verdict |
| profile | Effective profile, safe mode and whether performance overrides are active |
| config | Effective chunk/Spark and per-world hopper/redstone/armor-stand/anti-xray/block/save settings |
| gc | Heap and cumulative JVM GC counters; never forces collection |

Chunk queues sum per-player entries, so a chunk requested by multiple players
can appear more than once. Global IO/save queues and entity category timings
are not measured. Submitted chunk packets do not imply client acknowledgement.

Changing profiles, tuning or executors requires restart. Adaptive control and
full profiler/report commands are not implemented. Use JFR or an explicitly
enabled profiler for sampled stall causes.
