# 400–450 player collapse audit

Date: 2026-09-19  
Host: Windows 11, Intel Core i3-12100F, 8 logical CPUs, 31.7 GiB RAM, JDK 25  
Fixture: local loopback, server and bots on the same host, no plugins, pooled Netty allocator, prepared arena, mixed survival workload  
Sources: `benchmarks/results/2026-09-19-phase2-200-300.md`, `benchmarks/results/2026-09-19-phase3-0048-0049.md`, `benchmarks/results/2026-09-19-live-survival-root-config-450.md`, and run `smoke-2026-09-19T05-42-12-960Z-0dd0ce46`.

## Measured curve

The fixture does not contain valid runs at every requested point. Missing values stay `n/a`; they are not interpolated.

| Players | TPS evidence | Mean MSPT | p95 / p99 MSPT | Main-thread evidence | Host/fixture evidence |
|---:|---|---:|---|---|---|
| 200 | 20.00 minimum | 37.10–37.80 | 41.92 / 47.86 (0048 run) | no retained 200 JFR profile in the current audit set | view 8 / sim 6; all bots and actions passed |
| 300 | 6.2–6.8 TPS in the active mixed fixture | 146.9–160 | n/a for the stopped windows | NaturalSpawner, HashMap/LongOpenHashSet, entity tracking, player lookup | one flight kick in the normal fixture; `allow-flight` was test-only |
| 350 | n/a | n/a | n/a | no completed stage | no retained run |
| 400 | 5.38 five-second TPS during live Survival ramp | 55.18 (60 s) | 158.45 / 208.04 | live `/quantum tps` snapshot; ramp was not a valid gate window | 400 online, 500 max slots; same-host bot contention |
| 450 | 19.99 one-minute minimum in the completed prepared-arena run; the separate live Survival run stalled during cleanup | 46.53 | 57.19 / 67.35 | `getNode`, entity collection, `TrackedEntity.updatePlayer`, `LongOpenHashSet.contains`, `NaturalSpawner.getNearestPlayerForSpawning` | 1,693 loaded chunks, 1,749 entities, 2.68 GiB heap; 450 spawned, 0 unexpected disconnects |

The prepared-arena 450 run stayed near 20 TPS but failed the strict 40 ms mean / 50 ms percentile gate. The live Survival run is the more realistic capacity signal and collapsed during the 400→450 ramp. This is a fixture difference, not contradictory evidence.

## A–J inventory

### A. Host and bot contention

The server and bot driver share the four-core/eight-thread host over loopback. The completed 450 JFR reports a maximum sampled host busy fraction of only `0.201`, but that metric is sampled at coarse intervals and does not prove that the main thread had spare core time during stalls. Bot transport p95 was 26.3 ms in the prepared run and 982.7 ms in the live run. A remote-driver run is still required to separate CPU contention from server work.

### B. Executors and queues

Quantum-specific work currently has one bounded `WorkerBudget` and `AsyncChunkSerializer`; the serializer uses a fixed pool, an `ArrayBlockingQueue`, a semaphore, and `AbortPolicy`. It does not use `CallerRunsPolicy`. The budget on this eight-logical-CPU host is four unallocated Quantum workers after the diagnostic reserve. Paper/Moonrise executors remain upstream-owned, so this is not a complete global worker budget.

### C. Main-thread result application

`AsyncChunkSerializer` snapshots on the owner thread and applies completion through its existing send path. No separate Quantum apply queue or per-tick apply budget was found. The audit therefore treats completion storms and upstream chunk/entity work as unmeasured risks rather than claiming they are solved.

### D. Entity tracking

Moonrise already supplies `NearbyPlayers` spatial indexes. `ChunkMap.TrackedEntity.moonrise$tick` loops the nearby player array and calls `updatePlayer`; `updatePlayer` performs range checks, view-distance checks, chunk tracking checks, Bukkit visibility, `seenBy` membership, and add/remove pairing. The 450 JFR puts this path and its supporting map/entity collection in the top server-thread methods. Adding a second spatial index would duplicate existing work.

