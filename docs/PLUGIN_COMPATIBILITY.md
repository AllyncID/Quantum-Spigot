# Plugin compatibility

Quantum retains Bukkit/Spigot/Paper/Purpur packages, scheduler ordering and
single-authoritative-thread semantics. This is a design target, not a completed
binary or behavior compatibility certification. No Folia metadata is required.

| Plugin | Version tested | Status |
| --- | --- | --- |
| LuckPerms | none | UNTESTED |
| Vault | none | UNTESTED |
| EssentialsX | none | UNTESTED |
| WorldEdit | none | UNTESTED |
| WorldGuard | none | UNTESTED |
| PlaceholderAPI | none | UNTESTED |
| ProtocolLib | none | UNTESTED |
| ViaVersion / ViaBackwards | none | UNTESTED |
| CoreProtect | none | UNTESTED |
| Citizens | none | UNTESTED |
| DecentHolograms | none | UNTESTED |
| Anticheat, chat and economy representatives | none | UNTESTED |

Record exact plugin versions and startup, event, scheduler, inventory, teleport,
restart/persistence and protocol checks before upgrading a status. A plugin
appearing in `/quantum plugins` means a slow scheduled task was observed;
absence from that list is not proof of compatibility.
