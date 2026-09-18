# Source and patch provenance

| Component | Exact source | License / treatment | Status |
| --- | --- | --- | --- |
| Purpur base | PurpurMC/Purpur `3f5d9c0e80b56812c1694e014d44fabf540a4cad` | MIT for Purpur patches unless a patch says otherwise; preserve original LICENSE and patch authors | Inherited |
| Paper base | PaperMC/Paper `e5fe71723e2ffde7cc9fafc085ac3bb73e63175e` | Paper LICENSE.md states GPL-3.0 inheritance, with listed author contributions also offered under MIT; preserve original notices | Inherited through Paperweight |
| Paperweight | PaperMC/paperweight 2.0.0-beta.21 | Build dependency; retain its published metadata and license | Upstream tooling |
| Quantum foundation | Original work in this repository | MIT for new Quantum sources; does not relicense upstream | Phase 0/1 |
| Quantum Paper integration | `purpur-server/paper-patches/features/0006-Quantum-identity-and-scheduler-diagnostics.patch` | Original MIT additions on the pinned base; preserve upstream notices | Reapplied successfully |
| Quantum Minecraft integration | `purpur-server/minecraft-patches/features/0023-Quantum-runtime-and-tick-observability.patch` | Original MIT additions on the pinned base; preserve upstream notices | Reapplied successfully |
| Quantum world tuning | `purpur-server/minecraft-patches/features/0024-Quantum-configurable-world-tuning.patch` | Original MIT hooks before world-controller creation and armor-stand gravity; no external fork implementation imported | Base-index apply check passed; compiled with server |
| Additional fork candidates | Pinned DivineMC snapshot; see per-feature audit | Original notices and licensing retained individually | Integration in progress |

The top-level MIT license does not mean the complete Minecraft server or all
dependencies are MIT. Minecraft artifacts are obtained by upstream tooling
and remain subject to Mojang's terms. Bukkit/Spigot/Paper components retain
their original licenses. Do not redistribute generated Minecraft sources.

No proprietary implementation was imported. The following opt-in algorithm
ports now come from DivineMC's pinned source; they retain their original
licenses and are outside the scope of `LICENSE-QUANTUM`:

| Quantum patch | DivineMC patch at `039981b0bf973ea9bee0f2521b2c01d9bb419802` | Original source / author | Adaptation |
| --- | --- | --- | --- |
| 0026 | M0012 faster hash palette + `LithiumHashPalette.java` | Lithium, 2No2Name, LGPL-3.0; integration NONPLAYT | OFF by default; upstream factory fallback; copy retains bit width; Moonrise override access |
| 0027 | M0019 compact bit storage | ModernFix, embeddedt, GPL-3.0; integration NONPLAYT | OFF by default; preserve Paper palette finalization and Anti-Xray preset injection |
| 0028 | M0063 combined heightmap + `CombinedHeightmapUpdate.java` | Lithium, 2No2Name, LGPL-3.0; integration NONPLAYT | OFF by default; native four-update fallback; expose setter used by helper |
| 0029 | M0029 VarInt/VarLong writes | Velocity, Andrew Steinborn, GPL-3.0; integration NONPLAYT | OFF by default; retain upstream methods, including public `writeSlow`, as fallback |
| 0030 | Selected ItemStack/PatchedDataComponentMap changes from M0064 | Lithium, 2No2Name, LGPL-3.0; integration NONPLAYT | Mutation publishers only; this does not implement sleeping hoppers |
| 0031 | M0065 equipment tracking | Lithium, 2No2Name, LGPL-3.0; integration NONPLAYT | Re-subscribe decoded equipment; retain nonzero-count subscriptions; unsubscribe before clear; retain event-time changes |
| 0032 | M0011 recipe lookup | Carpet-Fixes (fxmorin), LGPL-3.0; Divine integration GPL-3.0 | Default OFF; ordered matcher calls, mutable recipe add/remove path and last-match priority retained |
| 0033 | ImprovedNoise subset of M0013 | C2ME, ishland, MIT | Preserve original public rounding/derivative path; only replace interpolation kernel |
| 0034 | Beardifier subset of M0009 | C2ME, ishland, MIT | Opt-in algebraic bury-distance calculation |
| 0035 | M0058 End biome cache | C2ME, ishland, MIT | Rewrite with bounded per-worker/source map, sampler identity invalidation and native Y-independent density guard |
| 0036 | M0035 async chunk sending | DivineMC, NONPLAYT, GPL-3.0 | Owner snapshot, bounded queue, synchronous overflow, native Anti-Xray fallback, preserved ready barrier, cancellation/failure handling |


