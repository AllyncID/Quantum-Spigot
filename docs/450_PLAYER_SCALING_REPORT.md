# QuantumSpigot 26.2 high-player scaling report

Status: **diagnostic phase; 10–15 minute 450-player gate not yet certified**  
Host: Intel Core i3-12100F, 8 logical CPUs, Windows 11, JDK 25, no plugins

## Results

| Fixture | Players | Result | Mean MSPT | p95 / p99 | Minimum TPS |
|---|---:|---|---:|---:|---:|
| Prepared arena, view 8 / sim 6 | 200 | gate pass in retained 0048 run | 37.10 | 41.92 / 47.86 | 20.00 |
| Prepared arena, view 6 / sim 4 | 300 | gate fail; active mixed run | 146.9–149.9 | n/a | 6.7–6.8 |
| Live Survival ramp | 400 | stalled ramp sample | 55.18 (60 s) | 158.45 / 208.04 | 5.38 (5 s) |
| Prepared arena, view 6 / sim 4 | 450 | workload complete, strict gate fail | 46.53 | 57.19 / 67.35 | 19.99 |

The prepared 450 run spawned all 450 clients, completed the mixed actions, and had zero unexpected disconnects. It exceeded the strict 40 ms mean and 50 ms percentile limits. The live Survival run is a separate admission-and-cleanup fixture and stalled during the 400→450 ramp, so it is not pooled with the prepared arena result.

## Root cause evidence

The 450 JFR server-thread samples are led by Moonrise entity map/collection lookups, `ChunkMap.TrackedEntity.updatePlayer`, `LongOpenHashSet.contains`, `NaturalSpawner.getNearestPlayerForSpawning`, and AI/player lookup work. Locator updates are no longer a leading method after patch 0049. Heap peaked at 2.68 GiB and recorded GC pauses were short relative to the stall windows. See [the collapse audit](400_PLAYER_COLLAPSE_AUDIT.md) for the A–J inventory and missing telemetry.

## Changes and decision

Retained patches 0048, 0049, 0052, and 0053 remain in the source. The 0050 entity-membership cadence experiment was removed after a 450 percentile regression. The 200-player visibility A/B regressed when `visibility-lookup=true`, so it remains configurable and OFF. The current follow-up patch adds only an empty visibility-map short-circuit; it does not alter gameplay, entity range, spawn rules, or threading. The rebuilt jar `FinalJar/QuantumSpigot-26.2-1.0.jar` has SHA-256 `8C1CD0522131DA62B6D2D59F5E9683C200C76BF186739D30A1D171E6DBFE79E7`; the 200-player regression check completed all actions with zero disconnects and measured 19.9 minimum TPS / 50.1 ms worst mean MSPT.

The normal full build path still needs cleanup: `applyAllPatches` fails before Java compilation while applying the stale `purpur-server/build.gradle.kts.patch`, and the complete server test suite has one existing `WaypointCollectionsTest` NPE. For this iteration the generated upstream build file was repaired from the cached Paper checkout, Java compilation and `createPaperclipJar` succeeded with tests excluded, and the new jar was smoke-tested. The full 200/300/450 matrix remains pending.

## Required next gate

Repair the generated build-file checkout, rebuild the jar, then run a fresh prepared 200/300/350/375/400/425/450 curve and a sustained 450 workload with queue, host, GC, loaded-chunk, and entity telemetry. Do not claim 450-player survival capacity from the current data.
