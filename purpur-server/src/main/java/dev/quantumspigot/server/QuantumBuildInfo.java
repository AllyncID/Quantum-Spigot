package dev.quantumspigot.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Provenance is embedded by the build, never fetched during server startup. */
public final class QuantumBuildInfo {
    private static final Properties UPSTREAM = load();

    private QuantumBuildInfo() {}

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = QuantumBuildInfo.class.getResourceAsStream("/quantum-upstream.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read bundled Quantum upstream provenance", e);
        }
        return properties;
    }

    public static String upstream() {
        return "Purpur " + UPSTREAM.getProperty("purpur.commit", "unknown")
            + " / Paper " + UPSTREAM.getProperty("paper.commit", "unknown");
    }
}
