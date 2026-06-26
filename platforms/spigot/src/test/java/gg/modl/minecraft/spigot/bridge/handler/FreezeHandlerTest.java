package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreezeHandlerTest {

    @Test
    void frozenAsyncChatBroadcastRunsThroughScheduler() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BridgeLocaleManager localeManager = mock(BridgeLocaleManager.class);
        RecordingScheduler scheduler = new RecordingScheduler();
        FreezeHandler freezeHandler = new FreezeHandler(plugin, localeManager, scheduler);
        StaffModeHandler staffModeHandler = mock(StaffModeHandler.class);
        Player frozenPlayer = mock(Player.class);
        Player staffPlayer = mock(Player.class);
        UUID frozenUuid = UUID.randomUUID();
        UUID staffUuid = UUID.randomUUID();
        AtomicBoolean scheduledTaskRunning = new AtomicBoolean(false);

        when(frozenPlayer.getUniqueId()).thenReturn(frozenUuid);
        when(frozenPlayer.getName()).thenReturn("Frozen");
        when(staffPlayer.getUniqueId()).thenReturn(staffUuid);
        when(staffModeHandler.isInStaffMode(staffUuid)).thenReturn(true);
        when(localeManager.getMessage(eq("freeze.chat"), anyMap())).thenReturn("Frozen: hello");

        freezeHandler.setStaffModeHandler(staffModeHandler);
        freezeHandler.freeze(frozenUuid.toString(), staffUuid.toString());

        AsyncPlayerChatEvent event = mock(AsyncPlayerChatEvent.class);
        when(event.getPlayer()).thenReturn(frozenPlayer);
        when(event.getMessage()).thenReturn("hello");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> {
                assertTrue(scheduledTaskRunning.get(), "online players must be collected from the scheduled task");
                return Collections.singletonList(staffPlayer);
            });
            bukkit.when(() -> Bukkit.getPlayer(frozenUuid)).thenReturn(frozenPlayer);
            bukkit.when(() -> Bukkit.getPlayer(staffUuid)).thenReturn(staffPlayer);

            freezeHandler.onChat(event);

            verify(event).setCancelled(true);
            verify(frozenPlayer, never()).sendMessage("Frozen: hello");
            verify(staffPlayer, never()).sendMessage("Frozen: hello");
            assertEquals(1, scheduler.syncRuns);

            scheduledTaskRunning.set(true);
            scheduler.syncTask.run();
        }

        verify(frozenPlayer).sendMessage("Frozen: hello");
        verify(staffPlayer).sendMessage("Frozen: hello");
    }

    @Test
    void frozenPlayerCannotDropClickOrInteract() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BridgeLocaleManager localeManager = mock(BridgeLocaleManager.class);
        RecordingScheduler scheduler = new RecordingScheduler();
        FreezeHandler freezeHandler = new FreezeHandler(plugin, localeManager, scheduler);

        Player frozenPlayer = mock(Player.class);
        UUID frozenUuid = UUID.randomUUID();
        when(frozenPlayer.getUniqueId()).thenReturn(frozenUuid);
        freezeHandler.freeze(frozenUuid.toString(), UUID.randomUUID().toString());

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
    }

    @Test
    void unfrozenPlayerDropClickAndInteractNotCancelled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BridgeLocaleManager localeManager = mock(BridgeLocaleManager.class);
        RecordingScheduler scheduler = new RecordingScheduler();
        FreezeHandler freezeHandler = new FreezeHandler(plugin, localeManager, scheduler);

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

    private static class RecordingScheduler implements BridgeScheduler {
        private Runnable syncTask;
        private int syncRuns;

        @Override
        public void runSync(Runnable task) {
            syncRuns++;
            syncTask = task;
        }

        @Override
        public void runForPlayer(UUID playerUuid, Runnable task) {
            if (syncTask != null) {
                task.run();
            }
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
