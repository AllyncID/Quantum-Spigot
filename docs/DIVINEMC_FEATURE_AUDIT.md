# DivineMC and Quantum optimization audit

Audit date: 2026-09-17. This is an implementation inventory and port decision,
not a claim that deferred features were runtime-tested.

Sources: DivineMC `ver/26.2` at `039981b0bf973ea9bee0f2521b2c01d9bb419802`
(verified through GitHub's branch API, matching the brief); its Purpur parent
is `ab0b6b65a198238af250de99c02ae93f89029327`, older than Quantum's
`3f5d9c0e80b56812c1694e014d44fabf540a4cad`. Quantum's Paper pin is
`e5fe71723e2ffde7cc9fafc085ac3bb73e63175e`. Upgrading upstream is separate work.

Reference source was downloaded by full commit to a separate `.references`
directory after a Git transport stall. Quantum's remotes were not changed.
`M####` below resolves to DivineMC `divinemc-server/minecraft-patches/features/`
and `P####` to `paper-patches/features/`; full names, touched files, patch authors
and embedded provenance are retained in `DIVINEMC_PATCH_INVENTORY.json`.
Support classes are under that server's `src/main/java`. Source paths in the
Quantum column refer to its generated Minecraft/Paper trees unless prefixed
`dev.quantumspigot`. Generated trees are inspected but not published.

Every row includes the target workload, source/origin, dependencies, current
state/evidence, decision, risk and validation gate. ABSENT means the named
Divine extension is absent, not that Paper lacks all optimization in that area.
UNVERIFIED does not count as implemented. Source origin claims are the embedded
patch notices; missing original commits/authors require resolution before porting.

| Candidate / workload | Source and origin | Dependencies | Quantum state and evidence | Decision / risk / validation |
|---|---|---|---|---|
| Player/entity spatial tracking; dense and spread | Quantum + Paper/Moonrise `ChunkMap`, `NearbyPlayers`, `ServerEntity` | Existing chunk/tracking maps, visibility API | PARTIAL: native spatial tracker and dirty entity data active; no Quantum incremental extension | KEEP existing; EXTEND only measured gaps. Test visibility, teleport, passengers, packet order and accepted movement; do not add a second index blindly. |
| Locator/waypoint updates; 200 movers | Native `ServerWaypointManager`, `Entity.setPosRaw`; no Divine waypoint patch identified | Guava connection table, `WaypointTransmitter`, Bukkit visibility | PARTIAL: native feature active; repeated view/snapshot work measured in JFR | REIMPLEMENT limited local lookup/allocation work, OFF during evaluation. Preserve dynamic visibility and callback/lifecycle order; matched dense/spread tests. |
| Visibility lookup; dense/spread | P0010, SparklyPaper via NONPLAYT | CraftPlayer visibility map; M0041 also uses specialized call | ABSENT: `CraftPlayer.canSee` still uses equality plus HashMap lookup | DEFER import until provenance and visibility regression gate; empty-map/identity fast paths are candidates. Never cache vanish decisions. |
| Idle hopper failure cache / inventory revisions | Quantum candidate; M0064 Lithium sleeping support overlaps | Inventory topology, plugin events, comparator wakeups | ABSENT: `HopperBlockEntity` native transfer/cooldown; Quantum exposes settings only | DEFER until idle/active farm profile and invalidation tests. Cancellation can depend on external plugin state. |
| Sleeping block entities; farms | M0064, Lithium, `block.entity`/inventory support | Rebindable tickers, chest/hopper/furnace/campfire/brewing changes | ABSENT: no Lithium sleep/wake runtime | DEFER broad port. Test every wake trigger, event, double chest/minecart, comparator, unload, save/restart and item counts. |
| Entity query/collision broad phase; mobs | Quantum candidate; M0015, M0067 | Moonrise entity lookup, exact filters/order | PARTIAL: native Moonrise lookup/collision active; `EntityGetter.getNearestPlayer` still scans relevant player collection | KEEP native index; investigate local query gap. Preserve tie-breaking, RNG, spectators and plugin filters; differential query tests. |
| POI and navigation request caches; villagers | M0015 and Quantum candidate | Brain memory invalidation, POI manager, navigation lifecycle | PARTIAL: native POI manager/cache exists; no Quantum request coalescer | DEFER added cache until villager/POI workload proves need; test reservation, job/bed removal, death and unload. |
| Chunk dedup/priorities/cancellation/fairness; exploration | Paper/Moonrise; Divine P0019 and M0062 | Chunk holders, tickets, player loader, prioritized executors | ALREADY_UPSTREAM for core shared chunk pipeline; Quantum runtime exposes native limits | KEEP. P0019 changes worker allocation, not a new dedup guarantee. Test request cancellation and ready latency before extending. |
| Save snapshots/backlog/memory; autosave | Paper/Moonrise save pipeline; Quantum native autosave budget | Owner-thread world state, region IO, ordering | ALREADY_UPSTREAM for async chunk IO; PARTIAL for custom backlog attribution | KEEP durability and native scheduling. DEFER new snapshot/cache framework until measured; test save ordering and restart. |
| Plugin attribution/background API | Quantum `QuantumRuntime`, `DiagnosticExecutor`; JFR | Bounded counters/reports, existing Bukkit scheduler | PARTIAL: slow sync tasks and whole-tick metrics implemented; event/command attribution absent | KEEP existing; DEFER wrappers until overhead/coverage design. Never move legacy plugin work async automatically. |
| Adaptive background budgets | Quantum candidate | Queue age, cancellation, worker accounting | ABSENT; worker budget is planning/diagnostics only | DEFER until background workload exists. No automatic reductions to mob caps, simulation or random ticks. |
| Parallel World Ticking; many worlds | M0046, P0015, SparklyPaper; async support | Per-world threads, global-state changes, scheduler/transfer contracts | ABSENT: authoritative simulation on main thread | DEFER architectural milestone; unrelated to one hot Survival world. Need plugin matrix, transfers, deadlock/save tests. |
| Regionized Chunk Ticking; spread players | M0049, P0020, `async/rct/RegionizedChunkTicking` | PWT/thread checks, region ownership, collection changes | ABSENT; no region gameplay scheduler | DEFER until ownership design/gates. Not equivalent to full Folia and not legacy-plugin transparent. |
| Parallel sensor phase; AI-heavy | M0072, `async/sensing/ParallelSensorTicker` | Allowlisted sensors, barrier, worker configuration | ABSENT: Brain/sensors execute on tick thread | DEFER until snapshot/shared writes and barrier costs audited on mob workload. |
| Async pathfinding; AI-heavy | M0040 Petal, `async/pathfinding` | Node evaluator, pending paths, bounded executor/rejection policy | ABSENT: native `PathFinder` synchronous gameplay | DEFER: target/stale path/cancellation and plugin hooks need behavior tests. Config defaults in Divine are not safe-default evidence. |
| Parallel entity tracker; dense | M0041, Sparkly/Petal support plus P0010 | Visibility calls, connection state, tracking arrays, workers | ABSENT: native tracker | DEFER pending visibility/event ownership and spawn/update/destroy order tests. |
| Async mob spawning; dispersed farms | M0042, P0013, Pufferfish | Async executor, caps, natural spawn accounting | ABSENT: native `NaturalSpawner` path | DEFER until RNG, caps, cancellation, final ownership and overspawn tests pass. |
| Async joining | Removed `patches/removed/1.21.10/server/0058-Async-Join-Thread.patch`; native login authentication threads | Authentication/IO plus owner-thread registration | REMOVED_OR_OBSOLETE for named Divine patch; native auth background work ALREADY_UPSTREAM | KEEP native; do not resurrect removed patch or bypass auth. Test duplicate login/timeouts separately. |
| Async chunk sending | M0035; Divine async support | Packet buffers, anti-xray, light/order, plugin network hooks | ABSENT for extra Divine offload; Netty transport already async | DEFER until ownership/release/anti-xray correctness and latency tests. |
| Portal preload / async locate | M0010, portal prefetch config and source patches | Generator/chunk tickets, executor, callback lifecycle | PARTIAL: native async chunk preparation exists; Divine locate/prefetch extension absent | DEFER extra pipeline until teleport wait profile; test cancellation, ticket leak and plugin destination decisions. |
| Raytrace Entity Culling | M0073, LogisticsCraft occlusion library/support | Per-player raytrace/cache, exclusions, movement/block invalidation | ABSENT | DEFER: bandwidth gain can cost CPU; test pop-in, glowing, spectator, vanish, vehicles and malformed input. |
| Raytrace AntiXray SDK | M0006, build dependency | External SDK/hooks separate from Paper Anti-Xray | ABSENT; Paper block Anti-Xray ALREADY_UPSTREAM and configurable | KEEP native; DEFER SDK pending need, provenance and latency/visibility tests. |
| Secure Seed | M0038, P0012, seed support | Persistent seed state, generator/RNG changes | ABSENT | DEFER world identity/security milestone; new test worlds only, no production migration. |
| Linear V1/V2 / Buffered storage | M0056; `region/linear` (also V3 sources), region factory | Reader/writer/version/compression and replacement region backend | ABSENT; native Anvil storage | DEFER durability milestone: roundtrip, truncation, disk full, crash/restart, backup and converter. Existence of V3 source alone does not prove every format selectable. |
| Syncmatica | M0054/P0016, Leaves `protocol/syncmatica` | Protocol core, file transfer/storage, permissions | ABSENT | DEFER until client need/version and payload/storage limits tested. |
| AppleSkin | Protocol core, Leaves protocol support | Negotiation and per-player food state | ABSENT | DEFER client integration; no core-MSPT claim. Test vanilla/modded versions and disconnect cleanup. |
| Jade | Protocol core and Jade support | Registry/provider/world queries and payload permissions | ABSENT | DEFER pending client/permission/size/rate tests; avoid exposing hidden data. |
| Xaero | Leaves `protocol/XaeroMapProtocol` | Protocol registration, world identity | ABSENT | DEFER client integration until version/handshake tests. |
| Sentry | Pufferfish `sentry/SentryManager`, appender, config/build dependency | External SDK, async delivery and private-data redaction | ABSENT | DEFER opt-in monitoring; no telemetry enabled. Need bounded queue/rate/redaction tests and operator DSN. |
| Worldgen math/noise | M0009/M0013, C2ME | Density/noise math and precision | ABSENT for these ports; native generator active | DEFER until exploration baseline and exact seed/coordinate output comparisons across workers. |
| Density function compiler | M0044, C2ME DFC support/ASM | Large compiler tree, caches, datapack lifecycle; own tests | ABSENT | DEFER independent generator milestone; no low-risk one-file port. Test all supported nodes, blending and reload. |
| Aquifer/beardifier | M0051, C2ME | Density compiler/math/data layout dependencies | ABSENT for extension | DEFER exact worldgen parity and concurrency gate. |
| Structure optimizer | M0031, StructureLayoutOptimizer/C2ME notices | Jigsaw pool weighting/RNG, optional dedup | ABSENT | DEFER; deduplicated shuffled pools explicitly change layouts. Preserve seed parity for main profile. |
| End biome cache | M0058, C2ME; capacity config | Generator cache lifecycle and coordinate keying | ABSENT for extension | DEFER until End generation measured; bound cache and test negative/large coordinates and seed isolation. |
| Native math | M0071, C2ME native support/build resources | OS/CPU library loading and Java fallback | ABSENT | DEFER platform/dependency gate on Windows i3 host; no assumed AVX support or speedup. |
| Hash/linear palettes | M0012/M0028, Lithium/native loop changes | Palette IDs, serialization and growth | ABSENT for named changes | DEFER until chunk serialization profile and palette boundary/roundtrip tests. |
| Compact bit storage | M0019, ModernFix | Packing/zero-storage representation | ABSENT for extension | DEFER bit-width/empty/full serialization tests and measured memory benefit. |
| Combined heightmap | M0063, Lithium | Chunk setBlockState/heightmap update ordering | ABSENT for extension | DEFER heightmap/block update differential tests. |
| NBT cache bounds | M0050, C2ME | Structure NBT cache and eviction | ABSENT for additional bound | DEFER until cache retention measured; preserve reload behavior and bound memory. |
| Equipment tracking | M0065, Lithium | ItemStack mutation hooks and dirty flags | PARTIAL: vanilla dirty equipment logic exists, Lithium extension absent | DEFER new invalidation until plugin mutations/attributes/equipment packets verified. |
| Recipe lookup | M0011 Carpet-Fixes; M0043/P0014 Pufferfish | Recipe order, reload, complex matching | PARTIAL: native recipe manager present; stream-to-list extension absent | DEFER until recipe workload; preserve last-match priority and all required matcher calls. |
| Suffocation | M0023, Pufferfish | LivingEntity damage cadence | ABSENT | REJECT for transparent survival profile: patch gates checks by tickCount % 10 and invulnerability. Optional behavior tradeoff only after explicit product choice/tests. |
| Fluids | M0030 | Flow caches/shapes/state transitions | ABSENT for extension | DEFER outcome/ordering/reload tests; never reduce scheduled tick budget to claim win. |
| Raid/spawn optimization | M0027, M0005/P0007 Paper PR | Raid wave/RNG; failed-spawn caching/events | PARTIAL: native raids/spawn rules, extra caches absent | DEFER until raid/farm cancellation and invalidation tests; failed-event cache is not automatically safe. |
| VarInt/VarLong writes | M0029 | Netty ByteBuf serialization | PARTIAL: native protocol codecs exist; named write specialization absent | DEFER until packet allocation/CPU profile; boundary/negative/random roundtrip tests required. |
| Non-flush packet sending | M0055, Paper PR 10172 | SingleThreadEventLoop lazy execution and final flush | ABSENT for named lazyExecute hook | DEFER latency/order/alternate event-loop test; cannot assume every plugin transport has the cast type. |
| Useless movement packets | M0068, Airplane/Pufferfish, Paul Sauve | ServerEntity movement flags | UNVERIFIED benefit: patch wraps existing conditions; native path must be truth-table compared | DEFER until reproducer proves changed work, then packet-sequence tests. Avoid importing neutral nesting. |
| Unchanged delta movement distance | M0016, SparklyPaper | Immutable Vec3 identity, ServerEntity | ABSENT identity guard | DEFER until measured benefit; native immutable semantics make a small candidate, not a claimed 15% server gain. |
| Reduced chunk lookups | M0024, Pufferfish | Enderman teleport lookup | ABSENT loaded-chunk fast path | REJECT transparent import as-is: unloaded destination now returns false. A behavior-preserving lookup-only variant needs separate evidence. |
| Projectile chunk loading | M0070, Airplane/Pufferfish | Tickets and per-projectile limits | ABSENT extension | DEFER behavior option; limits/reset/removal alter projectile outcomes. |
| Live collision context | M0067, Pufferfish | ShapeContext entity/held-item queries | ABSENT extension | DEFER collision parity and mutable-state snapshot semantics tests. |
| Random calls in chunk ticking | M0069, Airplane/Pufferfish, Paul Sauve | Per-chunk lightning countdown and RNG | ABSENT | REJECT transparent import: changes RNG consumption/distribution and initializes from a new Random. |
| Per-world MSPT | M0047 and Purpur patch 0005 | World tick boundaries, API/bars | PARTIAL: whole-server Quantum history; no Quantum world breakdown | DEFER or EXTEND native timing after attribution need; world durations must not be summed as concurrent wall time. |
| Command/profile caches | M0036/M0037 | Command source permissions/dispatcher reload; profile TTL | PARTIAL: native profile cache exists; named parse/result changes absent | DEFER invalidation and privacy/auth tests; do not retain expired permission decisions. |
| Relevant bug fixes | Source patches, M0048/M0060 | Each reproducer and upstream overlap | UNVERIFIED per fix; modern Paper already differs from Divine parent | DEFER blanket port; investigate local API version parsing as a concrete separate regression. |
| DAB / lag compensation / XP clumping / spawn range | M0039/M0033/M0045/M0061 | AI cadence, game clock, orb/entity rules and caps | ABSENT named extensions; native configuration controls exist | REJECT transparent default activation; optional behavior-changing features require separate acceptance and plugin/farm tests. |

## Dependency conclusions

P0018/M0062 named “Optimize Moonrise” includes synchronization and collection
replacements used with parallel features; it is not automatically a win for
Quantum's single simulation thread. PWT/RCT, parallel tracker and sensors must
not drag this foundation in without ownership and contention analysis.
Divine `auto-thread-allocation` can enable PWT/RCT together; it will not be copied
into Quantum's conservative profile. Its default-on async flags are not evidence
that those contracts are safe for the operator's plugins.

The first independent work is the measured waypoint/visibility hot path plus
an isolated reproduction of local build-version plugin parsing. Divine ports have since entered integration; see the implementation checkpoint below. The audit covers all brief groups;
DEFER rows identify real validation/product gates and are not implemented toggles.

## Implementation checkpoint - 2026-09-17

The original decision table above is the pre-port audit. This checkpoint records
subsequent implementation, not a performance acceptance or completion of all rows.

| Feature group | Current implementation | Validation still required |
| --- | --- | --- |
| Hash palette, uniform compact storage, combined heightmap, VarInt/VarLong | 0026-0029, default OFF | Small differential/roundtrip checks pass; combined load comparison pending |
| Item mutation and equipment tracking | 0030-0031, default OFF | Count/component/replacement/clear/shared-stack check passes; plugin runtime and mechanics pending |
| Recipe lookup | 0032, default OFF | Ordered matching, removal/re-add and empty-input checks pass; runtime plugin checks pending |
| Noise kernel and bury math | 0033-0034, default OFF | Random-coordinate bit parity check passes; complete generated-world comparison pending |
| End biome cache | 0035, default OFF, bounded, native density only | Seed isolation, native Y-independence, custom Y-dependent bypass and capacity checks pass |
| Async chunk serialization | 0036, default OFF | Snapshot, bounded admission, disconnect and failure checks pass; network/plugin/load validation pending |
| Sleeping hoppers/block entities | Item mutation prerequisite only | Sleep/wake and inventory invalidation implementation remains pending |
| Login ProfileResult cache | REJECT M0037 as written | Username-only reuse skips a new session-digest/IP verification. Keep native authentication on every connection |
| Command parse cache | M0036 needs rewrite | Retains an old source/callback and dispatcher; permissions and source lifecycle cannot be cached indiscriminately |

M0050 bounds the old IOWorker NBT cache used by world-upgrade/recreation paths;
it is not a demonstrated structure-NBT or normal Moonrise chunk-save hot-path optimization.
All other unimplemented rows remain pending; default-OFF configuration is not
counted as an implementation without a connected execution path.

Visibility lookup, immutable movement identity and optional Netty non-flush scheduling now have connected default-OFF paths (Paper 0007, M0037/M0038). Live collision contexts are a separate default-OFF behavior option (M0039); explicit position/placement contexts remain snapshots. Remaining audit rows still require implementation or a justified rejection.
