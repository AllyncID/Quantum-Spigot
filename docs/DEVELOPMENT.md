# Development

Prerequisites: Git, JDK 25, internet for first build. Keep the Gradle wrapper.

```sh
./gradlew applyAllPatches
./gradlew build test
./gradlew createPaperclipJar
```

Windows equivalents use `gradlew.bat`. The locally provisioned JDK for this
workspace is `C:/Users/shendy/.cache/quantumspigot/jdk-25.0.4.1+1`; this is a
developer machine path, not a required installation location.

The Windows helper handles Git's long-path setting for just its child process:

```powershell
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk-25'
```

The 26.2 resource tree can exceed Windows Git's default path limit. Build in a
short local directory outside OneDrive for faster filesystem operations. Keep
source changes synchronized explicitly when using a separate build mirror.

Generated `paper-server` and `purpur-server/src/minecraft` files are not
committed. Rebuild Quantum changes into feature patches with Paperweight,
inspect their scope, and prove they reapply. Keep original Purpur patch authors
and notices intact. New Quantum internal sources remain under
`purpur-server/src/main/java/dev/quantumspigot/server`.

Branding touches `settings.gradle.kts`, `gradle.properties`, the server Gradle
patch (manifest and Paperclip artifact), the API archive identity,
`ServerBuildInfoImpl`, the version-fetcher bridge, and startup telemetry hook.
Purpur already disables Paper's startup update check. Quantum does not relocate
Bukkit or Purpur packages or invent a remote
Quantum release service.

Tests cover rolling samples/histograms, configuration failure/preservation,
worker budgets, saturation, shutdown and lag thresholds. Use the optional
test plugin only in an isolated smoke/benchmark installation.

Publishing a binary or claiming stable performance requires the broader
compatibility, restart, stress and comparative benchmark gates from the master
specification. See VALIDATION.md for the work actually verified in this tree.
