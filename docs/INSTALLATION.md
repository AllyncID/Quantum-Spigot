# Installation

Use Java 25. Build with the checked-in Gradle wrapper; see DEVELOPMENT.md.
The runnable output is a Paperclip jar, not the plain server/API jar.

For a fresh test installation, use a separate working directory:

```sh
java -Xms2G -Xmx2G -jar quantumspigot-26.2-build.DEV.jar --nogui
```

Read and accept Minecraft's EULA yourself before enabling a server. No script
in this project sets `eula=true` automatically. Use an isolated disposable
world for smoke tests, bind the test server to loopback and use a distinct port.

This development milestone is not a stable production release. Back up worlds
and configurations before evaluating it. Use `--quantum-safe-mode` to select
compatibility while retaining diagnostics. No universal JVM flag set is
prescribed; match heap and GC choices to measured workload and available RAM.

## Local load-test configuration

The dated [active-load report](../benchmarks/results/2026-09-16-local-active.md)
records this Windows/JDK 25 candidate. It is configuration tuning, not a new
parallel gameplay engine or a universal 200-player guarantee:

* `server.properties`: `view-distance=6`, `simulation-distance=4`.
* Existing `config/paper-global.yml`: set `spark.enabled: false` and
  `spark.enable-immediately: false`; retain unrelated settings.
* Console: `gamerule minecraft:locator_bar false`. This removes player locator
  markers above the hotbar. Set it to `true` to restore them.
* JVM: add `-Dio.netty.allocator.type=pooled` before `-jar`. The test uses
  `-Xms1G -Xmx6G`, with JFR retained for diagnosis.

Keep the loopback/offline settings in the disposable test harness. They are
not a public-server deployment configuration. Run the supplied scenarios to
verify behavior on the intended hardware and world.
