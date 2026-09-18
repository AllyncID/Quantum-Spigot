# High-player optimization status

This report follows the Phase 2 V2 playbook: inspect existing work, use the
same workload, change one small path, and keep a change only when it is both
safe and measurably useful.

## Inventory and audit

| Priority | Status | Existing location | Evidence | Decision |
|---|---|---|---|---|
| Locator / waypoint | Partial | feature patches 0025, 0043, 0044 | `ServerWaypointManager` was 1.36% + 1.12% in the current JFR | Keep; continue only with a measured candidate lookup change |
| Entity scheduler active set | Missing | vanilla `EntityScheduler` / entity tick path | No isolated attribution in the current JFR | Defer until an isolated profile shows scheduler work is material |
| Block entity loop | Partial | feature patches 0040, 0041, 0042 | Existing sleeping and revision tracking; no loop hotspot in top methods | Do not duplicate; defer grouped-loop work |
| NaturalSpawner | Existing upstream path | `NaturalSpawner`, Moonrise `NearbyPlayers` | `getNearestPlayer` 13.21% and `spawnCategoryForPosition` 3.07% in the baseline JFR | Tried two synchronous variants; both reverted |
| Async spawn calculation | Missing | no connected Quantum path | No snapshot/validation design or isolated need yet | Defer |
| Pathfinding | Missing | vanilla `PathNavigation` / `PathFinder` | Not a top method in the baseline | Defer |
| AI sensors | Existing vanilla queries | `EntityGetter`, mob goals | Nearest-player cost is mixed across spawning and AI | Defer parallel work until a call-site split exists |
| Player movement | Partial | existing collision and movement patches | Not a top five method in this run | Defer |
| Entity tracker | Partial | existing visibility and movement patches | waypoint/tracker methods are below the nearest-player cost | Keep existing paths; defer phase 2 |
| Packet suppression | Partial | existing dirty equipment/movement and lazy flush paths | No packet construction method dominates the baseline | Defer |

## Matched baseline

The retained baseline is commit `f38d26257` with locator behavior enabled,
waypoint collections enabled, pooled Netty allocation, view distance 8,
simulation distance 6, no plugins, and the prepared spread fixture. Server and
bots share the local i3-12100F host, so this is a diagnostic shared-host result,
not a capacity claim.

The four native windows averaged **17.6 TPS** and **56.775 ms MSPT**. The JFR
hot methods were `EntityGetter.getNearestPlayer` 13.21%,
`NaturalSpawner.spawnCategoryForPosition` 3.07%,
`ServerWaypointManager.updatePlayer` 1.36%, and
`ServerWaypointManager.updateWaypoint` 1.12%.

## Decisions from this cycle

* A 256-block entity-index AABB was rejected. The query itself became 41.88%
  of JFR samples and the run fell to roughly 2.3 TPS. Run:
  `smoke-2026-09-18T14-02-20-892Z-aeb2966b`.
* An indexed `List` loop in `getNearestPlayer` was rejected. The matched run
  reached about 58.7 ms MSPT and 16.9–17.9 TPS, with the method still at
  15.40% of JFR samples. Run:
  `smoke-2026-09-18T14-20-58-925Z-87e98be8`.
* Both candidates were reverted. No gameplay rule, spawn cap, distance,
  locator behavior, or packet cadence was changed.
* Feature patch 0044's malformed hunk count was corrected so a clean patch
  application is reproducible; this is a build correctness fix, not a claimed
  performance gain.

## Recommended next order

1. Keep the existing locator patches and isolate its candidate-set cost from
   the general nearest-player cost with a call-site profile.
2. If NaturalSpawner remains dominant, use the existing Moonrise
   `NearbyPlayers` index only after proving exact distance-boundary behavior;
   do not add a second spatial index.
3. Profile entity scheduler and block-entity loop costs separately before
   writing either optimization.
4. Do not start async spawning, async pathfinding, sensor parallelism, or
   regionized ticking without a complete snapshot and stale-result design.

The final Phase 2 build passes the 200-player mixed-workload gate at 20 TPS
with a 37.8 ms worst rolling MSPT window. The matched 300-player runs are
retained in [the Phase 2 result](../benchmarks/results/2026-09-19-phase2-200-300.md):
the normal workload hits a long join/action tick and the test-only
`allow-flight` diagnostic settles near 6.7 TPS. The remaining cost is spread
across entity tracking, player lookup, map access, and vanilla spawning, so no
single safe locator-bar change explains or fixes the 300-player limit.
