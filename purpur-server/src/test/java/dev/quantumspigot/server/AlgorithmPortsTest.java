package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette;
import net.caffeinemc.mods.lithium.common.world.chunk.heightmap.CombinedHeightmapUpdate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class AlgorithmPortsTest {
    @Test
    void encodingsMatchUpstreamAcrossBoundariesAndRandomInputs() {
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            var random = new Random(728194);
            for (int i = 0; i < 2000; i++) {
                long value = i < 64 ? (1L << i) - 1 : random.nextLong();
                var expected = Unpooled.buffer();
                var actual = Unpooled.buffer();
                try {
                    runtime.when(QuantumRuntime::algorithms).thenReturn(QuantumConfig.Algorithms.DISABLED);
                    VarLong.write(expected, value); VarInt.write(expected, (int) value);
                    runtime.when(QuantumRuntime::algorithms).thenReturn(new QuantumConfig.Algorithms(false, false, false, true));
                    VarLong.write(actual, value); VarInt.write(actual, (int) value);
                    assertEquals(expected, actual, "encoding " + value);
                    assertEquals(value, VarLong.read(actual));
                    assertEquals((int) value, VarInt.read(actual));
                    assertFalse(actual.isReadable());
                } finally { expected.release(); actual.release(); }
            }
        }
    }

    @Test
    void paletteGrowthCopyAndPacketRoundTripPreserveEntries() {
        var ids = new IdMapper<Object>();
        for (int i = 0; i < 128; i++) ids.add(new Object());
        var palette = new LithiumHashPalette<Object>(5);
        for (int i = 0; i < 32; i++) assertEquals(i, palette.idFor(ids.byId(i), (bits, value) -> fail("unexpected resize")));
        var copy = palette.copy();
        assertEquals(5, palette.idFor(ids.byId(5), (bits, value) -> fail("duplicate resize")));
        assertEquals(99, palette.idFor(ids.byId(32), (bits, value) -> { assertEquals(6, bits); return 99; }));
        assertEquals(32, copy.getSize());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            copy.write(buffer, ids);
            var decoded = new HashMapPalette<Object>(5);
            decoded.read(buffer, ids);
            for (int i = 0; i < 32; i++) assertSame(ids.byId(i), decoded.valueFor(i));
        } finally { buffer.release(); }
    }

    @Test
    void uniformCompactionKeepsFastPaletteAndPresetValuesCoherent() {
        var ids = new IdMapper<Object>();
        for (int i = 0; i < 32; i++) ids.add(new Object());
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            runtime.when(QuantumRuntime::algorithms).thenReturn(new QuantumConfig.Algorithms(true, true, false, false));
            var strategy = Strategy.createForBlockStates(ids);
            var source = new PalettedContainer<>(ids.byId(0), strategy, null);
            for (int x = 0; x < 16; x++) source.set(x, 0, 0, ids.byId(x));
            for (int x = 0; x < 16; x++) source.set(x, 0, 0, ids.byId(0));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                source.write(buffer, null, 0);
                var decoded = new PalettedContainer<>(ids.byId(0), strategy, new Object[] {ids.byId(20)});
                decoded.read(buffer);
                for (int i = 0; i < 4096; i++) assertSame(ids.byId(0), decoded.get(i));
                decoded.set(2, 3, 4, ids.byId(20));
                assertSame(ids.byId(20), decoded.get(2, 3, 4));
                assertSame(ids.byId(0), decoded.get(2, 3, 5));
            } finally { buffer.release(); }
        }
    }

    @Test
    void combinedHeightmapsMatchFourNativeUpdates() {
        var chunk = mock(LevelChunk.class);
        when(chunk.getMinY()).thenReturn(-32);
        when(chunk.getHeight()).thenReturn(64);
        BlockState[] column = new BlockState[64];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(call -> column[((BlockPos) call.getArgument(0)).getY() + 32]);
        var types = List.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE);
        var expected = types.stream().map(type -> new Heightmap(chunk, type)).toList();
        var actual = types.stream().map(type -> new Heightmap(chunk, type)).toList();
        var blocks = List.of(Blocks.AIR, Blocks.STONE, Blocks.OAK_LEAVES, Blocks.WATER, Blocks.SHORT_GRASS);
        var random = new Random(728194);
        for (int i = 0; i < 1000; i++) {
            int index = random.nextInt(64), y = index - 32;
            BlockState state = blocks.get(random.nextInt(blocks.size())).defaultBlockState();
            column[index] = state;
            for (var map : expected) map.update(0, y, 0, state);
            CombinedHeightmapUpdate.updateHeightmaps(actual.get(0), actual.get(1), actual.get(2), actual.get(3), chunk, 0, y, 0, state);
            for (int map = 0; map < 4; map++) assertEquals(expected.get(map).getFirstAvailable(0, 0), actual.get(map).getFirstAvailable(0, 0), "update " + i + " map " + map);
        }
    }
}