The exact DivineMC commit contains the adapted source being copied. Its patch
headers identify earlier origin projects but do not provide original origin
commit IDs; that provenance limit remains recorded rather than inventing IDs.
License texts are retained under `licenses/`. Native integration is distributed
as patches, not generated Minecraft source. Algorithm correctness checks are in
`AlgorithmPortsTest`; no capacity or performance benefit is established yet.

No newly introduced runtime library is needed for Quantum instrumentation;
configuration, logging, and tests reuse dependencies already supplied upstream.

Before importing an optimization, add repository, full commit, patch path,
copyright, license, compatibility decision (PORT/ALREADY_UPSTREAM/OBSOLETE/
REWRITE/REJECT), test evidence, and comparative benchmark evidence here and
in its patch header and commit message.

Additional license references: [C2ME MIT](https://github.com/RelativityMC/C2ME-fabric/blob/ver/1.21.1/LICENSE), [Carpet-Fixes LGPL](https://github.com/fxmorin/carpet-fixes/blob/master/LICENSE). These identify project licensing; the imported adapted implementations remain pinned to the DivineMC commit above.

| Additional port | Source at pinned DivineMC commit | Adaptation and checks |
| --- | --- | --- |
| Paper 0007 visibility | P0010 SparklyPaper, GPL-3.0 integration | Opt-in fastutil map and empty shortcut; retain self-equality. Default/inverted visibility test passes |
| 0037 unchanged movement | M0016 SparklyPaper, GPL-3.0 integration | Same immutable vector skips distance calculation; thresholds and packet sequence unchanged |
| 0038 non-flush sends | M0055 Paper PR 10172, GPL-3.0 | Supported SingleThreadEventLoop only; flush and alternate-loop fallback test passes |
| 0039 live collision | M0067 Airplane/Pufferfish, Paul Sauve, GPL-3.0 | Separate default-OFF behavior option. Explicit position and placement remain captured; context regression passes |

Native patches 0024 through 0039 and Paper 0007 passed staged-index reapplication.
Focused algorithm, recipe/visibility/Netty/collision, equipment, End-cache/worldgen
and configuration checks passed (19 checks in the combined development run).
The separate async-chunk run passed 3 snapshot/queue/failure checks plus 8 config
checks. The follow-up equipment stack-resurrection and wrong-slot unsubscription regression also passes.
No end-to-end performance or plugin-capacity acceptance is implied.
License texts and this provenance file are included in the server jar under
`META-INF/licenses/quantum-ports` when packaged.

## Inventory revisions and idle machines - 2026-09-18

Native 0040 and 0041 are Quantum implementations after auditing Divine M0064
at the same pinned commit above. They reuse the Lithium mutation publishers
attributed to 2No2Name (LGPL-3.0) in 0030; they do not import the complete Divine
sleeping-hopper suite. `TrackedItemList` shares fixed-size inventory revision
tracking between cached hopper classification and machine wake-up. Mutation
publishers now activate based on subscription, independently of equipment's flag.
The post-enchantment callback is issued after the component mutation.

0041 keeps native ticker ordering/removal/mid-tick scheduling and verifies the
wrapper's current owner before sleep/wake. Admission is limited to idle native
furnaces, brewing stands, campfires and crafters. Paper 0008 wakes live BlockState
access because its timer setters can mutate native fields without an NBT load.
Native menu timer setters wake too. Both options default OFF. No plugin event
result, hopper transfer or cooldown is skipped by these patches.

`HopperInventoryCacheTest` compares classification with the native algorithm
over 1000 mutations and checks replacement/shared/zero-count stacks.
`BlockEntitySleepTest` covers idle admission, inventory/state/timer wake-up,
live Paper state mutation, unload/reattach, stale owner rejection and native
mid-tick/removal cadence. Equipment regression is rerun with the shared publishers.
Full survival mechanics and plugin/load acceptance are still pending.

## Observability extensions - 2026-09-18

0042 records native per-world tick duration, idle/active block-entity counts,
Moonrise chunk/entity/POI IO work and autosave queue age without changing tick,
save or ticket order. 0009 adds bounded owner-thread sampling around synchronous
plugin tasks, registered event listeners and Bukkit plugin commands. It keeps
inclusive and exclusive time separate for nested hooks and stores only bounded
names/numbers. Both features are diagnostic and default OFF where sampling or
world histories add overhead. They do not cover async/direct NMS/native
Brigadier work or establish a performance gain.
