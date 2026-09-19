# QuantumSpigot 300-player locator gate

Date: 2026-09-19  
Host: Intel Core i3-12100F, 8 logical CPUs, Windows 11, JDK 25  
Jar: `FinalJar/QuantumSpigot-26.2-1.0.jar`  
SHA-256: `88F0A6922089B5B21048EBECF3DDAE9036339179903DA14354D5669469356125`

## Result

| Players | View / simulation | Profile | Duration | Minimum TPS | Worst rolling mean MSPT | Maximum tick | Bot result |
|---:|---:|---|---:|---:|---:|---:|---|
| 300 | 8 / 6 | mixed survival | 120 s + 60 s warmup | 20.00 | 35.3 | 69.7 ms | PASS |

The run used 300 native 26.2 loopback clients, no plugins, pooled Netty allocation, locator bar enabled, and Quantum waypoint collections enabled. All 300 clients stayed connected and completed movement, attacks, block placement/breaking, container, inventory, chat, command, and ping actions. The driver recorded 539,250 movement packets, 4,495 confirmed target damages, 1,304 confirmed block placements, 1,330 confirmed block breaks, and 1,346 confirmed container opens.

The fixture is a prepared flat arena. `allow-flight=true` was used only in the benchmark fixture to prevent vanilla anti-flying kicks during scripted teleport/setup; it is not a production survival recommendation. The server's production gameplay rules, mob caps, tracking range, simulation distance, and locator feature were not disabled or nerfed.

## Code change validated

The locator path now gates high-player refreshes by marker cadence and receiver chunk changes. At 300 players this reduces repeated O(players²) locator reconciliation while retaining the locator bar and cross-chunk connection updates. The watchdog stack from the pre-change run was in `ServerWaypointManager.updateWaypoint` / `EntityBlockConnection.update`; the passing run's JFR moved the leading samples to Moonrise entity collections and natural spawning, with no waypoint method in the top list.

JFR and raw telemetry are retained at:

`C:\Users\skitt\.cache\quantumspigot\runs\smoke-2026-09-19T10-36-15-604Z-23a3c192`

This certifies the stated prepared-arena 300-player fixture only; it does not certify a live Survival world with farms, redstone, plugins, or terrain exploration.
