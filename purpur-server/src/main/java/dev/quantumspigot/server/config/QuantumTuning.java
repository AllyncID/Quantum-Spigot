package dev.quantumspigot.server.config;

import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.EngineMode;
import org.purpurmc.purpur.PurpurWorldConfig;
import org.spigotmc.SpigotWorldConfig;

/** Applies validated overrides once, before the world's controllers/entities capture settings. */
public final class QuantumTuning {
    private QuantumTuning() {}

    public static void applyGlobal(QuantumConfig config, GlobalConfiguration paper) {
        if (!config.performanceActive()) return;
        var p = config.performance();
        paper.chunkLoadingBasic.playerMaxChunkGenerateRate = or(p.generateRate(), paper.chunkLoadingBasic.playerMaxChunkGenerateRate);
        paper.chunkLoadingBasic.playerMaxChunkLoadRate = or(p.loadRate(), paper.chunkLoadingBasic.playerMaxChunkLoadRate);
        paper.chunkLoadingBasic.playerMaxChunkSendRate = or(p.sendRate(), paper.chunkLoadingBasic.playerMaxChunkSendRate);
        paper.chunkLoadingAdvanced.playerMaxConcurrentChunkGenerates = or(p.concurrentGenerates(), paper.chunkLoadingAdvanced.playerMaxConcurrentChunkGenerates);
        paper.chunkLoadingAdvanced.playerMaxConcurrentChunkLoads = or(p.concurrentLoads(), paper.chunkLoadingAdvanced.playerMaxConcurrentChunkLoads);
        if (p.disableBundledSpark()) {
            paper.spark.enabled = false;
            paper.spark.enableImmediately = false;
        }
    }

    public static boolean applyWorld(QuantumConfig.WorldTuning tuning, WorldConfiguration paper,
                                     SpigotWorldConfig spigot, PurpurWorldConfig purpur) {
        var h = tuning.hopper();
        spigot.hopperTransfer = or(h.transferTicks(), spigot.hopperTransfer);
        spigot.hopperCheck = or(h.checkTicks(), spigot.hopperCheck);
        spigot.hopperAmount = or(h.amount(), spigot.hopperAmount);
        paper.hopper.cooldownWhenFull = or(h.cooldownWhenFull(), paper.hopper.cooldownWhenFull);
        paper.hopper.disableMoveEvent = or(h.disableMoveEvent(), paper.hopper.disableMoveEvent);
        paper.hopper.ignoreOccludingBlocks = or(h.ignoreOccludingBlocks(), paper.hopper.ignoreOccludingBlocks);
        var a = tuning.armorStands();
        paper.entities.armorStands.tick = or(a.tick(), paper.entities.armorStands.tick);
        paper.entities.armorStands.doCollisionEntityLookups = or(a.collisionLookups(), paper.entities.armorStands.doCollisionEntityLookups);
        purpur.armorstandMovement = or(a.movement(), purpur.armorstandMovement);
        purpur.armorstandWaterMovement = or(a.waterMovement(), purpur.armorstandWaterMovement);
        var x = tuning.antiXray();
        paper.anticheat.antiXray.enabled = or(x.enabled(), paper.anticheat.antiXray.enabled);
        if (x.engineMode() != null) paper.anticheat.antiXray.engineMode = EngineMode.valueOf(x.engineMode());
        paper.anticheat.antiXray.maxBlockHeight = or(x.maxBlockHeight(), paper.anticheat.antiXray.maxBlockHeight);
        paper.anticheat.antiXray.updateRadius = or(x.updateRadius(), paper.anticheat.antiXray.updateRadius);
        paper.anticheat.antiXray.lavaObscures = or(x.lavaObscures(), paper.anticheat.antiXray.lavaObscures);
        paper.anticheat.antiXray.usePermission = or(x.usePermission(), paper.anticheat.antiXray.usePermission);
        var b = tuning.blocks();
        paper.unsupportedSettings.ticking.blockEntities = or(b.blockEntityTicking(), paper.unsupportedSettings.ticking.blockEntities);
        paper.environment.maxBlockTicks = or(b.maxBlockTicks(), paper.environment.maxBlockTicks);
        paper.environment.maxFluidTicks = or(b.maxFluidTicks(), paper.environment.maxFluidTicks);
        if (tuning.redstone() != null) paper.misc.redstoneImplementation = WorldConfiguration.Misc.RedstoneImplementation.valueOf(tuning.redstone());
        paper.chunks.maxAutoSaveChunksPerTick = or(tuning.autoSaveChunks(), paper.chunks.maxAutoSaveChunksPerTick);
        return !Boolean.FALSE.equals(a.gravity());
    }

    private static <T> T or(T override, T upstream) { return override == null ? upstream : override; }
}
