# Source and patch provenance

| Component | Exact source | License / treatment | Status |
| --- | --- | --- | --- |
| Purpur base | PurpurMC/Purpur `3f5d9c0e80b56812c1694e014d44fabf540a4cad` | MIT for Purpur patches unless a patch says otherwise; preserve original LICENSE and patch authors | Inherited |
| Paper base | PaperMC/Paper `e5fe71723e2ffde7cc9fafc085ac3bb73e63175e` | Paper LICENSE.md states GPL-3.0 inheritance, with listed author contributions also offered under MIT; preserve original notices | Inherited through Paperweight |
| Paperweight | PaperMC/paperweight 2.0.0-beta.21 | Build dependency; retain its published metadata and license | Upstream tooling |
| Quantum foundation | Original work in this repository | MIT for new Quantum sources; does not relicense upstream | Phase 0/1 |
| Quantum Paper integration | `purpur-server/paper-patches/features/0006-Quantum-identity-and-scheduler-diagnostics.patch` | Original MIT additions on the pinned base; preserve upstream notices | Reapplied successfully |
| Quantum Minecraft integration | `purpur-server/minecraft-patches/features/0023-Quantum-runtime-and-tick-observability.patch` | Original MIT additions on the pinned base; preserve upstream notices | Reapplied successfully |
| Pufferfish candidates | No commit imported | License/applicability/benchmark review required before any import | Not evaluated |

The top-level MIT license does not mean the complete Minecraft server or all
dependencies are MIT. Minecraft artifacts are obtained by upstream tooling
and remain subject to Mojang's terms. Bukkit/Spigot/Paper components retain
their original licenses. Do not redistribute generated Minecraft sources.

No proprietary implementation or external optimization patch was imported.
No newly introduced runtime library is needed for Quantum instrumentation;
configuration, logging, and tests reuse dependencies already supplied upstream.

Before importing an optimization, add repository, full commit, patch path,
copyright, license, compatibility decision (PORT/ALREADY_UPSTREAM/OBSOLETE/
REWRITE/REJECT), test evidence, and comparative benchmark evidence here and
in its patch header and commit message.
