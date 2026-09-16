package org.purpurmc.testplugin;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin implements Listener {
    private double[] samples;
    private int warmup;
    private int captured;

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().addPermission(new Permission("quantum.test", PermissionDefault.OP));
        this.getServer().getCommandMap().register("quantumtest", new Command("quantumtest", "Isolated Quantum smoke/baseline tools", "/quantumtest <check|slowtask|baseline>", List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!sender.hasPermission("quantum.test")) {
                    sender.sendMessage("Missing permission: quantum.test");
                    return true;
                }
                if (args.length != 1) {
                    sender.sendMessage(this.getUsage());
                    return true;
                }
                switch (args[0]) {
                    case "check" -> Bukkit.getScheduler().runTask(TestPlugin.this, () -> {
                        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Sync scheduler is not on the primary thread");
                        sender.sendMessage("PASS: synchronous Bukkit scheduler runs on the primary thread.");
                    });
                    case "slowtask" -> {
                        Bukkit.getScheduler().runTask(TestPlugin.this, () -> LockSupport.parkNanos(40_000_000L));
                        sender.sendMessage("Scheduled one intentional 40 ms task for stall-detector verification.");
                    }
                    case "baseline" -> {
                        if (TestPlugin.this.samples != null) {
                            sender.sendMessage("Capture already active.");
                        } else {
                            TestPlugin.this.samples = new double[1200];
                            TestPlugin.this.warmup = 600;
                            TestPlugin.this.captured = 0;
                            sender.sendMessage("Baseline: 600 warmup ticks, then 1200 measured ticks. Keep workload fixed.");
                        }
                    }
                    default -> sender.sendMessage(this.getUsage());
                }
                return true;
            }
        });
        this.getLogger().info("Quantum smoke tools enabled; no workload starts automatically.");
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (this.samples == null) return;
        if (this.warmup > 0) {
            this.warmup--;
            return;
        }
        this.samples[this.captured++] = event.getTickDuration();
        if (this.captured != this.samples.length) return;
        double[] completed = this.samples;
        this.samples = null;
        Path directory = this.getDataFolder().toPath();
        String name = "baseline-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
        String metadata = "timestamp=" + Instant.now() + "\nserver=" + Bukkit.getVersion()
            + "\njava=" + Runtime.version() + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
            + "\nos=" + System.getProperty("os.name") + "\nmetric=ServerTickEndEvent.tickDuration_ms\n"
            + "warmup_ticks=600\nsample_ticks=1200\n";
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            StringBuilder csv = new StringBuilder("sample,mspt\n");
            for (int i = 0; i < completed.length; i++) csv.append(i).append(',').append(completed[i]).append('\n');
            try {
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(name + ".csv"), csv.toString());
                Files.writeString(directory.resolve(name + ".properties"), metadata);
                this.getLogger().info("Baseline saved: " + directory.resolve(name + ".csv"));
            } catch (IOException e) {
                this.getLogger().log(Level.SEVERE, "Unable to save baseline", e);
            }
        });
    }

    @Override
    public void onDisable() {
        if (this.samples != null) this.getLogger().warning("Incomplete baseline discarded on shutdown.");
        this.samples = null;
    }
}
