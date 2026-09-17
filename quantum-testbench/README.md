# Quantum Testbench: local load tests

## Configuration and command smoke test

```powershell
node quantum-testbench/scripts/tuning-smoke.mjs 'purpur-server/build/libs/quantumspigot-26.2-build.DEV.jar' 'C:\path\to\jdk-25\bin\java.exe' 'C:\server\eula.txt'
```

Requires an existing operator-accepted EULA file. Uses a temporary world and
loopback port 25579; never opens the operator's world. Checks effective native
settings, every diagnostic command, armor-stand gravity without rewriting
NoGravity NBT, and safe-mode behavior across a save/restart. Saves both server
logs and the temporary world for diagnosis, then stops the test process.
This is functional verification, not a player-capacity benchmark.

The driver also accepts `-Dquantum.arena-y=300` and
`-Dquantum.arena-spacing=32` for a separately prepared, spread-out fixture.
Default coordinates remain Y=160 and spacing=8. The controller must build and
place players at the matching coordinates before creating `<output.json>.go`.
Before that marker the clients are in setup, not an active workload.
For an authorized loopback backend requiring Velocity forwarding, set
`QUANTUM_FORWARDING_SECRET_FILE` to the existing secret file. The driver signs
only its own `QTestNNN` profiles; it never prints the secret. Without that opt-in,
the original offline fixture protocol is unchanged. Keep online authentication
and forwarding validation enabled on the real network.

The original idle smoke remains available. Active scenarios use native 26.2
clients on an isolated Quantum server with **no added plugins**. This means
Quantum/Purpur gameplay defaults, not the Mojang vanilla server executable.

## Active scenarios

```powershell
.\gradlew.bat -p quantum-testbench installDist --write-locks
node --test quantum-testbench/scripts/metrics.test.mjs
node quantum-testbench/scripts/local-smoke.mjs prepare 'C:\path\to\jdk-25' quantum-testbench/scenarios/calibration.json
# Set eula=true only after the operator accepts Minecraft's EULA.
node quantum-testbench/scripts/local-smoke.mjs run 'C:\prepared\run'
```

Then prepare `scenarios/mixed-ramp.json` (50/100/150/200/250 clients), or
`scenarios/exploration.json` (50/200 creative flying explorers). Each stage
includes 60 seconds of active warmup before its configured measurement time.
The runner stops the ramp when a stage fails. Limits: localhost only, 300
clients, 6 GiB server heap, 2 GiB driver heap, 3 GiB minimum free host memory.

Mixed uses a 169 by 129 block stone platform at Y=159 above a normal seeded
world. Per ten clients: one idle, five walking/jumping (two request sprint),
two placing/digging dirt with a shovel, one attacking a stationary zombie,
one opening/closing a chest and swapping hotbar/offhand items. One fifth chat
every 25 seconds; another fifth requests `/list` every 20 seconds. Activities
are staggered deterministically. Saturation keeps scripted actors fed.
Zombies have no AI and extra health for repeated attacks. Chest item transfers,
crafting, real PvP, farming, redstone, terrain pathfinding and eating are **not**
simulated. Flat-floor physics must never be used as a general terrain client.
Builders receive one stack of dirt: use stages up to five measured minutes.

Explorers move at 4 blocks/second in divergent directions at Y=240 in creative
flight. They retain received chunk coordinates and wait before entering a
chunk that has not arrived; waiting ticks and successful travel steps are
recorded. More than 5% waiting fails workload validation. This is a chunk
generation/streaming stress proxy, not survival travel. The forced arena
remains loaded. Bots do not retain block terrain or render it. Server and
bots share the same machine.

For a real 200-player survival target, pregenerate the intended world before
opening it to players. It removes terrain-generation stalls seen in the
exploration check, while chunk sending, player/entity tracking, simulation,
redstone, mobs and plugins still need their own budget. The arena result is
therefore the relevant capacity proxy for a pregenerated region; exploration
is a separate boundary test.

Reports retain action attempts separately from server responses (target block
state transitions, damage attributed to the attacking bot, container open). Position corrections
and driver scheduling delay help identify invalid bot behavior or driver load.
Performance requires sampled 1-minute TPS >=19.9, observed ticks/coverage >=19.9,
worst rolling average MSPT <40, p95 <50, p99 <50, maximum tick <250 ms,
all requested clients, and a clean shutdown. This is a
strict near-20-TPS gate, not a guarantee that every tick is under 50 ms.
Each percentile is from a rolling 60-second server window; they are never
pooled or averaged into a supposed full-run percentile. JSONL and JFR retain
raw observations. Prepared runs preserve driver jars and source snapshots.
Sampling starts after 60 seconds of active warmup. The first minute's rolling
windows still look back into that active warmup; they exclude join/setup.
Driver scheduling delay, in contrast, has separate exact warmup and measured
maxima. Do not describe the rolling windows as disjoint measured-only ticks.
Use `node quantum-testbench/scripts/analyze-run.mjs <run-directory>` after
shutdown to create `ANALYSIS.md` and a JFR hotspot summary with startup excluded.
A pass on this fixture does not prove 200 unrestricted human
survival players, arbitrary plugins, or long-term reliability.

