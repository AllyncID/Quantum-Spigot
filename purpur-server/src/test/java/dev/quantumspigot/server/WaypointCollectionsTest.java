package dev.quantumspigot.server;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@VanillaFeature
class WaypointCollectionsTest {
    @Test
    void bothPathsPreserveUpdatesBrokenRetriesAndCleanup() {
        for (boolean optimized : new boolean[] {false, true}) {
            var level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.getGameRules().get(GameRules.LOCATOR_BAR)).thenReturn(true);
            try (var runtime = mockStatic(QuantumRuntime.class)) {
                runtime.when(() -> QuantumRuntime.waypointCollections("minecraft:overworld")).thenReturn(optimized);
                var manager = new ServerWaypointManager(level);
                assertEquals(optimized, manager.quantumWaypointCollections);
                var player = mock(ServerPlayer.class);
                var waypoint = mock(WaypointTransmitter.class);
                var first = mock(WaypointTransmitter.Connection.class);
                var replacement = mock(WaypointTransmitter.Connection.class);
                when(waypoint.makeWaypointConnectionWith(player)).thenReturn(Optional.of(first));
                manager.addPlayer(player);
                manager.trackWaypoint(waypoint);
                verify(first).connect();
                manager.updateWaypoint(waypoint);
                manager.updatePlayer(player);
                verify(first, times(2)).update();
                // Broken connection removal must be retried in the same update's second pass.
                when(first.isBroken()).thenReturn(true);
                when(waypoint.makeWaypointConnectionWith(player)).thenReturn(Optional.empty(), Optional.of(replacement));
                manager.updateWaypoint(waypoint);
                verify(first).disconnect();
                verify(replacement).connect();
                manager.locatorBarEnabled = false;
                manager.updateWaypoint(waypoint);
                manager.updatePlayer(player);
                verify(replacement, never()).update();
                manager.locatorBarEnabled = true;
                manager.updatePlayer(player);
                verify(replacement).update();
                manager.removePlayer(player);
                verify(replacement).disconnect();
                manager.untrackWaypoint(waypoint);
                assertTrue(manager.transmitters().isEmpty());
                manager.breakAllConnections();
                verify(replacement, times(1)).disconnect();
                // A removed/re-added row must not retain stale connections.
                manager.addPlayer(player);
                manager.trackWaypoint(waypoint);
                verify(replacement, times(2)).connect();
                manager.breakAllConnections();
                verify(replacement, times(2)).disconnect();
                manager.remakeConnections(waypoint);
                verify(replacement, times(3)).connect();
                manager.untrackWaypoint(waypoint);
                verify(replacement, times(3)).disconnect();
            }
        }
    }
}