### E. Activation, collision, spawning, AI

`NaturalSpawner.getNearestPlayerForSpawning` and `TemptGoal.shouldFollow` appear in the top 450 samples. The existing 0048 patch reuses Moonrise's spawn-range index and keeps the upstream fallback. The 0049 locator cadence patch removed locator work from the retained hotspot list. No collision, mob-cap, spawn-rule, or AI behavior nerf is retained.

### F. Movement, scheduler, block entities, random ticks

The mixed driver sends about 808,000 movement packets and performs block, inventory, container, chat, and command actions. The current JFR summary does not expose a dominant Quantum movement or scheduler method. Block-entity and random-tick load is therefore not isolated by this audit and must be measured in a dedicated control before changing it.

### G. Network and allocation

The 450 prepared run used pooled Netty buffers and stayed transport-stable. The live run's high round-trip latency tracks the server stall. Heap peaked at 2.68 GiB and the recorded G1 pauses were short young collections; the JFR contains repeated old-region evacuation cycles but no evidence that GC is the primary 6–7 ms steady-state gap.

### H. Configuration and fixture

The official-style fixture is pre-generated and uses view distance 6 / simulation distance 4 for the latest 450 result. The live Survival result includes normal server state and a connection ramp; it is not interchangeable with the isolated arena. The root config's login admission interval was temporarily set to `0` for the live test and restored to `5` seconds afterward.

### I. Existing patch and upstream reference audit

Retained Quantum patches are 0048 (spawn nearest-player lookup), 0049 (adaptive locator cadence), 0052 (login admission), 0053 (offline profile lookup), and startup branding. Patch 0050 was measured and removed because it regressed the 450 percentile result. The source already contains Moonrise's spatial entity infrastructure; Leaf/Pufferfish-style region threading is not present and is outside this phase. Upstream PRs named by the phase document still need a version-matched manual comparison before any port is attempted.

### J. Collapse point and next experiment

The first bend is between the validated 200-player run (about 37 ms) and the 300-player active mixed fixture (about 147–160 ms). The 400-player live ramp confirms that the same work becomes a stall during real Survival admission. At 450 in the prepared arena, the remaining steady-state cost is concentrated in entity viewer maintenance, Moonrise entity collection/map lookups, and natural-spawn nearest-player checks; locator cadence is no longer the leading cost.

**First experiment:** hold the source unchanged and repeat the prepared 450 workload with the bot driver process pinned away from the server process where Windows allows it, recording server JFR, host CPU, bot CPU, queue counters, and the same rolling MSPT gate. This isolates P0 host contention before a risky entity-tracking patch is written.

The affinity attempt was invalid as a capacity run: pinning the server to four logical CPUs produced only 7/450 ready bots and 295 driver timeouts. It was retained as a diagnostic artifact, not as a performance result. A reversible 200-player A/B on the prepared world then measured the existing visibility lookup option: `true` reached 19.8 minimum TPS and 50.2 ms worst mean MSPT; `false` reached 20.0 minimum TPS and 48.3 ms. JFR also showed the object visibility map as a new 5.6–5.9% sampled method when enabled. The option remains OFF.

The first retained source change after that A/B is the safe empty-map short-circuit in `paper-patches/features/0007-Quantum-visibility-lookups.patch`. It avoids `containsKey` when a player's inverted visibility map is empty without changing self-visibility, plugin inversion, or the configurable object-map path.

## Decision

Do not add another async pool, region threading, or gameplay nerf from this audit. The next code change is justified only after the host-contention control and a direct measurement of the entity viewer loop. The current evidence supports a real 200-player gate, a prepared-arena 450 run below 40 ms mean, and no claim of 450-player 20-TPS survival capacity on this host.
