# Phase 2 nearest-player experiments

## Scope

Local shared-host diagnostic only: Windows 11, Intel i3-12100F (8 logical
processors), 31.7 GiB RAM, JDK 25, loopback server and bot driver on one host,
no plugins, pooled Netty allocator, locator enabled, waypoint collections
enabled, view distance 8, simulation distance 6, seed 728194, pregenerated
spread fixture, 200 mixed clients.

## Baseline

Commit `f38d26257`, server SHA-256
`bf60adc085668dfca55c0e6c1b712ec165f60ea78b4aebeeeb9d9c3ffec4abc7`.

Run `smoke-2026-09-18T13-09-19-359Z-3f255105`:

| Metric | Result |
|---|---:|
| Native TPS window average | 17.6 |
| Native MSPT window average | 56.775 ms |
| JFR `getNearestPlayer` | 13.21% |
| JFR `NaturalSpawner.spawnCategoryForPosition` | 3.07% |

## Attempt 1 — AABB entity-index lookup

The spawn call was changed to search a 256-block entity AABB and fall back to
the vanilla scan if empty. The fallback preserved the no-player case, but the
spatial query was too expensive for this workload.

Run `smoke-2026-09-18T14-02-20-892Z-aeb2966b`:

* TPS fell to roughly 2.2–2.4 during the measured phase.
* `ChunkEntitySlices$EntityCollectionBySection.getEntities` reached 41.88% of
  JFR samples.

Decision: **REVERT**.

## Attempt 2 — indexed `List` loop

The broad player scan was changed from an enhanced-for iterator to an indexed
loop while preserving list order and predicate behavior.

Run `smoke-2026-09-18T14-20-58-925Z-87e98be8`:

* Rolling native MSPT settled around 58.7 ms.
* Rolling native TPS reached 16.9–17.9 before the safety gate stopped the run.
* `EntityGetter.getNearestPlayer` remained the top method at 15.40% of JFR
  samples.

Decision: **REVERT**. The result is a regression/neutral result within host
noise and does not justify keeping the extra patch.

## Conclusion

The current evidence does not support adding a second AABB index or changing
the generic nearest-player loop. The next candidate must reuse Moonrise's
existing `NearbyPlayers` data and prove distance-boundary equivalence before
being benchmarked. These runs are not a 200-player capacity pass and do not
justify scaling to 300 or 450 players.
