# QuantumSpigot local Survival — 450 bot test

- Date: 2026-09-19
- Server: `G:\Minecraft Server\Survival`, QuantumSpigot 26.2-DEV-Allync
- Workload: 450 mixed bots, separate chunks, movement/combat/inventory/chat/command actions, no plugins
- Login admission: `0` seconds for this test only; restored to `5` seconds afterward
- Result: **FAIL 20 TPS target**

The testbench reached all 450 bots with zero unexpected disconnects. During the ramp, the 400-player sample reported 5-second TPS **5.38**, 60-second average MSPT **55.18**, p95 **158.45**, and max **244.41**. The completed test recorded `minimumReady=450`, `driverMaxTickDelayMs=756.882`, transport p50 **576.57 ms**, p95 **982.73 ms**, and max **1826.80 ms**.

The server watchdog later stopped the run during disconnect cleanup. This confirms the 450-player workload still overwhelms this local host; the test did not meet 20 TPS.

The test-only client read timeout was raised with `-Dquantum.bot-read-timeout-seconds=120`; production server settings were not changed by that flag. The offline profile lookup patch avoids Mojang texture lookups during local offline-mode login.
