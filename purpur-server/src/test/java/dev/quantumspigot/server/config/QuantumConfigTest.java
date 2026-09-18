package dev.quantumspigot.server.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuantumConfigTest {
    @TempDir Path directory;

    @Test
    void defaultsAndSafeModePreserveOperatorProfileOnDisk() throws Exception {
        var config = QuantumConfig.load(this.directory, true);
        assertEquals(QuantumConfig.Profile.COMPATIBILITY, config.profile());
        assertEquals(900, config.diagnostics().historySeconds());
        assertFalse(config.diagnostics().worldTimings());
        assertFalse(config.diagnostics().pluginAttribution());
        assertEquals(16, config.diagnostics().pluginSampleEvery());
        assertTrue(config.coloredCommands());
        assertFalse(config.performanceActive());
        assertEquals(QuantumConfig.Algorithms.DISABLED, config.algorithms());
        assertEquals(QuantumConfig.Async.DISABLED, config.async());
        assertEquals(QuantumConfig.WorldTuning.INHERIT, config.worldTuning("minecraft:overworld"));
        assertEquals(QuantumConfig.Profile.BALANCED, QuantumConfig.load(this.directory, false).profile());
        assertTrue(Files.readString(this.directory.resolve("quantum-global.yml")).contains("#"));
    }

    @Test
    void rejectsMalformedFileWithoutWritingAnyDefaults() throws Exception {
        Path file = this.directory.resolve("quantum-diagnostics.yml");
        String original = "diagnostics:\n  enabled: 'true'\n";
        Files.writeString(file, original);
        assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(this.directory.resolve("quantum-global.yml")));
    }

    @Test
    void rejectsScalarSectionsAndFutureVersions() throws Exception {
        Path file = this.directory.resolve("quantum-global.yml");
        Files.writeString(file, "profile: false\n");
        assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
        Files.writeString(file, "config-version: 2\n");
        assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
    }

    @Test
    void rejectsZeroWorkersAndNonFiniteThreshold() throws Exception {
        Path threading = this.directory.resolve("quantum-threading.yml");
        Files.writeString(threading, "threading:\n  maximum-total-workers: 0\n");
        assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
        Files.delete(threading);
        Files.writeString(this.directory.resolve("quantum-diagnostics.yml"), "diagnostics:\n  plugin-task-threshold-ms: .NaN\n");
        assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
    }

    @Test
    void validatesOptionalWorldAndPluginAttributionSettings() throws Exception {
        Path file = this.directory.resolve("quantum-diagnostics.yml");
        Files.writeString(file, "diagnostics:\n  world-timings: true\n  plugin-attribution:\n    enabled: true\n    sample-every: 1\n");
        var config = QuantumConfig.load(this.directory, false).diagnostics();
        assertTrue(config.worldTimings());
        assertTrue(config.pluginAttribution());
        assertEquals(1, config.pluginSampleEvery());
        for (String invalid : new String[] {"0", "1025", "false", "1.5"}) {
            Files.writeString(file, "diagnostics:\n  plugin-attribution:\n    sample-every: " + invalid + "\n");
            assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
        }
    }

    @Test
    void rejectsDuplicateKeysNullsAndSerializedObjectsWithoutOverwriting() throws Exception {
        Path file = this.directory.resolve("quantum-global.yml");
        for (String malformed : new String[] {
            "config-version: 1\nconfig-version: 2\n",
            "profile:\n  active: null\n",
            "object:\n  ==: org.bukkit.inventory.ItemStack\n",
            "profile: [unfinished\n"
        }) {
            Files.writeString(file, malformed);
            assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false));
            assertEquals(malformed, Files.readString(file));
        }
    }

    @Test
    void migrationPreservesValuesCommentsAndBackup() throws Exception {
        Path file = this.directory.resolve("quantum-global.yml");
        String original = "# operator comment\nprofile:\n  active: custom\noperator-key: keep-me\n";
        Files.writeString(file, original);
        assertEquals(QuantumConfig.Profile.CUSTOM, QuantumConfig.load(this.directory, false).profile());
        String updated = Files.readString(file);
        assertTrue(updated.contains("operator comment"));
        assertTrue(updated.contains("keep-me"));
        try (var files = Files.list(this.directory)) {
            Path backup = files.filter(path -> path.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
    }

    @Test
    void performanceOverridesInheritPerWorldAndSafeModeBypassesWithoutRewriting() throws Exception {
        Path file = this.directory.resolve("quantum-performance.yml");
        Files.writeString(file, """
            enabled: true
            experimental:
              async:
                chunk-sending: true
                chunk-workers: 2
                chunk-queue-capacity: 16
              algorithms:
                fast-palette: true
                compact-storage: true
                combined-heightmap: true
                varint-writes: true
            chunks:
              generate-per-second: 20.0
              concurrent-generates: 1
            world-defaults:
              experimental:
                waypoint-collections: true
              hopper:
                check-ticks: 4
              armor-stands:
                gravity: false
              redstone:
                implementation: alternate_current
            worlds:
              minecraft:the_nether:
                experimental:
                  waypoint-collections: false
                hopper:
                  check-ticks: 8
                armor-stands:
                  gravity: true
            """);
        var config = QuantumConfig.load(this.directory, false);
        assertTrue(config.performanceActive());
        assertEquals(new QuantumConfig.Algorithms(true, true, true, true), config.algorithms());
        assertEquals(true, config.worldTuning("minecraft:overworld").waypointCollections());
        assertEquals(false, config.worldTuning("minecraft:the_nether").waypointCollections());
        assertEquals(4, config.worldTuning("minecraft:overworld").hopper().checkTicks());
        assertEquals(8, config.worldTuning("minecraft:the_nether").hopper().checkTicks());
        assertNull(config.worldTuning("minecraft:the_end").hopper().amount());
        assertEquals("ALTERNATE_CURRENT", config.worldTuning("minecraft:the_end").redstone());
        assertEquals(false, config.worldTuning("minecraft:overworld").armorStands().gravity());
        assertEquals(true, config.worldTuning("minecraft:the_nether").armorStands().gravity());
        String saved = Files.readString(file);
        var safe = QuantumConfig.load(this.directory, true);
        assertFalse(safe.performanceActive());
        assertEquals(QuantumConfig.Algorithms.DISABLED, safe.algorithms());
        assertEquals(QuantumConfig.WorldTuning.INHERIT, safe.worldTuning("minecraft:the_nether"));
        assertEquals(saved, Files.readString(file));
        Files.writeString(this.directory.resolve("quantum-global.yml"), "profile:\n  active: compatibility\n");
        assertFalse(QuantumConfig.load(this.directory, false).performanceActive());
    }

    @Test
    void invalidPerformanceSettingsDoNotWriteOtherDefaults() throws Exception {
        Path file = this.directory.resolve("quantum-performance.yml");
        for (String invalid : new String[] {
            "chunks:\n  generate-per-second: 0\n", "chunks:\n  send-per-second: .inf\n",
            "world-defaults:\n  hopper:\n    transfer-ticks: 0\n",
            "world-defaults:\n  armor-stands:\n    gravity: 'false'\n",
            "world-defaults:\n  experimental:\n    waypoint-collections: 'true'\n",
            "world-defaults:\n  anti-xray:\n    engine-mode: 4\n",
            "world-defaults:\n  redstone:\n    implementation: turbo\n",
            "worlds: [minecraft:overworld]\n", "worlds:\n  world: {}\n"
        }) {
            Files.writeString(file, invalid);
            assertThrows(InvalidConfigurationException.class, () -> QuantumConfig.load(this.directory, false), invalid);
            assertEquals(invalid, Files.readString(file));
            assertFalse(Files.exists(this.directory.resolve("quantum-global.yml")));
        }
    }
}
