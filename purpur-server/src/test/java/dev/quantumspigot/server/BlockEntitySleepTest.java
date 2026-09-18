package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import dev.quantumspigot.server.inventory.TrackedItemList;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class BlockEntitySleepTest {
    private static final QuantumConfig.Mechanics ENABLED = new QuantumConfig.Mechanics(false, false, false, false, true);

    private static TickingBlockEntity bound(BlockEntity entity) throws Exception {
        Class<?> type = Class.forName("net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (TickingBlockEntity) constructor.newInstance(mock(LevelChunk.class), entity, mock(BlockEntityTicker.class));
    }

    private static LevelChunk.RebindableTickingBlockEntityWrapper bind(BlockEntity entity) throws Exception {
        var constructor = LevelChunk.RebindableTickingBlockEntityWrapper.class.getDeclaredConstructor(TickingBlockEntity.class);
        constructor.setAccessible(true);
        var wrapper = constructor.newInstance(bound(entity));
        entity.quantumSetTickWrapper(wrapper);
        return wrapper;
    }

    @Test
    void idleMachinesSleepAndInventoryTimerOrStateChangesWakeThem() throws Exception {
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::mechanics).thenReturn(ENABLED);
            var level = mock(ServerLevel.class);
            var worldConfig = Level.class.getField("purpurConfig");
            worldConfig.setAccessible(true);
            worldConfig.set(level, mock(org.purpurmc.purpur.PurpurWorldConfig.class));
            var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
            var furnaceTicker = bind(furnace);
            AbstractFurnaceBlockEntity.serverTick(level, BlockPos.ZERO, furnace.getBlockState(), furnace);
            assertTrue(furnaceTicker.quantumIsSleeping());
            furnace.getContents().set(1, new ItemStack(Items.COAL)); // Direct native list mutation also wakes.
            assertFalse(furnaceTicker.quantumIsSleeping());
            AbstractFurnaceBlockEntity.serverTick(level, BlockPos.ZERO, furnace.getBlockState(), furnace);
            assertTrue(furnaceTicker.quantumIsSleeping());
            furnace.setBlockState(furnace.getBlockState());
            assertFalse(furnaceTicker.quantumIsSleeping());
            furnaceTicker.quantumSetSleeping(furnace, true);
            var furnaceData = AbstractFurnaceBlockEntity.class.getDeclaredField("dataAccess");
            furnaceData.setAccessible(true);
            ((net.minecraft.world.inventory.ContainerData) furnaceData.get(furnace)).set(0, 10);
            assertFalse(furnaceTicker.quantumIsSleeping());
            var liveState = mock(org.bukkit.craftbukkit.block.CraftFurnace.class, CALLS_REAL_METHODS);
            var snapshot = org.bukkit.craftbukkit.block.CraftBlockEntityState.class.getDeclaredField("snapshot");
            snapshot.setAccessible(true);
            snapshot.set(liveState, furnace);
            liveState.snapshotDisabled = true;
            furnaceTicker.quantumSetSleeping(furnace, true);
            liveState.setCookTime((short) 5);
            assertFalse(furnaceTicker.quantumIsSleeping(), "Paper live BlockState setters must wake the actual machine");

            var brewer = new BrewingStandBlockEntity(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState());
            var brewTicker = bind(brewer);
            BrewingStandBlockEntity.serverTick(level, BlockPos.ZERO, brewer.getBlockState(), brewer);
            assertTrue(brewTicker.quantumIsSleeping());
            brewer.setItem(0, new ItemStack(Items.GLASS_BOTTLE));
            assertFalse(brewTicker.quantumIsSleeping());
            brewTicker.quantumSetSleeping(brewer, true);
            var brewerData = BrewingStandBlockEntity.class.getDeclaredField("dataAccess");
            brewerData.setAccessible(true);
            ((net.minecraft.world.inventory.ContainerData) brewerData.get(brewer)).set(0, 10);
            assertFalse(brewTicker.quantumIsSleeping());

            var campfire = new CampfireBlockEntity(BlockPos.ZERO, Blocks.CAMPFIRE.defaultBlockState());
            var campfireTicker = bind(campfire);
            CampfireBlockEntity.cookTick(level, BlockPos.ZERO, campfire.getBlockState(), campfire, mock(RecipeManager.CachedCheck.class));
            assertTrue(campfireTicker.quantumIsSleeping());
            campfire.getItems().set(0, new ItemStack(Items.PORKCHOP));
            assertFalse(campfireTicker.quantumIsSleeping());

            var crafter = new CrafterBlockEntity(BlockPos.ZERO, Blocks.CRAFTER.defaultBlockState());
            var crafterTicker = bind(crafter);
            CrafterBlockEntity.serverTick(level, BlockPos.ZERO, crafter.getBlockState(), crafter);
            assertTrue(crafterTicker.quantumIsSleeping());
            crafter.setCraftingTicksRemaining(3);
            assertFalse(crafterTicker.quantumIsSleeping());
            CrafterBlockEntity.serverTick(level, BlockPos.ZERO, crafter.getBlockState(), crafter);
            assertFalse(crafterTicker.quantumIsSleeping());

            runtime.when(QuantumRuntime::mechanics).thenReturn(QuantumConfig.Mechanics.DISABLED);
            var nativeFurnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
            var nativeTicker = bind(nativeFurnace);
            assertFalse(nativeFurnace.getContents() instanceof TrackedItemList);
            AbstractFurnaceBlockEntity.serverTick(level, BlockPos.ZERO, nativeFurnace.getBlockState(), nativeFurnace);
            assertFalse(nativeTicker.quantumIsSleeping());
        }
    }

    @Test
    void staleEntityCannotSleepReplacementAndUnloadDetachesInventoryListeners() throws Exception {
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::mechanics).thenReturn(ENABLED);
            var old = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
            var replacement = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
            var wrapper = bind(old);
            wrapper.quantumSetSleeping(old, true);
            var rebind = wrapper.getClass().getDeclaredMethod("rebind", TickingBlockEntity.class);
            rebind.setAccessible(true);
            rebind.invoke(wrapper, bound(replacement));
            assertFalse(wrapper.quantumIsSleeping());
            wrapper.quantumSetSleeping(old, true);
            assertFalse(wrapper.quantumIsSleeping());
            replacement.quantumSetTickWrapper(wrapper);
            var stack = new ItemStack(Items.COAL, 2);
            replacement.getContents().set(1, stack);
            var tracked = (TrackedItemList) replacement.getContents();
            replacement.setRemoved();
            long removedRevision = tracked.revision();
            stack.setCount(1);
            assertEquals(removedRevision, tracked.revision());
            replacement.clearRemoved();
            replacement.quantumSetTickWrapper(wrapper);
            wrapper.quantumSetSleeping(replacement, true);
            stack.setCount(2);
            assertFalse(wrapper.quantumIsSleeping());
            stack.setCount(0);
            assertFalse(tracked.canSleep(), "a retained zero-count stack may be resurrected without notification");
            stack.setCount(1);
            assertTrue(tracked.canSleep());
        }
    }

    @Test
    void sleepKeepsNativeRemovalAndMidTickTaskCadence() throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        var tickers = new ArrayList<TickingBlockEntity>();
        for (int i = 0; i < 8; i++) {
            var ticker = mock(TickingBlockEntity.class);
            when(ticker.getPos()).thenReturn(BlockPos.ZERO);
            when(ticker.quantumIsSleeping()).thenReturn(i == 0);
            tickers.add(ticker);
        }
        var tickerField = Level.class.getField("blockEntityTickers");
        tickerField.setAccessible(true);
        tickerField.set(level, tickers);
        var pending = Level.class.getDeclaredField("pendingBlockEntityTickers");
        pending.setAccessible(true); pending.set(level, new ArrayList<>());
        var rate = mock(TickRateManager.class);
        when(rate.runsNormally()).thenReturn(true);
        doReturn(rate).when(level).tickRateManager();
        doReturn(true).when(level).shouldTickBlocksAt(any());
        doNothing().when(level).moonrise$midTickTasks();
        level.tickBlockEntities();
        verify(tickers.getFirst(), never()).tick();
        verify(tickers.getLast()).tick();
        verify(level).moonrise$midTickTasks();
        when(tickers.getFirst().isRemoved()).thenReturn(true);
        level.tickBlockEntities();
        assertEquals(7, tickers.size());
    }
}
