# Survival headroom work plan

## Operator sequencing update

The operator subsequently requested implementation across the feature list before
the combined load test. The active 200-client experiment was interrupted and is
not a completed benchmark. Implementations now proceed in dependency order, with
compilation and small correctness checks during development; matched load tests
are postponed until integration. Audit DEFER entries are pending implementation
gates, not a claim that the requested feature set is finished. Experimental
features remain disabled until the final validation and rollout decision.

## Baseline and scope

Work branch: `codex/survival-headroom-26.2`. Baseline source: `28a5a5532`
(native configuration and command changes completed before this optimization batch).
Baseline runnable checksum: `AB7A89DB97B3AB2015973C8419E091707AFA79A830F018D99DF8AD46A622FAF2`.
The binary predates that commit; its manifest still identifies `0688225`.
Pinned upstream commits are in `build-data/quantum-upstream.properties`.

The operator requested all candidates in the optimization brief to be handled.
Its acceptance gates apply: existing features are not duplicated, behavior
changes are separate opt-ins, and an unproven architecture is deferred rather
than enabled to complete a checklist. Each candidate has a decision in the audit.

The 200-client live investigation is an incomplete reproducer, not a capacity
pass. With locator bar enabled the 60s mean reached 218.54 ms; with it disabled
the observed mean recovered to 42.10 ms, p95 54.19 ms and p99 69.63 ms. This
leaves insufficient headroom. See the dated report for raw-window limitations.

Local snapshots, reference source and worlds live outside Git under `.migration`,
`.references` and `.load-tests` in the parent Minecraft Server directory. The
operator's live test fixtures have been removed, locator bar restored, forced
chunks released, and the world saved/stopped. New experiments use copies only.

## First bounded batch

1. Reduce allocation and redundant table-view work in waypoint updates while
   preserving connection callbacks, visibility checks, timing and the locator bar.
   Default OFF; per-dimension opt-in; compatibility/safe mode uses upstream.
2. Audit visibility lookups and entity queries identified by JFR. Adopt only a
   local change with a behavior reproducer and a measurable benefit. Do not cache
   plugin decisions or change AI cadence as an approximation.
3. Fix the local API version identifier if isolated plugin startup reproduces
   the observed `26.2.local` parsing failure. It is a compatibility fix, not a
   capacity improvement. Minecraft and Java versions remain unchanged.

The exact retained changes depend on the results. Do not combine a neutral
micro-optimization with a successful one and attribute the whole gain to both.

## Acceptance criteria fixed before comparison

* Relevant unit/regression tests, patch reapplication and runnable build pass.
* Waypoint lifecycle: add/update/broken replacement/untrack/player removal,
  visibility changes, world changes and disable behavior preserve the contract.
* No changed spawn caps, simulation distance, locator rule, farm cadence, event
  cancellation, save interval, or chunk generation state between candidates.
* Run at least three matched copies per retained performance candidate. Alternate
  order; report each run and variation. Profiler settings are identical.
* Target at 200 active clients: mean 25–30 ms, p95 <40 ms, p99 <50 ms; no recurring
  long ticks or growing queues. This is a target, not an assumed result.
* Require lower median mean and p95 beyond observed run variation to claim a
  performance win. Idle/light-load regression must be within 1 ms mean and
  2 ms p95; otherwise investigate and explain before acceptance.
* Driver must retain the requested clients, avoid sustained schedule lag, and
  confirm block/damage/container work. Add accepted-position checks; transmitted
  movement alone does not establish responsive gameplay.
* Measure queue latency and responses as well as MSPT. Record profiler overhead.
* No plugin-capacity claim until the actual Survival plugin package is supplied.
  The existing 37-plugin Lobby is a separate compatibility fixture.

Comparisons start with Quantum before/after. A matching Purpur run is required
for a Quantum-versus-Purpur speed claim; internet estimates cannot establish one.
No such new competitive claim will be made from the live investigation.

## Dependency and risk gates for later batches

* Spatial/inventory caches need complete invalidation and event tests first.
  Reuse Moonrise's spatial/chunk ownership structures rather than add duplicates.
* Async work requires measured snapshot costs, bounded queues, cancellation and
  stale-result rejection, thread ownership, and a tested fallback. No AsyncCatcher
  bypass or automatic movement of plugin tasks to worker threads.
* Parallel world/region ticking needs a separate legacy-plugin contract and
  cross-world/region transfer design. Four physical host cores also share GC,
  networking and the load generator; more pools are not automatically useful.
* Secure Seed, alternative region formats and native worldgen require independent
  determinism/durability/platform gates and never migrate the operator's world.
* Protocol integrations and Sentry require a concrete client/product need and
  payload/permission/privacy tests. Sentry remains OFF with no outbound telemetry.

Update this file and the audit after every batch. A failed/neutral candidate is
reverted or explicitly deferred; unfinished tests must remain visible.

## Waypoint follow-up — 2026-09-18

Patch 0043 now iterates healthy opt-in connections directly and keeps the
snapshot path only for broken-connection repair. The lifecycle test and patch
reapplication pass. On the same 200-player spread fixture, locator bar enabled
still settles around 56 ms rolling mean and fails the near-20-TPS gate; the
locator-off control stays at 20 TPS with roughly 31 ms mean, p95 35 ms and p99
42 ms. The control is diagnostic only. Vanilla locator behavior remains
enabled in Survival because changing it would trade gameplay for benchmark
headroom.
