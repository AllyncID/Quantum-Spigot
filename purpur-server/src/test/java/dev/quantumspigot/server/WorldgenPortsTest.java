package dev.quantumspigot.server;

import dev.quantumspigot.server.config.QuantumConfig;
import java.lang.reflect.Method;
import java.util.Random;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class WorldgenPortsTest {
    @Test
    void endCacheIsBoundedSeedIsolatedAndBypassesCustomYDependentDensity() throws Exception {
        var constructor = net.minecraft.world.level.biome.TheEndBiomeSource.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var holders = java.util.stream.IntStream.range(0, 5).mapToObj(i -> net.minecraft.core.Holder.direct(mock(net.minecraft.world.level.biome.Biome.class))).toArray();
        var source = (net.minecraft.world.level.biome.TheEndBiomeSource) constructor.newInstance(holders);
        var zero = net.minecraft.world.level.levelgen.DensityFunctions.zero();
        var samplers = java.util.stream.LongStream.of(17, 987654321).mapToObj(seed -> new net.minecraft.world.level.biome.Climate.Sampler(
            zero, zero, zero, net.minecraft.world.level.levelgen.DensityFunctions.endIslands(seed), zero, zero, java.util.List.of())).toList();
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            for (int i = 0; i < 100; i++) {
                int x = i % 2 == 0 ? 512 + i * 59 : -7000000 + i * 99, z = i * -37;
                for (var sampler : samplers) {
                    runtime.when(QuantumRuntime::worldgen).thenReturn(QuantumConfig.Worldgen.DISABLED);
                    var expected = source.getNoiseBiome(x, -32, z, sampler);
                    runtime.when(QuantumRuntime::worldgen).thenReturn(new QuantumConfig.Worldgen(2, false, false));
                    assertSame(expected, source.getNoiseBiome(x, 0, z, sampler));
                    assertSame(expected, source.getNoiseBiome(x, 96, z, sampler));
                }
            }
            for (int i = 0; i < 10; i++) source.getNoiseBiome(900 + i * 8, 0, 700, samplers.getFirst());
            var localField = source.getClass().getDeclaredField("quantumBiomeCache");
            localField.setAccessible(true);
            Object cache = ((ThreadLocal<?>) localField.get(source)).get();
            var mapField = cache.getClass().getDeclaredField("biomes");
            mapField.setAccessible(true);
            assertEquals(2, ((java.util.Map<?, ?>) mapField.get(cache)).size());
            var custom = mock(net.minecraft.world.level.levelgen.DensityFunction.class);
            when(custom.compute(any())).thenAnswer(call -> ((net.minecraft.world.level.levelgen.DensityFunction.FunctionContext) call.getArgument(0)).blockY() > 0 ? 1.0 : -1.0);
            var sampler = new net.minecraft.world.level.biome.Climate.Sampler(zero, zero, zero, custom, zero, zero, java.util.List.of());
            assertSame(holders[1], source.getNoiseBiome(1000, 10, 1000, sampler));
            assertSame(holders[3], source.getNoiseBiome(1000, -10, 1000, sampler));
            verify(custom, times(2)).compute(any());
        }
    }

    @Test
    void noiseAndBuryMathMatchUpstreamBitForBit() throws Exception {
        Method bury = Beardifier.class.getDeclaredMethod("getBuryContribution", double.class, double.class, double.class);
        bury.setAccessible(true);
        var random = new Random(728194);
        try (var runtime = mockStatic(QuantumRuntime.class)) {
            for (int seed = 0; seed < 4; seed++) {
                var noise = new ImprovedNoise(RandomSource.create(seed));
                for (int i = 0; i < 1000; i++) {
                    double x = (random.nextDouble() - .5) * 60000000;
                    double y = (random.nextDouble() - .5) * 4096;
                    double z = (random.nextDouble() - .5) * 60000000;
                    double scale = i % 2 == 0 ? 0 : .125, fudge = i % 3 == 0 ? -.5 : .25;
                    runtime.when(QuantumRuntime::worldgen).thenReturn(QuantumConfig.Worldgen.DISABLED);
                    double expected = noise.noise(x, y, z, scale, fudge);
                    double bx = i % 13 - 6, by = i % 11 - 5, bz = i % 7 - 3;
                    double expectedBury = (double) bury.invoke(null, bx, by, bz);
                    runtime.when(QuantumRuntime::worldgen).thenReturn(new QuantumConfig.Worldgen(0, true, true));
                    assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(noise.noise(x, y, z, scale, fudge)), "noise seed=" + seed + " case=" + i);
                    assertEquals(Double.doubleToLongBits(expectedBury), Double.doubleToLongBits((double) bury.invoke(null, bx, by, bz)), "bury case=" + i);
                }
            }
        }
    }
}
