# Quantum Spigot vs Nautilus local comparison

Date: 2026-09-19
Host: Windows 11, Intel i3-12100F (8 logical processors), JDK 25
Workload: local loopback, no plugins, native-protocol mixed survival actions,
pooled Netty, `allow-flight=true` for the test fixture only.

Nautilus binary: Jenkins build [Nautilus 26.2 #20](https://ci.obydux.win/job/Nautilus/job/26.2/20/artifact/nautilus-server/build/libs/nautilus-paperclip-26.2-R0.1-SNAPSHOT.jar), SHA-256
`4b87a2cb2c9d1009a309c8edce59bdcb7c6cb5dd3b03a5b27c590a6489dccff9`.

The comparison image is [quantum-vs-nautilus-3-phase-bars.png](quantum-vs-nautilus-3-phase-bars.png).

## Measurements

| Software | Players | MSPT | Minimum TPS | Run | Status |
|---|---:|---:|---:|---|---|
| Quantum Spigot | 200 | 37.10 ms | 20.00 | `smoke-2026-09-18T19-21-25-840Z-a5e84ac1` | valid mixed stage |
| Quantum Spigot | 300 | 41.11 ms | 20.00 | `smoke-2026-09-18T20-34-47-604Z-01baf159` | valid mixed stage |
| Quantum Spigot | 450 | 46.47 ms | 19.96 | `smoke-2026-09-19T05-09-14-137Z-d3171955` | valid mixed stage; strict MSPT gate failed |
| Nautilus | 200 | 60.6 ms | 16.5 | `smoke-2026-09-18T15-42-38-874Z-df754b74` | last native rolling sample before health stop |
| Nautilus | 300 | — | — | `smoke-2026-09-19T05-50-15-469Z-44b6eac4` | watchdog stall before valid rolling window |
| Nautilus | 450 | — | — | `smoke-2026-09-19T05-54-06-482Z-388266ee` | watchdog stall before valid rolling window |

Quantum 200/300 used view distance 8, simulation distance 6, and 32-block
arena spacing. Quantum 450 and Nautilus 300/450 used the 450-player prepared
fixture with view distance 6, simulation distance 4, and 16-block spacing;
therefore the graph is a practical local stress comparison, not a perfectly
identical fixture at every point.

Nautilus 300 and 450 did connect all requested clients, then stopped responding
long enough for the server watchdog to create a thread dump. No zero values are
invented for those phases; the image marks them as stalled.
