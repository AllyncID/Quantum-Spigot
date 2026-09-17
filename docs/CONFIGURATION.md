# Configuration

Quantum creates four commented schema-v1 files at startup:

| File | Implemented settings |
| --- | --- |
| `config/quantum/quantum-global.yml` | `profile.active`: compatibility, balanced, custom; `commands.colored-output` (default true) |
| `config/quantum/quantum-diagnostics.yml` | enabled, history-seconds, lag-spike threshold-mspt/consecutive-ticks, plugin-task-threshold-ms, warning-interval-seconds, save-reports, report-queue-capacity, retained-reports under diagnostics |
| `config/quantum/quantum-threading.yml` | main-reserve, jvm-reserve, maximum-total-workers under threading |
| `config/quantum/quantum-performance.yml` | Opt-in chunk rates, profiler control, per-dimension hopper/redstone/armor-stand/anti-xray/block/autosave overrides |

Defaults: balanced, instrumentation on, 900 seconds of tick history, 75 ms
lag threshold for 3 consecutive ticks, 20 ms scheduled-task threshold,
30-second warning/report interval, 16 queued reports, 64 retained reports,
2 processors reserved for main work and 1 for the JVM, automatic total budget.

Every setting requires restart. Unknown keys are preserved but have no effect.
Invalid types/ranges or unsupported schema/profile values abort startup without
rewriting malformed input. Missing defaults are added after validation, with a
backup of each existing rewritten file. Comments and operator values survive.
Duplicate keys, explicit null mapping values, serialized Bukkit objects,
mapping aliases and dotted mapping keys are rejected. Parser input is bounded
to 1 MiB, 262144 code points and 30 nesting levels.

Safe mode (`--quantum-safe-mode`) overrides the effective profile to
compatibility without changing files. Both compatibility and safe mode bypass
all Quantum performance overrides, retaining upstream settings and the operator's
diagnostics settings. Balanced/custom allow explicit overrides only when
`quantum-performance.yml` has `enabled: true` (default false).

## Performance settings

Start from the commented [survival example](examples/quantum-performance-survival.yml).
Every optional override defaults to `inherit`. `world-defaults` inherits the
loaded Paper/Spigot/Purpur configuration. Entries under `worlds` inherit those
defaults and use dimension keys such as `minecraft:overworld`; these are not
world folder names. Explicit values take precedence in memory at startup.
Upstream YAML files are not rewritten. Use a restart rather than `/paper reload`
or Bukkit reload, which can replace the upstream objects without reapplying tuning.
Plugin/API changes made after startup retain their native semantics.

| Section/key | Values and effect |
| --- | --- |
| `chunks.generate-per-second`, `load-per-second`, `send-per-second` | `inherit`, -1 unlimited, or positive finite rate <=1000000; per player, not server-wide. Lower limits can delay terrain. |
| `chunks.concurrent-generates`, `concurrent-loads` | `inherit`, -1 unlimited, 0 automatic, 1..1024; upstream chunk pipeline concurrency per player. |
| `profiling.disable-bundled-spark` | false by default; true disables bundled Spark while tuning is active, including an early-started sampler. Does not disable an external plugin. |
| `world-defaults.hopper` | transfer/check intervals 1..1200 ticks, amount 1..64, cooldown-when-full, disable-move-event, ignore-occluding-blocks. Higher intervals alter farms; skipping events can bypass plugin protections. |
| `world-defaults.redstone.implementation` | `inherit`, `VANILLA`, `EIGENCRAFT`, `ALTERNATE_CURRENT`; optimized engines can change update order. |
| `world-defaults.armor-stands` | tick, collision-lookups, movement, water-movement, gravity: each `inherit`/true/false. Disabling tick/movement freezes physics; useful for decorations, not equivalent gameplay. |
| `world-defaults.anti-xray` | enabled, engine-mode 1..3, max-block-height -2032..2032, update-radius 0..2, lava-obscures, use-permission. Applied before the packet controller is created. Hidden/replacement block lists remain fully configurable in Paper's per-world YAML. |
| `world-defaults.blocks` | block-entity-ticking; max-scheduled-block-ticks/max-scheduled-fluid-ticks 1..1000000. Disabling block entities stops furnaces/hoppers; reducing budgets defers mechanics. Random ticks and natural spawning are unchanged. |
| `world-defaults.saving.max-chunks-per-tick` | `inherit` or 1..10000; spreads native autosave work. Save interval remains upstream-controlled. |

The same world keys can be overridden under `worlds.<dimension-key>`. Example:

```yaml
worlds:
  minecraft:the_nether:
    anti-xray:
      enabled: false
  minecraft:overworld:
    anti-xray:
      enabled: true
      engine-mode: 2
```

Armor-stand `gravity: false` suppresses acceleration while preserving entity
`NoGravity` NBT/API and existing velocity. Setting true/inherit restores ordinary
gravity behavior, including per-entity NoGravity. This avoids permanently
changing entity data when a world policy is later disabled.

`/quantum config` displays effective upstream values for the player's dimension
or every loaded dimension in the console. Async tracking/pathfinding, simulation
tick skipping, packet reducers and alternate disk formats from other forks are
not implemented by these settings. The supplied UniverseSpigot file was a feature
reference; its recommendations do not establish compatibility or performance on
Quantum 26.2. Anti-Xray is protection and adds work, not a TPS optimization.
# Experimental algorithm ports

`quantum-performance.yml` accepts the following global settings, all default
`false`. They also require `enabled: true`; safe/compatibility mode bypasses them.
Restart after changing them. Their performance acceptance tests are pending.

```yaml
experimental:
  algorithms:
    fast-palette: false
    compact-storage: false
    combined-heightmap: false
    varint-writes: false
```

These select runtime code paths, not reduced simulation settings. Fast palette
and compact storage retain the normal packet/storage format; the combined
heightmap path shares downward block scans; VarInt writes retain the standard
encoding. `/quantum config` shows the effective switches. Per-dimension waypoint
collection tuning is `world-defaults.experimental.waypoint-collections` (and
the same path under a `worlds.<dimension>` override); `inherit` is OFF unless
the world default enables it.

## Additional experimental ports

All settings below are under `experimental` in `quantum-performance.yml`, require
`enabled: true`, and are bypassed by compatibility/safe mode. Restart required.

* `mechanics.equipment-tracking` and `mechanics.recipe-lookup`: false.
* `worldgen.end-biome-cache-entries`: 0 (OFF), maximum 65536 per worker/source.
* `worldgen.noise-kernel` and `worldgen.beardifier-math`: false.
* `async.chunk-sending`: false. Only section copies are serialized in workers;
  block entities, heights, lighting, visibility and plugin events stay on the owner.
* `async.chunk-workers`: 1, capped by remaining Quantum worker budget.
* `async.chunk-queue-capacity`: 64 waiting snapshots, range 1-1024. Occupied
  slots are bounded before making a world copy. Overflow uses native synchronous
  serialization. Anti-Xray-modified packets use the native path.

`/quantum threads` and lag reports expose actual chunk-worker counts, queue depth,
oldest waiting age, last queue/copy/compute latency, fallback/failure/cancellation
counts. These measure serialization submission, not client chunk readiness.
Experimental performance benefits and combined plugin behavior remain unverified.

Additional default-OFF keys: `experimental.network.visibility-lookup`, `experimental.network.unchanged-movement`, `experimental.network.lazy-flush`, and `experimental.mechanics.live-collision-context`. The last option changes entity-context sampling from captured to current state; explicit placement/position contexts are preserved.
