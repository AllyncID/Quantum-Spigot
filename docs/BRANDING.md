# QuantumSpigot branding audit

## Existing identity

`ServerBuildInfoImpl` already exposed `QuantumSpigot` as the human-facing brand
and retained Paper/Purpur brand compatibility IDs. `PurpurConfig` and
`CraftServer` consume that value, and the README already identified the project
as a Purpur/Paper fork maintained by Allync.

## Startup and version output

Paper's bootstrap previously emitted only the Java and implementation loading
lines. `/version`, crash reports, and `ServerBuildInfo` already resolve the
Quantum brand and dynamic build information.

## Applied change

`PaperBootstrap` now emits one compact QuantumSpigot banner before normal
startup messages. It uses Paper's existing Adventure `ComponentLogger`, so
terminal colors follow the existing logger path and file logs strip ANSI codes.
The version and Java values are resolved at runtime; no build or commit value is
hardcoded.

## Preserved compatibility

Paper/Purpur identifiers, upstream attribution, license files, artifact/module
names, API version strings, and plugin-facing compatibility behavior were left
unchanged.
