package dev.quantumspigot.server.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantumMessagesTest {
    @Test
    void colorsReflectTickBudgetAndPlainModePreservesReadableLiteralText() {
        assertEquals(NamedTextColor.GREEN, QuantumMessages.tps(20).color());
        assertEquals(NamedTextColor.YELLOW, QuantumMessages.tps(19).color());
        assertEquals(NamedTextColor.RED, QuantumMessages.tps(12).color());
        assertEquals(NamedTextColor.GREEN, QuantumMessages.mspt(39.99).color());
        assertEquals(NamedTextColor.YELLOW, QuantumMessages.mspt(40).color());
        assertEquals(NamedTextColor.RED, QuantumMessages.mspt(50).color());
        assertEquals("unavailable", PlainTextComponentSerializer.plainText().serialize(QuantumMessages.tps(Double.NaN)));
        var rich = QuantumMessages.header("TPS").append(QuantumMessages.pair(" Name", "<red>literal"));
        var plain = QuantumMessages.style(rich, false);
        assertEquals("[Quantum] TPS Name: <red>literal", PlainTextComponentSerializer.plainText().serialize(plain));
        assertNull(plain.color());
        assertEquals("1024.0 MiB", QuantumMessages.memory(1073741824));
        assertEquals("unavailable", QuantumMessages.memory(-1));
    }
}