`mixed-jfr-only.json` tests the same gameplay with bundled Spark disabled in
the fixture's `config/paper-global.yml`. On this Windows/JDK host, JFR linked a
7.887-second safepoint synchronization delay to a Spark sampler `ThreadDump`.
JFR remains enabled, and JVM safepoint timings are retained in `safepoints.log`.
This scenario isolates profiler interference without changing simulation.

`mixed-tuned.json` additionally disables `minecraft:locator_bar`, which removes
player locator markers. This is a gameplay/UI tradeoff, not unchanged defaults.
The pooled allocator scenarios also use `-Dio.netty.allocator.type=pooled`:
the adaptive allocator exhausted 6 GiB direct memory during the 250-client ramp
on this host. Heap remained below 1.5 GiB; increasing heap is not the remedy
being tested. See the dated report for validation and remaining limits.

`quantum-comparison.json` and `purpur-comparison.json` run the same stages,
seed, distances, heap, profiler settings and tuning on the preserved binaries.
They use identical native `/tps` and `/mspt` commands. Those commands round to
0.1 and expose a rolling 60-second mean/min/max, **not p95/p99 or tick counts**.
This comparison has a narrower gate (TPS >=19.9, mean <40 ms, maximum <250 ms,
all clients/workload valid). It must not be presented as passing the full
Quantum percentile gate. The manifest records the collector and server hash.

## Original smoke

This is the first runnable step of the supplied load/reliability blueprint.
It runs a fresh Quantum 26.2 server without added plugins, then native-protocol
headless bots: 60 seconds idle, one bot for 60 seconds, and 20 bots for 120 seconds.
The server binds only to `127.0.0.1:25570`, using an isolated offline-mode world
outside OneDrive. This does not measure Microsoft authentication performance.

The initial bot profile acknowledges configuration, teleport, chunks, ping and
keepalive, sends client ticks and occasionally rotates its head. It does not
render or retain a world, simulate survival physics, explore, fight, or use
inventories. A pass is a protocol/startup/shutdown smoke result, not evidence
of support for 200 realistic players.

## Build and prepare

Use JDK 25 and the parent project's Gradle wrapper:

```powershell
.\gradlew.bat -p quantum-testbench installDist --write-locks
node --test quantum-testbench/scripts/metrics.test.mjs
node quantum-testbench/scripts/local-smoke.mjs prepare 'C:\path\to\jdk-25'
```

Preparation prints a unique directory with a reviewable manifest, an empty
plugins folder, the server jar and fixed server.properties. The operator must
read and accept [Minecraft's EULA](https://www.minecraft.net/en-us/eula) before
setting `eula=true` there. No script accepts the agreement automatically.

```powershell
node quantum-testbench/scripts/local-smoke.mjs run 'C:\path\printed\by\prepare'
```

Minimum available RAM before startup: 12 GiB. JVM heap limits: server 6 GiB,
driver 2 GiB. The controller stops on unexpected disconnect/protocol failure,
server failure, low available memory, or sustained low TPS/high rolling p99.
Only its own child processes are stopped; application windows are not closed.

Console logs, per-stage bot JSON, telemetry JSONL, a JFR recording, manifest
and JSON/Markdown result live in the run directory. MSPT observations are
explicitly rolling 60-second windows; do not average their percentiles into
a supposed full-run percentile. Raw full-tick export is future collector work.

After smoke validation, use progressive 20/50/100/150/200 stages before mixed
gameplay workloads. Host and bot saturation must be distinguished. Repeat
comparable performance scenarios at least three times on restored worlds.

## Dependency provenance

Original testbench sources are covered by the parent `LICENSE-QUANTUM`.
MCProtocolLib uses MIT; its notice is retained in `licenses/MCProtocolLib-LICENSE.txt`.
Pinned coordinate: `org.geysermc.mcprotocollib:protocol:26.2-20260907.143312-19`.
The driver checks Minecraft 26.2 / protocol 776 at startup. Dependency versions
are locked in `gradle.lockfile`; each prepared run hashes every runtime jar.
The protocol is separate from the Quantum server's dependencies.

Upstream source/API reference: GeyserMC/MCProtocolLib feature/26.2 commit
`964619f30b1f2074bb300d46218f17d0c9c71181`. The published binary has its own
recorded checksum; the reference commit is not asserted to be its build commit.
