package dev.quantumspigot.server.commands;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class QuantumMessages {
    private QuantumMessages() {}
    static Component header(String title) {
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("Quantum", NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY)).append(Component.text(title, NamedTextColor.WHITE));
    }
    static Component label(String text) { return Component.text(text, NamedTextColor.GRAY); }
    static Component value(Object value) { return Component.text(String.valueOf(value), NamedTextColor.WHITE); }
    static Component pair(String name, Object value) { return label(name + ": ").append(value(value)); }
    static Component separator() { return Component.text("  |  ", NamedTextColor.DARK_GRAY); }
    static Component note(String text) { return Component.text(text, NamedTextColor.GRAY); }
    static Component warning(String text) { return Component.text(text, NamedTextColor.YELLOW); }
    static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    static Component tps(double value) {
        if (!Double.isFinite(value) || value < 0) return warning("unavailable");
        return Component.text(decimal(value), value >= 19.9 ? NamedTextColor.GREEN : value >= 18 ? NamedTextColor.YELLOW : NamedTextColor.RED);
    }
    static Component mspt(double value) {
        if (!Double.isFinite(value) || value < 0) return warning("unavailable");
        return Component.text(decimal(value), value < 40 ? NamedTextColor.GREEN : value < 50 ? NamedTextColor.YELLOW : NamedTextColor.RED);
    }
    static String memory(long bytes) { return bytes < 0 ? "unavailable" : String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0); }
    static Component style(Component message, boolean colored) {
        return colored ? message : Component.text(PlainTextComponentSerializer.plainText().serialize(message));
    }
}
