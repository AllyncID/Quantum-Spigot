# Configuration

Quantum creates three commented schema-v1 files at startup:

| File | Implemented settings |
| --- | --- |
| `config/quantum/quantum-global.yml` | `profile.active`: compatibility, balanced, custom |
| `config/quantum/quantum-diagnostics.yml` | enabled, history-seconds, lag-spike threshold-mspt/consecutive-ticks, plugin-task-threshold-ms, warning-interval-seconds, save-reports, report-queue-capacity, retained-reports under diagnostics |
| `config/quantum/quantum-threading.yml` | main-reserve, jvm-reserve, maximum-total-workers under threading |

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
compatibility without changing files. It retains diagnostics according to the
operator's diagnostics settings. All available profiles currently preserve the
same Purpur simulation; there are no hidden performance overrides.

Entity, network, adaptive, security and per-world Quantum settings in the
master specification are future work. Existing Paper/Purpur configuration is
the authority for those systems; it is not migrated or rewritten by Quantum.
