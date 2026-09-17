package dev.quantumspigot.server;

import dev.quantumspigot.server.threading.AsyncChunkSerializer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class AsyncChunkSerializerTest {
    @Test
    void workerSerializesPrivateSnapshotAndPublishesCompleteNativeBytes() throws Exception {
        Holder<Biome> biome = Holder.direct(mock(Biome.class));
        var biomes = new IdMapper<Holder<Biome>>();
        biomes.add(biome);
        var section = new LevelChunkSection(
            new PalettedContainer<>(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), null),
            new PalettedContainer<>(biome, Strategy.createForBiomes(biomes), null));
        section.setBlockState(1, 2, 3, Blocks.STONE.defaultBlockState());
        section.setBlockState(4, 5, 6, Blocks.WATER.defaultBlockState());
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(-300, 147));
        when(chunk.getSections()).thenReturn(new LevelChunkSection[] { section });
        var heightmap = mock(Heightmap.class);
        long[] heights = {42};
        when(heightmap.getRawData()).thenReturn(heights);
        when(chunk.getHeightmaps()).thenReturn(Map.of(Heightmap.Types.WORLD_SURFACE, heightmap).entrySet());
        when(chunk.getBlockEntities()).thenReturn(Map.of());
        var expected = new ClientboundLevelChunkPacketData(chunk, null);
        var packet = new ClientboundLevelChunkWithLightPacket(chunk, mock(LevelLightEngine.class));
        assertFalse(packet.isReady());
        section.setBlockState(1, 2, 3, Blocks.DIAMOND_BLOCK.defaultBlockState());
        heights[0] = 99;
        clearInvocations(chunk);
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            worker.submit(packet::quantumFinishSnapshot).get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(chunk);
        assertTrue(packet.isReady());
        assertEquals(-300, packet.getX());
        assertEquals(42, packet.getChunkData().getHeightmaps().get(Heightmap.Types.WORLD_SURFACE)[0]);
        var before = expected.getReadBuffer();
        var after = packet.getChunkData().getReadBuffer();
        try { assertEquals(before, after); }
        finally { before.release(); after.release(); }
    }

    @Test
    void queueIsBoundedBeforeCopyAndDisconnectedJobsAreCancelled() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isConnected()).thenReturn(true);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var packet = mock(ClientboundLevelChunkWithLightPacket.class);
        doAnswer(call -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(packet).quantumFinishSnapshot();
        var disconnected = mock(Connection.class);
        var cancelled = mock(ClientboundLevelChunkWithLightPacket.class);
        try (var serializer = new AsyncChunkSerializer(1, 1, (client, error) -> fail(error))) {
            assertSame(packet, serializer.submit(() -> packet, connection));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertSame(cancelled, serializer.submit(() -> cancelled, disconnected));
            var copied = new AtomicBoolean();
            assertNull(serializer.submit(() -> { copied.set(true); return packet; }, connection));
            assertFalse(copied.get());
            assertEquals(1, serializer.snapshot().queued());
            assertEquals(1, serializer.snapshot().fallbacks());
            release.countDown();
            serializer.close();
            assertEquals(1, serializer.snapshot().cancelled());
            verify(cancelled).quantumDiscardSnapshot();
            verify(cancelled, never()).quantumFinishSnapshot();
            assertNull(serializer.submit(() -> fail("copy after shutdown"), connection));
        } finally { release.countDown(); }
    }

    @Test
    void failureReleasesCapacityAndRequestsDisconnectWithoutPublishing() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isConnected()).thenReturn(true);
        var packet = mock(ClientboundLevelChunkWithLightPacket.class);
        var failure = new IllegalStateException("invalid snapshot");
        doThrow(failure).when(packet).quantumFinishSnapshot();
        var notified = new CountDownLatch(1);
        try (var serializer = new AsyncChunkSerializer(1, 1, (client, error) -> {
            assertSame(connection, client); assertSame(failure, error); notified.countDown();
        })) {
            serializer.submit(() -> packet, connection);
            assertTrue(notified.await(5, TimeUnit.SECONDS));
            serializer.close();
            assertEquals(1, serializer.snapshot().failures());
            verify(packet).quantumDiscardSnapshot();
            verify(packet, never()).setReady(true);
        }
    }
}
