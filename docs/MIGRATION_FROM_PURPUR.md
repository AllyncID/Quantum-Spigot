# Evaluating migration from Purpur

Stop the server and back up the full world and configuration directories.
Test the Quantum jar against a copy of a matching 26.2 installation first.
Keep `server.properties`, `bukkit.yml`, `spigot.yml`, Paper configuration and
`purpur.yml`; Quantum reads additional files under `config/quantum`.

Compare plugin behavior, teleports, inventories, chunk loading and restart
persistence before considering deployment. Compatibility testing is incomplete
for this milestone; review PLUGIN_COMPATIBILITY.md. To roll back, stop Quantum
and restore the backup rather than trying to downgrade a modified world.
