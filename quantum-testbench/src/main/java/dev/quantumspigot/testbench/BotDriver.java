package dev.quantumspigot.testbench;

import com.google.gson.Gson;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.geysermc.mcprotocollib.protocol.packet.ping.clientbound.ClientboundPongResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.ping.serverbound.ServerboundPingRequestPacket;

/** Loopback-only protocol driver. Mixed movement requires the runner's flat arena fixture. */
public final class BotDriver {
    private static final Gson JSON = new Gson();
    private static final AtomicLong RECEIVED = new AtomicLong();
    private static final AtomicLong SENT = new AtomicLong();
    private static final AtomicLong DISCONNECTS = new AtomicLong();
    private static volatile boolean stopping;
    private static volatile boolean active;
    private static volatile long measuredAfter;
    private static final List<Long> ROUND_TRIPS = java.util.Collections.synchronizedList(new ArrayList<>());
    private static String profile = "idle";
    private static byte[] forwardingSecret;
    private static final int ARENA_Y = Integer.getInteger("quantum.arena-y", 160);
    private static final int ARENA_SPACING = Integer.getInteger("quantum.arena-spacing", 8);
    private static final Map<String, AtomicLong> ACTIONS = new ConcurrentHashMap<>();
    private static void count(String action) { ACTIONS.computeIfAbsent(action, ignored -> new AtomicLong()).incrementAndGet(); }
    private static Map<String, Long> actions() {
        Map<String, Long> result = new java.util.TreeMap<>();
        ACTIONS.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    private static synchronized void emit(Object event) { System.out.println(JSON.toJson(event)); }

    public static void main(String[] args) throws Exception {
        if (!MinecraftCodec.CODEC.getMinecraftVersion().equals("26.2") || MinecraftCodec.CODEC.getProtocolVersion() != 776) {
            throw new IllegalStateException("Driver must use native 26.2 / protocol 776");
        }
        if (args.length == 1 && args[0].equals("--version")) {
            emit(Map.of("minecraft", "26.2", "protocol", 776, "driver", "local-active", "profiles", List.of("idle", "mixed", "explore")));
            return;
        }
        if (args.length < 4 || args.length > 6) throw new IllegalArgumentException("Usage: BotDriver <port> <bots 1..300> <seconds 10..3600> <output.json> [idle|mixed|explore] [warmupSeconds]");
        if (args.length >= 5) profile = args[4];
        if (!List.of("idle", "mixed", "explore").contains(profile)) throw new IllegalArgumentException("Unknown profile");
        int port = Integer.parseInt(args[0]), count = Integer.parseInt(args[1]), seconds = Integer.parseInt(args[2]);
        int warmupSeconds = args.length == 6 ? Integer.parseInt(args[5]) : 0;
        if (warmupSeconds < 0 || warmupSeconds >= seconds) throw new IllegalArgumentException("Invalid warmup duration");
        if (port < 1024 || port > 65535 || count < 1 || count > 300 || seconds < 10 || seconds > 3600) {
            throw new IllegalArgumentException("Port, count or duration outside allowed range");
        }
        Path output = Path.of(args[3]).toAbsolutePath();
        if (ARENA_Y < 80 || ARENA_Y > 310) throw new IllegalArgumentException("Arena Y must be 80..310");
        if (ARENA_SPACING < 8 || ARENA_SPACING > 64) throw new IllegalArgumentException("Arena spacing must be 8..64");
        String secretFile = System.getenv("QUANTUM_FORWARDING_SECRET_FILE");
        if (secretFile != null) {
            forwardingSecret = Files.readString(Path.of(secretFile)).trim().getBytes(StandardCharsets.UTF_8);
            if (forwardingSecret.length < 32) throw new IllegalArgumentException("Forwarding secret must be at least 32 bytes");
        }
        if (Files.exists(output)) throw new IllegalArgumentException("Refusing to overwrite an existing result");
        Files.createDirectories(output.getParent());
        List<Bot> bots = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            synchronized (bots) { for (Bot bot : bots) bot.session.disconnect(Component.text("Testbench shutdown")); }
        }));
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            Bot bot = new Bot(i, port);
            synchronized (bots) { bots.add(bot); }
            bot.session.connect(false);
            Thread.sleep(profile.equals("idle") ? 500 : 200); // bounded ramp, not a login-burst test
        }
        long loginDeadline = System.nanoTime() + 90_000_000_000L;
        while (ready(bots) != count && System.nanoTime() < loginDeadline && DISCONNECTS.get() == 0) Thread.sleep(50);
        boolean allSpawned = ready(bots) == count;
        int minReady = ready(bots);
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        if (allSpawned) emit(Map.of("event", "all_spawned", "bots", count, "joinPhaseMs", (System.nanoTime() - start) / 1_000_000.0));
        if (allSpawned && !profile.equals("idle")) {
            long setupDeadline = System.nanoTime() + 180_000_000_000L;
            while (!Files.exists(Path.of(output + ".go")) && System.nanoTime() < setupDeadline && DISCONNECTS.get() == 0) Thread.sleep(50);
            if (!Files.exists(Path.of(output + ".go"))) throw new IllegalStateException("Arena setup timed out");
        }
        active = true;
        emit(Map.of("event", "actions_started", "timestamp", Instant.now().toString(), "profile", profile));
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        long measurementStart = System.nanoTime() + warmupSeconds * 1_000_000_000L;
        measuredAfter = measurementStart;
        long nextTick = System.nanoTime(), lastReport = 0, maxTickDelay = 0, warmupMaxTickDelay = 0;
        while (allSpawned && System.nanoTime() < deadline && DISCONNECTS.get() == 0) {
            long now = System.nanoTime();
            long delay = Math.max(0, now - nextTick);
            if (now >= measurementStart) maxTickDelay = Math.max(maxTickDelay, delay);
            else warmupMaxTickDelay = Math.max(warmupMaxTickDelay, delay);
            for (Bot bot : bots) bot.tick(now);
            int connected = ready(bots);
            minReady = Math.min(minReady, connected);
            if (now - lastReport >= 5_000_000_000L) {
                lastReport = now;
                emit(Map.of("event", "sample", "timestamp", Instant.now().toString(), "ready", connected,
                    "receivedPackets", RECEIVED.get(), "sentPackets", SENT.get(), "unexpectedDisconnects", DISCONNECTS.get(),
                    "driverMaxTickDelayMs", maxTickDelay / 1_000_000.0, "driverProcessCpuFraction", os.getProcessCpuLoad(),
                    "driverHeapUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
                emit(Map.of("event", "actions", "counts", actions()));
            }
            nextTick = Math.max(nextTick + 50_000_000L, now + 1_000_000L); // never catch up with a packet burst
            long wait = nextTick - System.nanoTime();
            if (wait > 0) java.util.concurrent.locks.LockSupport.parkNanos(wait);
        }
        stopping = true;
        for (Bot bot : bots) bot.session.disconnect(Component.text("Testbench completed"));
        boolean pass = allSpawned && minReady == count && DISCONNECTS.get() == 0 && maxTickDelay < 250_000_000L;
        Map<String, Object> result = new java.util.LinkedHashMap<>(Map.of("result", pass ? "PASS" : "FAIL", "requestedBots", count,
            "allSpawned", allSpawned, "minimumReady", minReady, "unexpectedDisconnects", DISCONNECTS.get(),
            "driverMaxTickDelayMs", maxTickDelay / 1_000_000.0, "elapsedSeconds", (System.nanoTime() - start) / 1_000_000_000.0,
            "mode", profile, "minecraft", "26.2"));
        result.put("actions", actions());
        result.put("warmupMaxTickDelayMs", warmupMaxTickDelay / 1_000_000.0);
        result.put("warmupSeconds", warmupSeconds);
        synchronized (ROUND_TRIPS) {
            ROUND_TRIPS.sort(Long::compare);
            result.put("transportRoundTrip", ROUND_TRIPS.isEmpty() ? Map.of("samples", 0) : Map.of(
                "samples", ROUND_TRIPS.size(), "p50Ms", ROUND_TRIPS.get((ROUND_TRIPS.size() - 1) / 2) / 1_000_000.0,
                "p95Ms", ROUND_TRIPS.get((int) Math.ceil(ROUND_TRIPS.size() * .95) - 1) / 1_000_000.0,
                "maxMs", ROUND_TRIPS.getLast() / 1_000_000.0));
        }
        Files.writeString(output, JSON.toJson(result) + "\n");
        emit(result);
        Thread.sleep(500);
        if (!pass) System.exit(1);
    }

    private static int ready(List<Bot> bots) {
        int ready = 0;
        for (Bot bot : bots) if (bot.spawned && bot.session.isConnected()) ready++;
        return ready;
    }

    private static final class Bot extends SessionAdapter {
        private final ClientSession session;
        private final String name;
        private final long started = System.nanoTime();
        private volatile boolean spawned;
        private boolean loggedIn, positioned, chunks;
        private double x, y, z;
        private float yaw, pitch;
        private long lastLook;
        private long pingSent, lastPing;
        private final int index;
        private int entityId, targetId = -1, sequence, tick, lastTargetBlock = -1;
        private double verticalSpeed;
        private boolean sprinting;
        private final double homeX, homeZ;
        private Vector3i block;
        private final java.util.Set<Long> loadedChunks = new java.util.HashSet<>();
        private static long chunkKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

        private Bot(int index, int port) throws Exception {
            this.index = index;
            this.homeX = (index % 20) * ARENA_SPACING + 4.5;
            this.homeZ = (index / 20) * ARENA_SPACING + 4.5;
            this.block = Vector3i.from((int) homeX + 2, ARENA_Y, (int) homeZ);
            this.name = String.format(java.util.Locale.ROOT, "QTest%03d", index + 1);
            this.session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                .setProtocol(new MinecraftProtocol(this.name)).create();
            this.session.setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, false);
            this.session.setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);
            this.session.addListener(this);
        }

        @Override public synchronized void packetReceived(Session session, Packet packet) {
            RECEIVED.incrementAndGet();
            if (packet instanceof ClientboundPongResponsePacket pong && pong.getPingTime() == pingSent && pingSent != 0) {
                if (pingSent >= measuredAfter) ROUND_TRIPS.add(System.nanoTime() - pingSent);
                pingSent = 0;
                count("pingResponses");
            }
            if (packet instanceof ClientboundCustomQueryPacket query) {
                try {
                    byte[] answer = forwardingSecret != null && query.getChannel().asString().equals("velocity:player_info")
                        ? forwarding(this.name) : null;
                    session.send(new ServerboundCustomQueryAnswerPacket(query.getMessageId(), answer));
                } catch (Exception e) { session.disconnect(Component.text("Unable to sign local test profile"), e); }
            } else if (packet instanceof ClientboundLoginFinishedPacket) {
                session.send(new ServerboundClientInformationPacket("en_us", 6, ChatVisibility.FULL, true,
                    List.of(), HandPreference.RIGHT_HAND, false, true, ParticleStatus.MINIMAL));
            } else if (packet instanceof ClientboundLoginPacket login) {
                this.loggedIn = true;
                this.entityId = login.getEntityId();
            } else if (packet instanceof ClientboundPlayerPositionPacket position) {
                this.x = position.getPosition().getX() + (position.getRelatives().contains(PositionElement.X) ? this.x : 0);
                this.y = position.getPosition().getY() + (position.getRelatives().contains(PositionElement.Y) ? this.y : 0);
                this.z = position.getPosition().getZ() + (position.getRelatives().contains(PositionElement.Z) ? this.z : 0);
                this.yaw = position.getYRot() + (position.getRelatives().contains(PositionElement.Y_ROT) ? this.yaw : 0);
                this.pitch = position.getXRot() + (position.getRelatives().contains(PositionElement.X_ROT) ? this.pitch : 0);
                session.send(new ServerboundAcceptTeleportationPacket(position.getId()));
                session.send(new ServerboundMovePlayerPosRotPacket(false, false, this.x, this.y, this.z, this.yaw, this.pitch));
                this.positioned = true;
                if (active) count("positionCorrections");
            } else if (packet instanceof ClientboundPlayerAbilitiesPacket abilities && profile.equals("explore") && abilities.isCanFly()) {
                session.send(new ServerboundPlayerAbilitiesPacket(true));
            } else if (packet instanceof ClientboundAddEntityPacket entity && entity.getType() == EntityType.ZOMBIE
                && Math.abs(entity.getX() - (homeX + 2)) < 1 && Math.abs(entity.getZ() - homeZ) < 1) {
                targetId = entity.getEntityId();
            } else if (packet instanceof ClientboundDamageEventPacket hurt && hurt.getEntityId() == targetId && hurt.getSourceCauseId() == entityId) {
                count("confirmedTargetDamage");
            } else if (packet instanceof ClientboundOpenScreenPacket screen) {
                count("confirmedContainerOpen");
                session.send(new ServerboundContainerClosePacket(screen.getContainerId()));
            } else if (packet instanceof ClientboundBlockUpdatePacket update && update.getEntry().getPosition().equals(block)) {
                count("targetBlockUpdates");
                int state = update.getEntry().getBlock();
                if (active && lastTargetBlock == 0 && state != 0) count("confirmedBlockPlaced");
                if (active && lastTargetBlock > 0 && state == 0) count("confirmedBlockBroken");
                lastTargetBlock = state;
            } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
                this.chunks = true;
                if (profile.equals("explore")) loadedChunks.add(chunkKey(chunk.getX(), chunk.getZ()));
                if (active) count("chunksReceivedDuringActions");
            } else if (packet instanceof ClientboundForgetLevelChunkPacket chunk) {
                loadedChunks.remove(chunkKey(chunk.getX(), chunk.getZ()));
            } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
                session.send(new ServerboundChunkBatchReceivedPacket(4.0f));
            } else if (packet instanceof ClientboundPingPacket ping) {
                session.send(new ServerboundPongPacket(ping.getId()));
            }
            if (!this.spawned && this.loggedIn && this.positioned && this.chunks) {
                session.send(ServerboundPlayerLoadedPacket.INSTANCE);
                this.spawned = true;
                emit(Map.of("event", "spawned", "bot", this.name, "latencyMs", (System.nanoTime() - this.started) / 1_000_000.0));
            }
        }

        @Override public void packetSent(Session session, Packet packet) { SENT.incrementAndGet(); }

        @Override public void disconnected(DisconnectedEvent event) {
            if (!stopping) DISCONNECTS.incrementAndGet();
            emit(Map.of("event", "disconnected", "bot", this.name, "expected", stopping,
                "reason", event.getReason().toString(), "cause", event.getCause() == null ? "" : event.getCause().toString()));
        }

        private synchronized void tick(long now) {
            if (spawned && pingSent == 0 && now - lastPing >= 5_000_000_000L) {
                lastPing = pingSent = now;
                session.send(new ServerboundPingRequestPacket(now));
                count("pingRequests");
            }
            if (!this.spawned || !this.session.isConnected()) return;
            if (!profile.equals("idle")) {
                gameplay();
                this.session.send(ServerboundClientTickEndPacket.INSTANCE);
                return;
            }
            if (now - this.lastLook >= 1_000_000_000L) {
                this.lastLook = now;
                this.yaw = (this.yaw + 15) % 360;
                this.session.send(new ServerboundMovePlayerRotPacket(true, false, this.yaw, this.pitch));
            }
            this.session.send(ServerboundClientTickEndPacket.INSTANCE);
        }

        private void gameplay() {
            int step = tick++ + index * 17;
            int role = index % 10;
            boolean moving = profile.equals("explore") || (role >= 1 && role <= 5);
            if (moving) {
                boolean grounded = true;
                if (profile.equals("explore")) {
                    double angle = index * 2.399963229728653;
                    double nextX = x + Math.cos(angle) * .20, nextZ = z + Math.sin(angle) * .20;
                    if (loadedChunks.contains(chunkKey((int) Math.floor(nextX / 16), (int) Math.floor(nextZ / 16)))) {
                        x = nextX; z = nextZ; count("explorationSteps");
                    } else count("chunkWaitTicks");
                    grounded = false;
                } else {
                    // ponytail: collision/physics only for the known flat arena; terrain traversal needs a world-aware client.
                    double angle = step * .09;
                    double dx = homeX + 2 * Math.sin(angle) - x, dz = homeZ + 2 * Math.cos(angle) - z;
                    double distance = Math.hypot(dx, dz);
                    double speed = role % 2 == 0 ? .26 : .20;
                    if (distance > .001) { x += dx / distance * Math.min(speed, distance); z += dz / distance * Math.min(speed, distance); }
                    if (step % 100 == 0 && y <= ARENA_Y + .001) { verticalSpeed = .42; count("jumps"); }
                    y += verticalSpeed;
                    verticalSpeed = (verticalSpeed - .08) * .98;
                    if (y <= ARENA_Y) { y = ARENA_Y; verticalSpeed = 0; } else grounded = false;
                    if (!sprinting && role % 2 == 0) {
                        session.send(new ServerboundPlayerCommandPacket(entityId, PlayerState.START_SPRINTING)); sprinting = true;
                    }
                }
                yaw = (yaw + 5) % 360;
                session.send(new ServerboundMovePlayerPosRotPacket(grounded, false, x, y, z, yaw, 0));
                count("movementPackets");
            }
            if (profile.equals("mixed")) {
                if ((role == 6 || role == 7) && step % 160 == 0) {
                    session.send(new ServerboundSetCarriedItemPacket(0));
                    session.send(new ServerboundUseItemOnPacket(block.add(0, -1, 0), Direction.UP, Hand.MAIN_HAND, .5f, 1, .5f, false, false, ++sequence));
                    count("placeAttempts");
                }
                if ((role == 6 || role == 7) && step % 160 == 30) {
                    session.send(new ServerboundSetCarriedItemPacket(1));
                    session.send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, block, Direction.UP, ++sequence));
                    count("digAttempts");
                }
                if ((role == 6 || role == 7) && step % 160 == 50) {
                    session.send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, block, Direction.UP, ++sequence));
                }
                if (role == 8 && targetId >= 0 && step % 24 == 0) {
                    session.send(new ServerboundAttackPacket(targetId));
                    session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
                    count("attackAttempts");
                }
                if (role == 9 && step % 80 == 0) {
                    session.send(new ServerboundUseItemOnPacket(block, Direction.UP, Hand.MAIN_HAND, .5f, .5f, .5f, false, false, ++sequence));
                    session.send(new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0));
                    session.send(new ServerboundSetCarriedItemPacket((step / 80) % 9));
                    count("inventoryCycles");
                }
            }
            if (index % 5 == 0 && step % 500 == 0) {
                session.send(new ServerboundChatPacket("qtest " + name + " " + step, System.currentTimeMillis(), 0, null, 0, new BitSet(), 0));
                count("chatMessages");
            }
            if (index % 5 == 1 && step % 400 == 0) {
                session.send(new ServerboundChatCommandPacket("list")); count("commands");
            }
        }
    }

    /** Velocity v1 for loopback test profiles only; the server's signature validation remains enabled. */
    private static byte[] forwarding(String name) throws Exception {
        if (!name.matches("QTest[0-9]{3}")) throw new IllegalArgumentException("Not a test profile");
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeByte(1); // Forwarding version, followed by two short ASCII strings (one-byte VarInt lengths).
        data.writeByte(9); data.writeBytes("127.0.0.1");
        data.writeLong(id.getMostSignificantBits()); data.writeLong(id.getLeastSignificantBits());
        data.writeByte(name.length()); data.writeBytes(name); data.writeByte(0); // No skin properties.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(forwardingSecret, "HmacSHA256"));
        byte[] payload = bytes.toByteArray();
        bytes.reset(); bytes.write(mac.doFinal(payload)); bytes.write(payload);
        return bytes.toByteArray();
    }
}
