package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreezeHandlerTest {

    private final InlineScheduler scheduler = new InlineScheduler();
    private final World world = mock(World.class);
    private final UUID worldId = UUID.randomUUID();
    private final UUID frozenUuid = UUID.randomUUID();
    private final Player frozenPlayer = mock(Player.class);
    private final FreezeHandler freezeHandler =
            new FreezeHandler(mock(JavaPlugin.class), mock(BridgeLocaleManager.class), scheduler);

    FreezeHandlerTest() {
        when(world.getUID()).thenReturn(worldId);
        when(frozenPlayer.getUniqueId()).thenReturn(frozenUuid);
    }

    private void freezeAndAnchorAt(double x, double y, double z) {
        scheduler.running = false;
        freezeHandler.freeze(frozenUuid.toString(), UUID.randomUUID().toString());
        scheduler.running = true;
        PlayerMoveEvent anchoring = moveEvent(new Location(world, x, y, z), new Location(world, x, y, z));
        freezeHandler.onMove(anchoring);
    }

    private PlayerMoveEvent moveEvent(Location from, Location to) {
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(frozenPlayer);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        return event;
    }

    @Test
    void frozenPlayerIsPinnedBackToTheAnchor() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerMoveEvent running = moveEvent(new Location(world, 10, 64, 10), new Location(world, 15, 64, 10, 90f, 20f));
        freezeHandler.onMove(running);

        ArgumentCaptor<Location> pinned = ArgumentCaptor.forClass(Location.class);
        verify(running).setTo(pinned.capture());
        assertEquals(10.0, pinned.getValue().getX());
        assertEquals(10.0, pinned.getValue().getZ());
        assertEquals(90f, pinned.getValue().getYaw());
        assertEquals(20f, pinned.getValue().getPitch());
    }

    @Test
    void frozenPlayerIsNotPinnedWhileStillAtTheAnchor() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerMoveEvent lookingAround = moveEvent(new Location(world, 10, 64, 10), new Location(world, 10, 64, 10, 45f, 0f));
        freezeHandler.onMove(lookingAround);

        verify(lookingAround, never()).setTo(any(Location.class));
    }

    @Test
    void crossWorldMovementTeleportsThePlayerBackToTheAnchor() {
        freezeAndAnchorAt(10, 64, 10);

        World nether = mock(World.class);
        when(nether.getUID()).thenReturn(UUID.randomUUID());
        when(frozenPlayer.getLocation()).thenReturn(new Location(nether, 1, 64, 1));
        PlayerMoveEvent inNether = moveEvent(new Location(nether, 1, 64, 1), new Location(nether, 2, 64, 1));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            bukkit.when(() -> Bukkit.getPlayer(frozenUuid)).thenReturn(frozenPlayer);
            freezeHandler.onMove(inNether);
        }

        verify(inNether, never()).setTo(any(Location.class));

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(frozenPlayer).teleport(destination.capture());
        assertEquals(world, destination.getValue().getWorld());
        assertEquals(10.0, destination.getValue().getX());
        assertEquals(10.0, destination.getValue().getZ());
        verify(frozenPlayer).setVelocity(new Vector(0, 0, 0));
    }

    @Test
    void freezingCapturesTheAnchorAndDismountsThePlayer() {
        when(frozenPlayer.getLocation()).thenReturn(new Location(world, 3, 65, 7));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(frozenUuid)).thenReturn(frozenPlayer);
            freezeHandler.freeze(frozenUuid.toString(), UUID.randomUUID().toString());
        }

        verify(frozenPlayer).leaveVehicle();
        verify(frozenPlayer).setVelocity(new Vector(0, 0, 0));

        PlayerMoveEvent running = moveEvent(new Location(world, 3, 65, 7), new Location(world, 9, 65, 7));
        freezeHandler.onMove(running);

        ArgumentCaptor<Location> pinned = ArgumentCaptor.forClass(Location.class);
        verify(running).setTo(pinned.capture());
        assertEquals(3.0, pinned.getValue().getX());
        assertEquals(7.0, pinned.getValue().getZ());
    }

    @Test
    void frozenPlayerCannotTeleportAwayFromTheAnchor() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(frozenPlayer);
        when(event.getTo()).thenReturn(new Location(world, 500, 70, 500));

        freezeHandler.onTeleport(event);

        verify(event).setCancelled(true);
    }

    @Test
    void teleportBackOntoTheAnchorIsAllowed() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(frozenPlayer);
        when(event.getTo()).thenReturn(new Location(world, 10, 64, 10, 12f, 3f));

        freezeHandler.onTeleport(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void frozenPlayerCannotUsePortals() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerPortalEvent event = mock(PlayerPortalEvent.class);
        when(event.getPlayer()).thenReturn(frozenPlayer);

        freezeHandler.onPortal(event);

        verify(event).setCancelled(true);
    }

    @Test
    void respawningMovesTheAnchorToTheRespawnPoint() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(frozenPlayer);
        when(respawn.getRespawnLocation()).thenReturn(new Location(world, 0, 70, 0));
        freezeHandler.onRespawn(respawn);

        PlayerMoveEvent atSpawn = moveEvent(new Location(world, 0, 70, 0), new Location(world, 0, 70, 0));
        freezeHandler.onMove(atSpawn);

        verify(atSpawn, never()).setTo(any(Location.class));
    }

    @Test
    void frozenPlayerCannotBoardAVehicle() {
        freezeAndAnchorAt(10, 64, 10);

        VehicleEnterEvent event = mock(VehicleEnterEvent.class);
        when(event.getEntered()).thenReturn(frozenPlayer);

        freezeHandler.onVehicleEnter(event);

        verify(event).setCancelled(true);
    }

    @Test
    void frozenPlayerCannotDropClickOrInteract() {
        freezeAndAnchorAt(10, 64, 10);

        PlayerDropItemEvent dropEvent = mock(PlayerDropItemEvent.class);
        when(dropEvent.getPlayer()).thenReturn(frozenPlayer);
        freezeHandler.onDropItem(dropEvent);
        verify(dropEvent).setCancelled(true);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(frozenPlayer);
        freezeHandler.onInventoryClick(clickEvent);
        verify(clickEvent).setCancelled(true);

        PlayerInteractEvent interactEvent = mock(PlayerInteractEvent.class);
        when(interactEvent.getPlayer()).thenReturn(frozenPlayer);
        freezeHandler.onInteract(interactEvent);
        verify(interactEvent).setCancelled(true);

        PlayerInteractEntityEvent interactEntityEvent = mock(PlayerInteractEntityEvent.class);
        when(interactEntityEvent.getPlayer()).thenReturn(frozenPlayer);
        freezeHandler.onInteractEntity(interactEntityEvent);
        verify(interactEntityEvent).setCancelled(true);
    }

    @Test
    void unfrozenPlayerDropClickAndInteractNotCancelled() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerDropItemEvent dropEvent = mock(PlayerDropItemEvent.class);
        when(dropEvent.getPlayer()).thenReturn(player);
        freezeHandler.onDropItem(dropEvent);
        verify(dropEvent, never()).setCancelled(true);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        freezeHandler.onInventoryClick(clickEvent);
        verify(clickEvent, never()).setCancelled(true);

        PlayerInteractEvent interactEvent = mock(PlayerInteractEvent.class);
        when(interactEvent.getPlayer()).thenReturn(player);
        freezeHandler.onInteract(interactEvent);
        verify(interactEvent, never()).setCancelled(true);
    }

    private static class InlineScheduler implements BridgeScheduler {
        private boolean running = true;

        @Override
        public void runOnMainThread(Runnable task) {
            if (running) task.run();
        }

        @Override
        public void runForPlayer(UUID playerUuid, Runnable task) {
            if (running) task.run();
        }

        @Override
        public void runLater(Runnable task, long delayTicks) {
        }

        @Override
        public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {
        }

        @Override
        public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
            return () -> {};
        }

        @Override
        public void cancelTask(BridgeTask task) {
        }
    }
}
