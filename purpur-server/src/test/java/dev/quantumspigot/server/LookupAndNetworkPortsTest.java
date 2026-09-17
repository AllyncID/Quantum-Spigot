package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class LookupAndNetworkPortsTest {
    @Test
    void recipeListPreservesMatcherOrderAndSeesRemovalAndReplacement() {
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STONE)));
        var level = mock(Level.class);
        var calls = new ArrayList<Integer>();
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int index = i;
            CraftingRecipe recipe = mock(CraftingRecipe.class);
            when(recipe.getType()).thenReturn(RecipeType.CRAFTING);
            when(recipe.matches(input, level)).thenAnswer(call -> { calls.add(index); return index != 1; });
            holders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("quantum_test_" + i)), recipe));
        }
        var recipes = RecipeMap.create(holders);
        var expected = recipes.getRecipesFor(RecipeType.CRAFTING, input, level).toList();
        assertEquals(List.of(0, 1, 2), calls);
        calls.clear();
        assertEquals(expected, recipes.getRecipesForList(RecipeType.CRAFTING, input, level));
        assertEquals(List.of(0, 1, 2), calls);
        assertSame(holders.get(2), expected.getLast());
        assertTrue(recipes.removeRecipe(holders.get(2).id()));
        assertSame(holders.getFirst(), recipes.getRecipesForList(RecipeType.CRAFTING, input, level).getLast());
        recipes.addRecipe(holders.get(2));
        assertSame(holders.get(2), recipes.getRecipesForList(RecipeType.CRAFTING, input, level).getLast());
        calls.clear();
        assertTrue(recipes.getRecipesForList(RecipeType.CRAFTING, CraftingInput.EMPTY, level).isEmpty());
        assertTrue(calls.isEmpty());
    }

    @Test
    void visibilityRetainsSelfAndDefaultInversionRulesWithEitherMap() throws Exception {
        CraftPlayer player = mock(CraftPlayer.class, CALLS_REAL_METHODS);
        var entity = mock(org.bukkit.entity.Entity.class);
        UUID id = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(id);
        var field = CraftPlayer.class.getDeclaredField("invertedVisibilityEntities");
        field.setAccessible(true);
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            for (boolean enabled : new boolean[] {false, true}) {
                runtime.when(QuantumRuntime::network).thenReturn(new QuantumConfig.Network(enabled, false, false));
                java.util.Map<UUID, Set<?>> inversions = enabled ? new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>() : new HashMap<>();
                field.set(player, inversions);
                assertTrue(player.canSee((org.bukkit.entity.Entity) player));
                for (boolean visible : new boolean[] {false, true}) {
                    when(entity.isVisibleByDefault()).thenReturn(visible);
                    inversions.clear();
                    clearInvocations(entity);
                    assertEquals(visible, player.canSee(entity));
                    if (enabled) verify(entity, never()).getUniqueId();
                    inversions.put(id, Set.of());
                    assertEquals(!visible, player.canSee(entity));
                    inversions.remove(id);
                    assertEquals(visible, player.canSee(entity));
                }
            }
        }
    }

    @Test
    void nonFlushSpecializationFallsBackForFlushesAndAlternateEventLoops() throws Exception {
        var send = Connection.class.getDeclaredMethod("sendPacket", Packet.class, ChannelFutureListener.class, boolean.class);
        send.setAccessible(true);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = mock(Channel.class);
        var supported = mock(SingleThreadEventLoop.class);
        when(connection.channel.eventLoop()).thenReturn(supported);
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::network).thenReturn(new QuantumConfig.Network(false, false, true));
            send.invoke(connection, mock(Packet.class), null, false);
            verify(supported).lazyExecute(any());
            send.invoke(connection, mock(Packet.class), null, true);
            verify(supported).execute(any());
            runtime.when(QuantumRuntime::network).thenReturn(QuantumConfig.Network.DISABLED);
            send.invoke(connection, mock(Packet.class), null, false);
            verify(supported, times(2)).execute(any());
            var alternate = mock(EventLoop.class);
            when(connection.channel.eventLoop()).thenReturn(alternate);
            runtime.when(QuantumRuntime::network).thenReturn(new QuantumConfig.Network(false, false, true));
            send.invoke(connection, mock(Packet.class), null, false);
            verify(alternate).execute(any());
        }
    }

    @Test
    void liveCollisionIsOptInAndNeverOverridesExplicitPosition() {
        var entity = mock(LivingEntity.class);
        when(entity.getMainHandItem()).thenReturn(new ItemStack(Items.STONE));
        when(entity.getY()).thenReturn(2.0);
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::mechanics).thenReturn(QuantumConfig.Mechanics.DISABLED);
            var snapshot = CollisionContext.of(entity);
            runtime.when(QuantumRuntime::mechanics).thenReturn(new QuantumConfig.Mechanics(false, false, true));
            var live = CollisionContext.of(entity);
            var explicit = CollisionContext.withPosition(entity, 5.0);
            when(entity.getMainHandItem()).thenReturn(new ItemStack(Items.DIAMOND));
            when(entity.getY()).thenReturn(-2.0);
            when(entity.isDescending()).thenReturn(true);
            assertTrue(snapshot.isHoldingItem(Items.STONE));
            assertTrue(live.isHoldingItem(Items.DIAMOND));
            assertTrue(explicit.isHoldingItem(Items.STONE));
            assertFalse(snapshot.isDescending());
            assertTrue(live.isDescending());
            assertTrue(snapshot.isAbove(Shapes.block(), BlockPos.ZERO, false));
            assertFalse(live.isAbove(Shapes.block(), BlockPos.ZERO, false));
            assertTrue(explicit.isAbove(Shapes.block(), new BlockPos(0, 3, 0), false));
            assertTrue(CollisionContext.empty().isAbove(Shapes.block(), BlockPos.ZERO, true));
        }
    }
}
