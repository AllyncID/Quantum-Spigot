package dev.quantumspigot.server;

import com.destroystokyo.paper.util.VersionFetcher;
import net.kyori.adventure.text.Component;

public final class QuantumVersionFetcher implements VersionFetcher {
    @Override
    public long getCacheTime() { return 720000; }

    @Override
    public Component getVersionMessage() {
        return Component.text("QuantumSpigot development build; upstream Purpur/Paper. No Quantum update service is configured.");
    }
}
