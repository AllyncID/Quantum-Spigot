# Live spread investigation — incomplete run

Windows i3-12100F (4 cores / 8 threads), Microsoft Java 25.0.3+9, 6 GiB G1
server heap, pooled Netty, Survival view/simulation 8/6, no Survival plugins.
Lobby, proxy and the owner's client shared the host. Quantum binary SHA-256:
`AB7A89DB97B3AB2015973C8419E091707AFA79A830F018D99DF8AD46A622FAF2`.

At the owner's explicit request, 200 protocol clients joined the running
Survival backend plus one real player. Mixed actors occupied 200 separate
chunks on test pads at Y=300, spacing 32 blocks, across 612 x 292 blocks.
100 clients moved/jumped, 40 placed/dug dirt, 20 attacked tagged targets,
20 opened chests/swapped items, 20 remained idle; chat and commands overlapped.
This remains a scripted flat-floor fixture, not terrain-aware human gameplay.

The earlier compact setup was interrupted before its `.go` marker and is not
an active performance result. The spread run started actions at 00:25:26 WITA.
At 00:28:47 all 200 clients remained connected, with 400600 movement packets,
4006 jumps, 961 confirmed placements, 991 breaks, 2380 attributed damage events
and 1000 container opens. Packet counts do not prove accepted movement latency.

| Local time | Locator bar | TPS 5s / 1m | Rolling 60s mean / p95 / p99 / max MSPT | Samples |
|---|---|---|---|---|
| 00:26:19 | on | 6.62 / 6.09 | 166.83 / 499.32 / 1350.75 / 3056.46 | 341 |
| 00:26:57 | switched off | 4.54 / 4.57 | 218.54 / 518.65 / 1054.48 / 1813.20 | 277 |
| 00:28:10 | off | 19.93 / 19.99 | 42.10 / 54.19 / 69.63 / 163.40 | 1199 |

All windows cover approximately 60 seconds; do not pool their percentiles.
Loaded chunks were 2301 and sampled player chunk queues were empty. The last
window still fails the desired 25–30 ms mean / p95 <40 ms headroom target.
Disabling a gameplay/UI feature is an operational tradeoff, not a transparent
code optimization or a matched before/after performance claim.

A main-thread snapshot caught `ServerWaypointManager.updateWaypoint` from
`Entity.setPosRaw` during player movement. A short JFR recorded 3513 main-thread
execution samples, 1118 with that manager in the captured stack (31.8%). Other
costs included entity nearest-player queries. This is sampled attribution,
not exact inclusive timing. The thread dump itself paused the VM for 264 ms;
the large lag was already present before profiling. Young GC pauses observed
around that point were approximately 20 ms, with no full GC.

The process/logs ended around 00:28:51 before the scheduled five-minute run
completed; no final driver result or clean shutdown exists. Therefore this run
is **INCOMPLETE and fails capacity validation**. It is a bottleneck reproducer.
Evidence retained locally in `.load-tests/survival-20260917`: driver JSONL,
JFR, thread snapshot and the server's archived log. No private chat/secret is
published here. On recovery the test pads/targets were removed, force-loaded
test chunks released, locator bar restored to true, and the world saved and
stopped cleanly. Subsequent experiments use independent world copies.
