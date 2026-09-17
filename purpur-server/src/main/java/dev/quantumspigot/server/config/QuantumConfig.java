package dev.quantumspigot.server.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Validated once at startup; no YAML access from the tick path. */
public record QuantumConfig(Profile profile, boolean safeMode, Diagnostics diagnostics, Workers workers,
                            boolean coloredCommands, Performance performance) {
    public enum Profile { COMPATIBILITY, BALANCED, CUSTOM }

    public record Diagnostics(boolean enabled, int historySeconds, double lagThresholdMs, int consecutiveTicks,
                              double pluginThresholdMs, int warningIntervalSeconds, boolean saveReports,
                              int reportQueueCapacity, int retainedReports) {}

    public record Workers(int mainReserve, int jvmReserve, int maximumTotal) {}

    // Null overrides mean inherit the existing Paper/Spigot/Purpur setting.
    public record Hopper(Integer transferTicks, Integer checkTicks, Integer amount, Boolean cooldownWhenFull,
                         Boolean disableMoveEvent, Boolean ignoreOccludingBlocks) {}
    public record ArmorStands(Boolean tick, Boolean collisionLookups, Boolean movement, Boolean waterMovement, Boolean gravity) {}
    public record AntiXray(Boolean enabled, Integer engineMode, Integer maxBlockHeight, Integer updateRadius,
                           Boolean lavaObscures, Boolean usePermission) {}
    public record Blocks(Boolean blockEntityTicking, Integer maxBlockTicks, Integer maxFluidTicks) {}
    public record WorldTuning(Hopper hopper, ArmorStands armorStands, AntiXray antiXray, Blocks blocks,
                              String redstone, Integer autoSaveChunks, Boolean waypointCollections) {
        public static final WorldTuning INHERIT = new WorldTuning(new Hopper(null, null, null, null, null, null),
            new ArmorStands(null, null, null, null, null), new AntiXray(null, null, null, null, null, null),
            new Blocks(null, null, null), null, null, null);
    }
    public record Performance(boolean enabled, boolean disableBundledSpark, Double generateRate, Double loadRate,
                              Double sendRate, Integer concurrentGenerates, Integer concurrentLoads,
                              WorldTuning defaults, Map<String, WorldTuning> worlds, Algorithms algorithms, Mechanics mechanics, Worldgen worldgen, Async async, Network network) {}
    public record Network(boolean visibilityLookup, boolean unchangedMovement, boolean lazyFlush) {
        public static final Network DISABLED = new Network(false, false, false);
    }

    public Network network() {
        return this.performanceActive() ? this.performance.network() : Network.DISABLED;
    }
    public record Async(boolean chunkSending, int chunkWorkers, int chunkQueueCapacity) {
        public static final Async DISABLED = new Async(false, 1, 64);
    }

    public Async async() {
        return this.performanceActive() ? this.performance.async() : Async.DISABLED;
    }
    public record Worldgen(int endBiomeCacheEntries, boolean noiseKernel, boolean beardifierMath) {
        public static final Worldgen DISABLED = new Worldgen(0, false, false);
    }

    public Worldgen worldgen() {
        return this.performanceActive() ? this.performance.worldgen() : Worldgen.DISABLED;
    }
    public record Mechanics(boolean equipmentTracking, boolean recipeLookup, boolean liveCollisionContext) {
        public static final Mechanics DISABLED = new Mechanics(false, false, false);
    }

    public Mechanics mechanics() {
        return this.performanceActive() ? this.performance.mechanics() : Mechanics.DISABLED;
    }
    public record Algorithms(boolean fastPalette, boolean compactStorage, boolean combinedHeightmap, boolean varintWrites) {
        public static final Algorithms DISABLED = new Algorithms(false, false, false, false);
    }

    public Algorithms algorithms() {
        return this.performanceActive() ? this.performance.algorithms() : Algorithms.DISABLED;
    }

    public boolean performanceActive() {
        return this.performance.enabled() && !this.safeMode && this.profile != Profile.COMPATIBILITY;
    }

    public WorldTuning worldTuning(String dimensionKey) {
        return this.performanceActive() ? this.performance.worlds().getOrDefault(dimensionKey, this.performance.defaults())
            : WorldTuning.INHERIT;
    }

    public static QuantumConfig load(Path directory, boolean safeMode) throws IOException, InvalidConfigurationException {
        Document global = new Document(directory.resolve("quantum-global.yml"));
        Document diagnostics = new Document(directory.resolve("quantum-diagnostics.yml"));
        Document threading = new Document(directory.resolve("quantum-threading.yml"));
        Document performance = new Document(directory.resolve("quantum-performance.yml"));
        Profile configured;
        String profile = global.string("profile.active", "balanced", "compatibility bypasses Quantum tuning; balanced/custom permit explicit performance overrides.");
        try {
            configured = Profile.valueOf(profile.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw global.invalid("profile.active", "compatibility, balanced, or custom (other profiles are not implemented)");
        }
        QuantumConfig result = new QuantumConfig(safeMode ? Profile.COMPATIBILITY : configured, safeMode,
            new Diagnostics(
                diagnostics.bool("diagnostics.enabled", true, "SAFE: tick and synchronous scheduler instrumentation; restart required."),
                diagnostics.integer("diagnostics.history-seconds", 900, 60, 3600, "Maximum history, sized for 20 TPS. Fast ticks may shorten coverage."),
                diagnostics.number("diagnostics.lag-spike.threshold-mspt", 75.0, 1.0, 60000.0, "Record an incident after consecutive slow ticks."),
                diagnostics.integer("diagnostics.lag-spike.consecutive-ticks", 3, 1, 1200, "Consecutive threshold breaches required."),
                diagnostics.number("diagnostics.plugin-task-threshold-ms", 20.0, 0.1, 60000.0, "Warn about synchronous scheduled tasks; never cancel them."),
                diagnostics.integer("diagnostics.warning-interval-seconds", 30, 1, 3600, "Minimum interval between repeated warnings and lag reports."),
                diagnostics.bool("diagnostics.save-reports", true, "Write bounded text reports under quantum-reports/lag-spikes."),
                diagnostics.integer("diagnostics.report-queue-capacity", 16, 1, 256, "Bounded IO queue. Overflow drops diagnostics, never blocks the tick."),
                diagnostics.integer("diagnostics.retained-reports", 64, 1, 1024, "Maximum Quantum lag reports retained on disk.")),
            new Workers(
                threading.integer("threading.main-reserve", 2, 0, 1024, "Logical processors reserved when planning Quantum workers."),
                threading.integer("threading.jvm-reserve", 1, 0, 1024, "Additional reservation for JVM/GC work."),
                threading.autoPositive("threading.maximum-total-workers", -1, "-1: automatic. Quantum budget only; does not resize upstream workers.")),
            global.bool("commands.colored-output", true, "Color Quantum command headers, labels and health metrics; false uses plain text."),
            loadPerformance(performance));
        // Validate every file before changing any of them.
        global.saveDefaults();
        diagnostics.saveDefaults();
        threading.saveDefaults();
        performance.saveDefaults();
        return result;
    }

    private static Performance loadPerformance(Document doc) throws InvalidConfigurationException {
        boolean enabled = doc.bool("enabled", false, "Opt in to the overrides below. Disabled and compatibility/safe mode retain upstream settings. Restart required.");
        boolean disableSpark = doc.bool("profiling.disable-bundled-spark", false, "Disable bundled Spark when overrides are active; useful for the measured Windows profiler stalls. External plugins are unaffected.");
        Double generate = doc.rate("chunks.generate-per-second", "New chunks per player per second: inherit, -1 unlimited, or positive. Lower limits slow exploration.");
        Double load = doc.rate("chunks.load-per-second", "Chunk loads per player per second; also limits generation. inherit, -1 unlimited, or positive.");
        Double send = doc.rate("chunks.send-per-second", "Chunk packets per player per second. Lower limits delay client terrain delivery.");
        Integer concurrentGenerate = doc.overrideInt("chunks.concurrent-generates", null, -1, 1024, "Per player: inherit, -1 unlimited, 0 automatic, or positive.");
        Integer concurrentLoad = doc.overrideInt("chunks.concurrent-loads", null, -1, 1024, "Per player: inherit, -1 unlimited, 0 automatic, or positive.");
        WorldTuning defaults = loadWorld(doc, "world-defaults", WorldTuning.INHERIT);
        if (!doc.yaml.contains("worlds", true)) {
            doc.yaml.createSection("worlds");
            doc.yaml.setComments("worlds", List.of("Overrides by dimension key, e.g. minecraft:overworld or minecraft:the_nether. Missing/inherit values use world-defaults."));
            doc.changed = true;
        }
        if (!doc.yaml.isConfigurationSection("worlds")) throw doc.invalid("worlds", "a mapping of dimension keys to settings");
        Map<String, WorldTuning> worlds = new LinkedHashMap<>();
        for (String key : doc.yaml.getConfigurationSection("worlds").getKeys(false)) {
            if (!key.matches("[a-z0-9_-]+:[a-z0-9_/-]+")) throw doc.invalid("worlds." + key, "a dimension key such as minecraft:overworld");
            worlds.put(key, loadWorld(doc, "worlds." + key, defaults));
        }
        Algorithms algorithms = new Algorithms(
            doc.bool("experimental.algorithms.fast-palette", false, "EXPERIMENTAL: Lithium identity hash palettes; packet/storage format stays upstream. Restart required."),
            doc.bool("experimental.algorithms.compact-storage", false, "EXPERIMENTAL: compact uniform decoded palettes. Restart required."),
            doc.bool("experimental.algorithms.combined-heightmap", false, "EXPERIMENTAL: share block searches across four heightmaps. Restart required."),
            doc.bool("experimental.algorithms.varint-writes", false, "EXPERIMENTAL: bulk writes for standard VarInt/VarLong encodings. Restart required."));
        Mechanics mechanics = new Mechanics(
            doc.bool("experimental.mechanics.equipment-tracking", false, "EXPERIMENTAL: track ItemStack/component mutations before equipment scans. Owner-thread gameplay remains synchronous."),
            doc.bool("experimental.mechanics.recipe-lookup", false, "EXPERIMENTAL: collect matching recipes without a stream; preserves last-match priority and matcher order."),
            doc.bool("experimental.mechanics.live-collision-context", false, "EXPERIMENTAL BEHAVIOR: entity contexts read current movement/held item instead of capturing them. Explicit placement/position contexts retain captured values."));
        Worldgen worldgen = new Worldgen(
            doc.integer("experimental.worldgen.end-biome-cache-entries", 0, 0, 65536, "0 OFF. Per-thread/source bounded cache for native End-island density only; custom samplers use upstream."),
            doc.bool("experimental.worldgen.noise-kernel", false, "EXPERIMENTAL: C2ME flattened gradient kernel; preserve upstream coordinate rounding and derivative path."),
            doc.bool("experimental.worldgen.beardifier-math", false, "EXPERIMENTAL: simplify structure bury-distance math. Worldgen parity must be verified before enabling."));
        Async async = new Async(
            doc.bool("experimental.async.chunk-sending", false, "EXPERIMENTAL: serialize copied chunk sections on bounded workers. Anti-Xray modification uses native serialization; events stay on the owner thread."),
            doc.integer("experimental.async.chunk-workers", 1, 1, 32, "Worker request, capped by the remaining Quantum worker budget. Zero available workers uses native serialization."),
            doc.integer("experimental.async.chunk-queue-capacity", 64, 1, 1024, "Maximum waiting section snapshots. Full queue falls back synchronously without dropping or reordering packets."));
        Network network = new Network(
            doc.bool("experimental.network.visibility-lookup", false, "EXPERIMENTAL: specialized visibility map and empty-map shortcut; self-visibility and plugin inversion rules stay unchanged."),
            doc.bool("experimental.network.unchanged-movement", false, "EXPERIMENTAL: skip distance math when the immutable movement vector is the same object. Packet cadence is unchanged."),
            doc.bool("experimental.network.lazy-flush", false, "EXPERIMENTAL: avoid waking supported Netty event loops for non-flush sends. Native flush and packet order are retained; other event loops use execute."));
        return new Performance(enabled, disableSpark, generate, load, send, concurrentGenerate, concurrentLoad, defaults, Map.copyOf(worlds), algorithms, mechanics, worldgen, async, network);
    }

    private static WorldTuning loadWorld(Document d, String p, WorldTuning parent) throws InvalidConfigurationException {
        Hopper h = parent.hopper();
        ArmorStands a = parent.armorStands();
        AntiXray x = parent.antiXray();
        Blocks b = parent.blocks();
        return new WorldTuning(
            new Hopper(
                d.overrideInt(p + ".hopper.transfer-ticks", h.transferTicks(), 1, 1200, "inherit or transfer interval; vanilla-like 8. Higher values slow farms."),
                d.overrideInt(p + ".hopper.check-ticks", h.checkTicks(), 1, 1200, "inherit or idle check interval; vanilla-like 1. Higher values delay pickups."),
                d.overrideInt(p + ".hopper.amount", h.amount(), 1, 64, "inherit or items per transfer; vanilla-like 1. Changes sorter/farm timing."),
                d.overrideBool(p + ".hopper.cooldown-when-full", h.cooldownWhenFull(), "inherit or true/false. Native Paper cooldown for full hoppers."),
                d.overrideBool(p + ".hopper.disable-move-event", h.disableMoveEvent(), "inherit or true/false. True bypasses InventoryMoveItemEvent and may break protection/sorting plugins."),
                d.overrideBool(p + ".hopper.ignore-occluding-blocks", h.ignoreOccludingBlocks(), "inherit or true/false. True changes hopper collection through solid blocks.")),
            new ArmorStands(
                d.overrideBool(p + ".armor-stands.tick", a.tick(), "inherit or true/false. False freezes stand ticking/physics; intended for static decorations."),
                d.overrideBool(p + ".armor-stands.collision-lookups", a.collisionLookups(), "inherit or true/false. False skips armor stand entity collision searches."),
                d.overrideBool(p + ".armor-stands.movement", a.movement(), "inherit or true/false. False disables Purpur movement ticking, including gravity movement."),
                d.overrideBool(p + ".armor-stands.water-movement", a.waterMovement(), "inherit or true/false. False disables fluid interaction for armor stands."),
                d.overrideBool(p + ".armor-stands.gravity", a.gravity(), "inherit/true preserves normal gravity and per-entity NoGravity. False suppresses acceleration without rewriting entity NBT; existing momentum may remain.")),
            new AntiXray(
                d.overrideBool(p + ".anti-xray.enabled", x.enabled(), "inherit or true/false. Ore obfuscation adds work; this is protection, not a TPS optimization."),
                d.overrideInt(p + ".anti-xray.engine-mode", x.engineMode(), 1, 3, "inherit or 1 hide, 2 obfuscate, 3 obfuscate layer. Applied before packet controller construction."),
                d.overrideInt(p + ".anti-xray.max-block-height", x.maxBlockHeight(), -2032, 2032, "inherit or upper obfuscation height. Paper rounds to a section boundary."),
                d.overrideInt(p + ".anti-xray.update-radius", x.updateRadius(), 0, 2, "inherit or 0..2 block reveal update radius."),
                d.overrideBool(p + ".anti-xray.lava-obscures", x.lavaObscures(), "inherit or true/false. Treat lava as an obstruction."),
                d.overrideBool(p + ".anti-xray.use-permission", x.usePermission(), "inherit or true/false. Enable the native anti-xray bypass permission. Block lists remain in paper-world.yml.")),
            new Blocks(
                d.overrideBool(p + ".blocks.block-entity-ticking", b.blockEntityTicking(), "inherit or true/false. False stops hoppers, furnaces and other block entities; unsuitable for survival."),
                d.overrideInt(p + ".blocks.max-scheduled-block-ticks", b.maxBlockTicks(), 1, 1000000, "inherit or per-tick scheduled block budget. Native default 65536; lower values defer mechanics."),
                d.overrideInt(p + ".blocks.max-scheduled-fluid-ticks", b.maxFluidTicks(), 1, 1000000, "inherit or per-tick scheduled fluid budget. Native default 65536; lower values delay fluid flow.")),
            d.redstone(p + ".redstone.implementation", parent.redstone()),
            d.overrideInt(p + ".saving.max-chunks-per-tick", parent.autoSaveChunks(), 1, 10000, "inherit or positive chunk autosave budget. Lower values spread saves over more ticks; autosave interval stays upstream-controlled."),
            d.overrideBool(p + ".experimental.waypoint-collections", parent.waypointCollections(), "EXPERIMENTAL, default OFF. Reduce waypoint collection allocation without changing locator visibility or update frequency. inherit/false uses upstream; safe/compatibility mode bypasses this."));
    }

    private static final class Document {
        private final Path path;
        private final YamlConfiguration yaml = new YamlConfiguration();
        private boolean changed;

        private Document(Path path) throws IOException, InvalidConfigurationException {
            this.path = path;
            this.yaml.options().parseComments(true);
            if (Files.exists(path)) {
                if (Files.size(path) > 1_048_576) throw this.invalid("<document>", "at most 1 MiB");
                String contents = Files.readString(path);
                LoaderOptions limits = new LoaderOptions();
                limits.setAllowDuplicateKeys(false);
                limits.setMaxAliasesForCollections(0);
                limits.setCodePointLimit(262144);
                limits.setNestingDepthLimit(30);
                try {
                    Object parsed = new Yaml(new SafeConstructor(limits)).load(contents);
                    if (parsed != null && !(parsed instanceof Map)) throw this.invalid("<document>", "a YAML mapping");
                    this.validateTree(parsed, "");
                    this.yaml.loadFromString(contents);
                } catch (YAMLException e) {
                    throw new InvalidConfigurationException(path + ": invalid or unsupported YAML", e);
                }
            } else {
                this.yaml.options().setHeader(List.of("QuantumSpigot. All settings require a restart.",
                    "Explicit native tuning only; no asynchronous gameplay offload or adaptive simulation."));
            }
            this.integer("config-version", 1, 1, 1, "Configuration schema; newer versions must not be downgraded.");
        }

        private void validateTree(Object value, String prefix) throws InvalidConfigurationException {
            if (value instanceof Map<?, ?> mapping) {
                for (var entry : mapping.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || key.equals("==") || key.contains(".")) {
                        throw this.invalid(prefix, "string mapping keys without serialization markers or dots");
                    }
                    String path = prefix.isEmpty() ? key : prefix + "." + key;
                    if (entry.getValue() == null) throw this.invalid(path, "an explicit non-null value");
                    this.validateTree(entry.getValue(), path);
                }
            } else if (value instanceof List<?> list) {
                for (Object element : list) this.validateTree(element, prefix);
            }
        }

        private Object value(String key, Object fallback, String comment) throws InvalidConfigurationException {
            // A malformed parent must not be silently replaced by a mapping.
            int dot = key.indexOf('.');
            while (dot != -1) {
                String parent = key.substring(0, dot);
                if (this.yaml.contains(parent, true) && !this.yaml.isConfigurationSection(parent)) {
                    throw this.invalid(parent, "a YAML mapping");
                }
                dot = key.indexOf('.', dot + 1);
            }
            if (!this.yaml.contains(key, true)) {
                this.yaml.set(key, fallback);
                this.yaml.setComments(key, List.of(comment));
                this.changed = true;
            }
            return this.yaml.get(key);
        }

        private String string(String key, String fallback, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, fallback, comment);
            if (!(value instanceof String text)) throw this.invalid(key, "a string");
            return text;
        }

        private boolean bool(String key, boolean fallback, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, fallback, comment);
            if (!(value instanceof Boolean flag)) throw this.invalid(key, "true or false");
            return flag;
        }

        private int integer(String key, int fallback, int min, int max, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, fallback, comment);
            if (!(value instanceof Integer number) || number < min || number > max) {
                throw this.invalid(key, "an integer in [" + min + ", " + max + "]");
            }
            return number;
        }

        private int autoPositive(String key, int fallback, String comment) throws InvalidConfigurationException {
            int value = this.integer(key, fallback, -1, 1024, comment);
            if (value == 0) throw this.invalid(key, "-1 or an integer in [1, 1024]");
            return value;
        }

        private double number(String key, double fallback, double min, double max, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, fallback, comment);
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < min || number.doubleValue() > max) {
                throw this.invalid(key, "a finite number in [" + min + ", " + max + "]");
            }
            return number.doubleValue();
        }

        private Boolean overrideBool(String key, Boolean parent, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, "inherit", comment);
            if ("inherit".equals(value)) return parent;
            if (!(value instanceof Boolean flag)) throw this.invalid(key, "inherit, true or false");
            return flag;
        }

        private Integer overrideInt(String key, Integer parent, int min, int max, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, "inherit", comment);
            if ("inherit".equals(value)) return parent;
            if (!(value instanceof Integer number) || number < min || number > max) throw this.invalid(key, "inherit or an integer in [" + min + ", " + max + "]");
            return number;
        }

        private Double rate(String key, String comment) throws InvalidConfigurationException {
            Object value = this.value(key, "inherit", comment);
            if ("inherit".equals(value)) return null;
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || (number.doubleValue() != -1 && number.doubleValue() <= 0) || number.doubleValue() > 1000000) {
                throw this.invalid(key, "inherit, -1 or a finite positive rate <= 1000000");
            }
            return number.doubleValue();
        }

        private String redstone(String key, String parent) throws InvalidConfigurationException {
            String value = this.string(key, "inherit", "inherit, VANILLA, EIGENCRAFT or ALTERNATE_CURRENT. Optimized engines can change update order; check technical builds.");
            if (value.equalsIgnoreCase("inherit")) return parent;
            value = value.toUpperCase(Locale.ROOT);
            if (!List.of("VANILLA", "EIGENCRAFT", "ALTERNATE_CURRENT").contains(value)) throw this.invalid(key, "inherit, VANILLA, EIGENCRAFT or ALTERNATE_CURRENT");
            return value;
        }

        private InvalidConfigurationException invalid(String key, String expected) {
            return new InvalidConfigurationException(this.path + ": " + key + " expected " + expected);
        }

        private void saveDefaults() throws IOException {
            if (!this.changed) return;
            Files.createDirectories(this.path.getParent());
            Path temporary = Files.createTempFile(this.path.getParent(), ".quantum-", ".tmp");
            try {
                Files.writeString(temporary, this.yaml.saveToString());
                if (Files.exists(this.path)) {
                    // A unique backup preserves operator content before adding new keys.
                    Path backup = Files.createTempFile(this.path.getParent(), this.path.getFileName() + ".", ".bak");
                    Files.copy(this.path, backup, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
