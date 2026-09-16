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
}
