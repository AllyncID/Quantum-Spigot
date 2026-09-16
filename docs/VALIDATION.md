# Phase 0 / 1 validation

Development build validated 2026-09-16. This file reports executed checks;
the master specification is a roadmap, not evidence of completed validation.

## Environment and source

Windows 11, AMD Ryzen 9 5900HX, 16 logical processors, approximately 32 GiB RAM.
Temurin JDK 25.0.4.1+1, Gradle wrapper 9.4.1, Paperweight 2.0.0-beta.21.
Purpur `3f5d9c0e80b56812c1694e014d44fabf540a4cad` and Paper
`e5fe71723e2ffde7cc9fafc085ac3bb73e63175e` are pinned.
Builds run from a short local mirror outside OneDrive with process-local
`core.longpaths=true` for Git. No global Git/Java configuration was changed.

## Executed checks

* Upstream `applyAllPatches`: PASS, including all Minecraft and server patches.
* Unmodified upstream `:purpur-server:compileJava createPaperclipJar`: PASS.
  Quantum sources were excluded by a temporary init script before integration.
  Preserved runnable baseline: `artifacts/purpur-26.2-baseline.jar`.
  SHA-256: `3715905eedb5ce67057c7549cf4131773876c8eca13bc9f7e02d8ae161d533d3`.
* API compilation with Quantum build configuration: PASS.
* Quantum `applyAllPatches`: PASS, including the branding/build-script patch,
  Paper feature 0006 and Minecraft feature 0023, on top of the pinned upstream.
* Quantum isolated foundation suite with actual API/dependencies: 14 passed,
  covering configuration validation/preservation, tick windows/histograms,
  lag thresholds/cooldown, worker allocation, queue saturation and shutdown.
* Integrated `build test createPaperclipJar --no-configuration-cache`: PASS.
  API: 519 tests (2 skipped); checkstyle module: 3 tests; server: 9285 tests
  (22 skipped). Total: **9783 passed, 24 skipped, zero failures/errors**.
  The server run includes all 14 Quantum tests. The normal upstream test task
  excludes the `Slow` tag; those additional tests were not run.
* Optional test-plugin compilation/package: PASS.
* Archive inspection: Quantum manifest, pinned upstream resource, and Quantum
  runtime/command classes present; exactly one copy of the metadata resource.
* Runnable Paperclip bootstrap and `--help`: PASS (exit 0), including the
  `--quantum-safe-mode` option. This does not start a world or run a load test.
* Baseline CSV summarizer: deterministic 1..100 fixture gives mean 50.5,
  p50 50, p95 95, p99 99, max 100. Non-finite samples rejected. This fixture
  is a tool check and is **not a server performance measurement**.

Runnable artifact: `artifacts/quantumspigot-26.2-build.DEV.jar`, 68221702 bytes.
SHA-256: `92716c269abc8c1afb8c291b16481eaca40477e8918d17073fb4deb5ac8afa60`.
Test helper: `artifacts/quantum-test-plugin-1.0.0-SNAPSHOT.jar`.
Checksums and verification outputs are kept alongside these development artifacts.
The manifest's Git commit is the pinned base because local Quantum source changes
are not committed yet; use the artifact checksum to identify this exact binary.

The first integrated packaging attempt found duplicate upstream metadata from
multiple resource sets. Resource inclusion was restricted to the main source
set, then the complete build and checks were rerun successfully. Standalone
Quantum test classes were also added to the upstream suite-only test selection.

## Remaining runtime gates

The following paragraph records the original Phase 0/1 state on the build
host. Later local tests, operator EULA acceptance and their separate host
are documented in [the active-load investigation](../benchmarks/results/2026-09-16-local-active.md).
Do not combine the two machines' results.

At the end of the original build-only validation, no Minecraft EULA had been
accepted by automation. World startup, safe-mode
startup, diagnostic command behavior, test-plugin scheduler/stall checks,
save/restart checks and an idle comparison baseline have not run yet.
No external plugin compatibility, multi-player load, latency, long-duration
stress, corruption-recovery or comparative performance claim has been verified.
The operator supplied `Quantum-Spigot-Load-Tester (1).md`. It describes a separate
testbench to be implemented; it is not an executable test suite. Target hosts,
plugin/world fixtures, a proven 26.2 bot client, and collector implementation
are still prerequisites for the requested 200-player test. No simulated player
count is a measured result.
CI is configured but has not been executed on a remote repository.

## Current complete build and local runtime update

The follow-up complete build (`applyAllPatches`, `build`, `test` and
`createPaperclipJar`) passed on the local Quantum host. It ran 38 actionable
tasks (31 executed, 7 up-to-date); the integrated suite reported 9783 passed,
24 skipped and zero failures/errors. The resulting packaged jar is
`purpur-server/build/libs/quantumspigot-26.2-build.DEV.jar`, SHA-256
`0B61BC71C1DB7EE4168DCBAA95206E939DA3FFFC70C2AA86070193231AFF09F7`.

The operator accepted the Minecraft EULA and the separate local active-load
report records the runtime evidence, including the pooled-allocator 200/250
arena pass and the 200-explorer terrain-generation limit:
[active-load investigation](../benchmarks/results/2026-09-16-local-active.md).
Those are controlled localhost scenarios, not a release-stability guarantee.

Do not advance to optimization patches or label this a stable release based
on build success alone. Follow `benchmarks/README.md` for the runtime baseline.
