package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class EquipmentTrackingTest {
    @Test
    void countComponentReplacementAndClearKeepSubscriptionsCorrect() {
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::mechanics).thenReturn(new QuantumConfig.Mechanics(true, false, false));
            var equipment = new EntityEquipment();
            var stack = new ItemStack(Items.STONE, 10);
            equipment.set(EquipmentSlot.MAINHAND, stack);
            for (int count = 9; count > 0; count--) {
                equipment.lithium$onEquipmentChangesSent();
                stack.setCount(count);
                assertTrue(equipment.lithium$hasUnsentEquipmentChanges(), "count change " + count);
            }
            equipment.lithium$onEquipmentChangesSent();
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges());
            equipment.lithium$onEquipmentChangesSent();
            stack.setCount(0);
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges());
            equipment.lithium$onEquipmentChangesSent();
            stack.setCount(2); // A plugin can grow the original stack without replacing the equipment slot.
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges());
            equipment.lithium$onEquipmentChangesSent();
            stack.setCount(1);
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges(), "resurrected stack must remain subscribed");
            equipment.lithium$onEquipmentChangesSent();
            stack.lithium$unsubscribeWithData(equipment, 7);
            stack.setCount(-1);
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges(), "wrong slot must not unsubscribe equipment");
            equipment.lithium$onEquipmentChangesSent();
            stack.setCount(1);
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges());
            var second = new ItemStack(Items.STONE, 3);
            equipment.set(EquipmentSlot.MAINHAND, second);
            equipment.clear();
            equipment.lithium$onEquipmentChangesSent();
            second.setCount(2);
            assertFalse(equipment.lithium$hasUnsentEquipmentChanges(), "cleared stack must be unsubscribed");
            equipment.set(EquipmentSlot.OFFHAND, second);
            var target = new EntityEquipment();
            target.setAll(equipment);
            target.lithium$onEquipmentChangesSent();
            equipment.lithium$onEquipmentChangesSent();
            second.setCount(1);
            assertTrue(target.lithium$hasUnsentEquipmentChanges());
            assertTrue(equipment.lithium$hasUnsentEquipmentChanges());
            equipment.clear(); target.clear();
            second.setCount(0);
        }
    }
}
