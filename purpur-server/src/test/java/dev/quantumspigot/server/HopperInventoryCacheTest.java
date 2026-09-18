package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import dev.quantumspigot.server.inventory.TrackedItemList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class HopperInventoryCacheTest {
    @Test
    void mutationsAndReloadedListsMatchNativeClassification() throws Exception {
        var classify = HopperBlockEntity.class.getDeclaredMethod("getFullState", HopperBlockEntity.class);
        var replace = HopperBlockEntity.class.getDeclaredMethod("setItems", NonNullList.class);
        classify.setAccessible(true); replace.setAccessible(true);
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::mechanics).thenReturn(QuantumConfig.Mechanics.DISABLED);
            var nativeHopper = new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState());
            runtime.when(QuantumRuntime::mechanics).thenReturn(new QuantumConfig.Mechanics(false, false, false, true, false));
            var cachedHopper = new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState());
            assertInstanceOf(TrackedItemList.class, cachedHopper.getContents());
            var random = new Random(728194);
            for (int i = 0; i < 1000; i++) {
                int slot = random.nextInt(5);
                int action = random.nextInt(4);
                if (action == 0) {
                    int count = random.nextInt(65);
                    nativeHopper.setItem(slot, new ItemStack(Items.STONE, count));
                    cachedHopper.setItem(slot, new ItemStack(Items.STONE, count));
                } else if (action == 1 && nativeHopper.getItem(slot) != ItemStack.EMPTY) {
                    int count = random.nextInt(68) - 1;
                    nativeHopper.getItem(slot).setCount(count);
                    cachedHopper.getItem(slot).setCount(count);
                } else if (action == 2 && nativeHopper.getItem(slot) != ItemStack.EMPTY) {
                    int max = random.nextInt(64) + 1;
                    nativeHopper.getItem(slot).set(DataComponents.MAX_STACK_SIZE, max);
                    cachedHopper.getItem(slot).set(DataComponents.MAX_STACK_SIZE, max);
                } else if (action == 3) {
                    nativeHopper.clearContent(); cachedHopper.clearContent();
                }
                assertEquals(classify.invoke(null, nativeHopper), classify.invoke(null, cachedHopper), "mutation " + i);
                assertEquals(classify.invoke(null, nativeHopper), classify.invoke(null, cachedHopper), "cached repeat " + i);
            }
            var old = (TrackedItemList) cachedHopper.getContents();
            cachedHopper.setItem(0, new ItemStack(Items.STONE, 1));
            ItemStack retained = cachedHopper.getItem(0);
            replace.invoke(cachedHopper, NonNullList.withSize(5, ItemStack.EMPTY));
            long detachedRevision = old.revision();
            retained.setCount(2);
            assertEquals(detachedRevision, old.revision(), "replaced list must unsubscribe");
            assertEquals(0, classify.invoke(null, cachedHopper));
            cachedHopper.setItem(0, new ItemStack(Items.STONE, 64));
            assertEquals(1, classify.invoke(null, cachedHopper));
        }
    }

    @Test
    void sharedStacksKeepSlotSubscriptionsAndZeroCountResurrection() {
        var stack = new ItemStack(Items.STONE, 64);
        var list = new TrackedItemList(List.of(stack, stack));
        assertEquals(2, list.fullState());
        list.set(0, ItemStack.EMPTY);
        assertEquals(1, list.fullState());
        stack.setCount(0);
        assertEquals(0, list.fullState());
        stack.setCount(1);
        assertEquals(1, list.fullState());
        stack.setCount(0);
        assertEquals(0, list.fullState());
        stack.setCount(64);
        list.set(0, stack);
        assertEquals(2, list.fullState());
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        assertEquals(1, list.fullState()); // Native Paper requires count == max, not count >= max.
        list.clear();
        assertEquals(0, list.fullState());
        long cleared = list.revision();
        stack.setCount(1);
        assertEquals(cleared, list.revision());
    }
}
