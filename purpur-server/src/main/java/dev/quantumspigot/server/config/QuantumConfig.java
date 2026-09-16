package dev.quantumspigot.server.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Validated once at startup; no YAML access from the tick path. */
public record QuantumConfig(Profile profile, boolean safeMode, Diagnostics diagnostics, Workers workers) {
    public enum Profile { COMPATIBILITY, BALANCED, CUSTOM }

    public record Diagnostics(boolean enabled, int historySeconds, double lagThresholdMs, int consecutiveTicks,
                              double pluginThresholdMs, int warningIntervalSeconds, boolean saveReports,
                              int reportQueueCapacity, int retainedReports) {}

    public record Workers(int mainReserve, int jvmReserve, int maximumTotal) {}

    public static QuantumConfig load(Path directory, boolean safeMode) throws IOException, InvalidConfigurationException {
        Document global = new Document(directory.resolve("quantum-global.yml"));
        Document diagnostics = new Document(directory.resolve("quantum-diagnostics.yml"));
        Document threading = new Document(directory.resolve("quantum-threading.yml"));
        Profile configured;
        String profile = global.string("profile.active", "balanced", "Phase 1: compatibility, balanced, or custom. All preserve Purpur simulation.");
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
                threading.autoPositive("threading.maximum-total-workers", -1, "-1: automatic. Quantum budget only; does not resize upstream workers.")));
        // Validate every file before changing any of them.
        global.saveDefaults();
        diagnostics.saveDefaults();
        threading.saveDefaults();
        return result;
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
                this.yaml.options().setHeader(List.of("QuantumSpigot Phase 1. All settings require a restart.",
                    "No gameplay offload or adaptive simulation is enabled by this milestone."));
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
