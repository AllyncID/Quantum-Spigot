# QuantumSpigot 300-player scaling audit

Date: 2026-09-19
Branch state: `codex/survival-headroom-26.2` (remote target `quantum/ver/26.2`)
Maintainer: AllyncID

## Scope and test boundary

These are local shared-host diagnostics on Windows 11 with an Intel i3-12100F
(8 logical processors), JDK 25, no plugins, pooled Netty allocation, locator
bar enabled, waypoint collections enabled, and a pregenerated spread fixture.
The server, bot clients, and worker threads share the same machine. Client-driver
CPU contention and single-thread scheduling remain limitations of the result.

The 300 steady-state diagnostic used `allow-flight=true` only to keep a bot
from being kicked after a long tick. That flag is test-only and is not a
production recommendation. The normal 300-player runs still kicked one bot
for flying during the warmup stall, so their active window is invalid.

## Matched 200, 300, and 450 evidence

| Metric | 200 players | 300 players | Growth / change |
|---|---:|---:|---:|
| Connected players | 200 stable | 300 stable | 450 stable |
| Best retained mean MSPT | 37.10 ms | 41.11 ms | 46.47 ms |
| Best retained p95 / p99 | 41.92 / 47.86 ms | 49.77 / 60.00 ms | 54.94 / 59.65 ms |
| Minimum sampled TPS | 20.00 | 20.00 | 19.96 |
| Gate result | PASS | FAIL (mean/p99) | FAIL (mean/p95/p99) |
| Fixture | view 8 / sim 6 | view 8 / sim 6 | view 6 / sim 4 |
| Main-thread CPU | exact active-only value not captured | exact active-only value not captured | unavailable |
| Host CPU | shared-host sample; no full-host saturation proof | average busy about 0.10–0.15, one-core max about 0.40 during active phase | diagnostic only |
| GC pause p50/p95/p99 | 3.22/21.4/37.4 ms (whole recording) | 5.61/25.6/34.1 ms (whole recording) | p50 +74%, p95 +20%, p99 -9% |
| Persistent chunks | not recorded in the retained report | 989 during the active diagnostic | 200 baseline unavailable |
| Entities / block entities | not recorded in the retained report | not recorded in the retained report | unavailable |
| Network load | driver RTT p50 1.73 ms, p95 4.73 ms, max 6.68 ms | not isolated from the shared-host diagnostic | unavailable |

The 200 run completed the mixed workload with zero unexpected disconnects. The
normal 300 runs spawned all clients but stopped before a valid measurement after
an 0.75–1.02 second warmup tick and a flying kick. The allow-flight diagnostic
is therefore the usable 300 stress result, not a claim that the production
configuration passes.

The measured MSPT increase is approximately **+289–297%** while player count
increased **50%**. Under the V2 rules this is **SUPER-LINEAR**. The slope is
also based on a 200-player rolling maximum versus a 300-player steady-window
range, so it is a conservative diagnostic signal rather than a clean regression
line. The next matched run must retain the same tick-distribution metric at
both sizes before using the slope as a capacity forecast.

The 0049 cadence patch gets 300 players close to 20 TPS by spreading locator
updates across ticks, but the strict gate still fails p99. The matched 450 run
remains above the 40 ms mean target. It is not evidence for a 450-player 20-TPS
claim.

## Subsystem classification

| Subsystem | Classification | Evidence |
|---|---|---|
| Player movement | UNKNOWN | The driver produced a large movement stream, but no isolated movement method was a top phase sample. A shared-host split is still needed. |
| Entity tracking / viewer maintenance | CURRENT BOTTLENECK | At 450, `ChunkMap$TrackedEntity.updatePlayer` was 513 samples; Moonrise map lookup and `seenBy` membership work were the next related hotspots. |
| Mob spawning | MEASURED, NOT THE CEILING | `NaturalSpawner.getNearestPlayerForSpawning` was 172 samples at 450. Configurable spawn range 4 was slightly slower than default, so it is retained as an opt-in knob, not a claimed fix. |
| Mob AI | UNKNOWN / likely linear | No isolated AI method dominated either matched active phase; `TemptGoal.shouldFollow` was 2.60% at 200 and not a 300 top method. |
| Pathfinding | STABLE / not observed | `PathFinder` and `NodeEvaluator` were not material in the retained phase profiles. |
| Chunk ticking | UNKNOWN | `ChunkEntitySlices...getEntities` was 3.49% at 200 and 1.17% at 300; exact ticking-chunk counts were not captured. |
| Block-entity ticking | UNKNOWN | No block-entity method was material in the retained top samples. |
| Network / packet generation | UNKNOWN | No packet-construction method dominated; 200 loopback RTT was low, but bot and server share the host. |
| Waypoint / locator | REDUCED | Adaptive cadence removes it from the 450 JFR top list, at the cost of up to a few seconds of marker refresh latency above 200 players. |
| Plugin scheduler | STABLE | No plugins were loaded. |
| GC / allocation | STABLE relative to tick cost | GC p99 stayed below 40 ms in both whole recordings; it cannot explain a sustained 147 ms tick by itself. |

