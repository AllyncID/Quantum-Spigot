# QuantumSpigot

Minecraft Java Edition **26.2** server software built on Purpur/Paper, focused
on plugin compatibility, observability and measured performance engineering.
Requires **Java 25**.

This repository is the Phase 0/1 development foundation. It does not yet claim
a performance advantage or production stability. Gameplay and authoritative
world mutation retain the upstream execution model; no Folia scheduler or
region-aware plugin metadata is required.

## Foundation

* Versioned, validated configuration with comments and migration backups.
* Bounded tick history, MSPT histogram and exact rolling quantiles.
* Permission-controlled `/quantum` diagnostics, lag incidents and slow sync tasks.
* Colored command summaries and `/quantum config` for effective world settings.
* Opt-in native hopper, redstone, armor-stand, Anti-Xray, chunk and block controls,
  with per-dimension inheritance and a [survival preset](docs/examples/quantum-performance-survival.yml).
* Bounded diagnostic IO with visible rejection/error counts.
* A global budget for future Quantum workers, and a compatibility safe mode.
* An isolated test plugin and repeatable baseline collection tools.

Check [validation](docs/VALIDATION.md) for actual build/test evidence and
remaining gates. Async pathfinding, parallel tracking, adaptive simulation and
other optimizations in the master specification are future work.

## Build

Clone this Git repository and use the wrapper with JDK 25:

```sh
./gradlew applyAllPatches
./gradlew build test
./gradlew createPaperclipJar
```

On Windows use `gradlew.bat`. The runnable artifact is
`purpur-server/build/libs/quantumspigot-26.2-build.<BUILD>.jar` (`DEV` locally).
The module directories retain upstream names to simplify patch maintenance.
The plain API/server jars are not runnable distributions.

Start from a separate test directory:

```sh
java -Xms2G -Xmx2G -jar quantumspigot-26.2-build.DEV.jar --nogui
```

Read Minecraft's EULA before accepting it. Back up worlds/configuration before
evaluating this development build. Add `--quantum-safe-mode` for compatibility
profile diagnostics. Heap/GC settings must be chosen for the workload; no
universal performance flag set is imposed.

## Documentation

* [Installation](docs/INSTALLATION.md), [configuration](docs/CONFIGURATION.md), [commands](docs/COMMANDS.md)
* [Architecture](docs/ARCHITECTURE.md), [threading](docs/THREADING.md), [performance status](docs/PERFORMANCE.md)
* [Plugin compatibility](docs/PLUGIN_COMPATIBILITY.md), [troubleshooting](docs/TROUBLESHOOTING.md)
* Migration from [Paper](docs/MIGRATION_FROM_PAPER.md) or [Purpur](docs/MIGRATION_FROM_PURPUR.md)
* [Development](docs/DEVELOPMENT.md), [upstream process](docs/UPSTREAM.md), [provenance](docs/UPSTREAM_PATCHES.md)
* [Benchmark harness](benchmarks/README.md), [benchmark methodology](docs/BENCHMARKING.md)
* [Local active bots](quantum-testbench/README.md), [2026-09-16 load investigation](benchmarks/results/2026-09-16-local-active.md)

## Credits and licensing

Based on [Purpur](https://github.com/PurpurMC/Purpur) and
[Paper](https://github.com/PaperMC/Paper), including Bukkit/Spigot APIs.
The original Purpur [MIT license](LICENSE) is retained. Original Quantum
additions use [MIT](LICENSE-QUANTUM); upstream components keep their respective
licenses, including Paper's GPL-3.0 inheritance and author-specific MIT grants.
This does not relicense Minecraft or third-party libraries. See the provenance
ledger before redistributing artifacts.

QuantumSpigot is not affiliated with Mojang, Microsoft, PaperMC, PurpurMC or
Pufferfish. No Pufferfish or proprietary optimization code has been imported.
