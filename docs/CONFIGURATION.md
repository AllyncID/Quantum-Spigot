# QuantumSpigot configuration

QuantumSpigot reads one authoritative file from the server root:

```text
quantumspigot.yml
```

On the first start after this change, the four legacy files under `config/quantum/` are merged into the root file. Each existing legacy file is retained beside itself as `*.pre-root.yml`; it is no longer read. The typed snapshot is validated once at startup, so gameplay loops never perform YAML lookups.

Use these commands from the console or an operator:

```text
/quantum config
/quantum profile
/quantum reload
```

`/quantum reload` parses and validates a complete replacement before publishing it. A malformed file leaves the previous snapshot active. Diagnostics, profile and admission values are safe to inspect immediately; world tuning and worker-pool changes are reported as restart-required because their native objects are created during startup.

The default `network.login-admission-interval-seconds: 5` spreads completed player admissions by five seconds. Set it to `0` only when a controlled login burst is required. This does not sleep or block the server thread.