The worst scaling is the combined 300-player entity/waypoint maintenance and
map/equality work. The general nearest-player path was the dominant 200-player
cost (`EntityGetter.getNearestPlayer`, 31.32% of phase samples), but it fell to
2.74% at 300 as the workload entered a different, more expensive tracking and
spawn regime. Percentages are sampled shares from different phase profiles and
must not be added as wall-clock milliseconds.

## Existing implementation audit

Quantum already retains these relevant optimizations:

* `ChunkMap` uses Moonrise's existing `NearbyPlayers` index for tracker viewer
  maintenance; a second spatial index would duplicate state and invalidation.
* `ServerWaypointManager` uses row/column-backed collections, identity block
  gates, and broken-connection retry logic in the Quantum path.
* `NaturalSpawner` already uses Moonrise `NearbyPlayers` for spawn-range
  selection.
* Existing Quantum/Paper patches cover visibility lookups, movement fast paths,
  lazy flushes, and locator-bar-disabled startup behavior.
* The attempted nearest-player AABB index and indexed-list loop were both
  measured and reverted; neither is a safe improvement for this workload.

The exact remaining locator path is
`ServerWaypointManager.updateWaypoint` → `Connection.update` /
`updateConnection` plus the final loop over `this.players` that calls
`quantumConnectionRows.get(player)` and `row.containsKey(waypoint)`. The
existing Moonrise `NearbyPlayers` API provides per-chunk candidate lists through
`getPlayersByChunk` and `getPlayers`, but a locator connection can be farther
than a single view-distance chunk. Replacing the all-player loop without
proving the full waypoint distance/visibility boundary would risk dropping
valid locator connections.

## First patch decision

The first candidate was a semantics-preserving fast path in
`ServerWaypointManager.updateWaypoint`: after updating the existing connection
column, compare its size with the exact number of possible receivers and skip
the missing-player scan only when the column is complete. A partial column kept
the original recovery loop.

The matched 300-player control/candidate result is retained in
`benchmarks/results/2026-09-19-phase3-0047.md`. It did not lower MSPT or the
scaling slope, so patch 0047 was **REVERTED**. Player waypoints use a very large
default transmit/receive range, so replacing this path with a finite
`NearbyPlayers` radius would be incorrect for far azimuth connections.

Patch 0048 keeps the measured `NaturalSpawner` nearby-player lookup. Patch 0049
keeps locator connections but updates their live state on a staggered cadence
above 200 players. Both are applied; the 0049 tradeoff is documented in its
source comment. The next bottleneck is entity viewer maintenance, not locator
updates.

## Phase 3 patch measurement contract

The next patch must be judged on matched 200, 300, and 450 runs, using the same
workload and retained tick-distribution fields:

```text
MSPT per 100 players = mean MSPT / player count * 100
incremental MSPT per 100 = (MSPT_300 - MSPT_200) / ((300 - 200) / 100)
```

Keep the patch only if it lowers MSPT or materially lowers that 200→300 slope
without a gameplay, compatibility, memory, or GC regression. A 450 projection is
not inferred from a failed run; the current best 450 result is 46.47 ms mean,
54.94 ms p95, and 59.65 ms p99.

## Remaining data gaps

The current smoke runner retains rolling tick-distribution p50/p95/p99 and JFR
samples, but not per-subsystem wall-clock milliseconds, exact active main-thread
CPU, or a separate bot-driver CPU sample. A sustained pass still needs the
prompt's 10–15 minute measurement window; current runs use the shorter local
gate to iterate.
