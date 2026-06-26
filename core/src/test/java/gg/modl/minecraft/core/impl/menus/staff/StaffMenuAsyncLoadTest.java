package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.impl.commands.staff.StaffListCommand;
import gg.modl.minecraft.core.util.Permissions;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffMenuAsyncLoadTest {
    @Test
    void staffListDataFutureWaitsForStaffAndRolesBeforeCompleting() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174300");
        Cache cache = new Cache(new CachedProfileRegistry());
        cache.cacheStaffPermissions(viewerUuid, "ModlStaff", "staff-1", "Admin",
                Collections.singletonList(Permissions.STAFF_MANAGE));
        TestPlatform platform = new TestPlatform(cache);
        CompletableFuture<RolesListResponse> rolesFuture = new CompletableFuture<>();
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();

        StaffListMenu menu = new StaffListMenu(platform.platform(), httpClient(rolesFuture, staffFuture),
                viewerUuid, "ModlStaff", true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        rolesFuture.complete(new RolesListResponse(Collections.emptyList(), 200));

        assertFalse(menu.getDataFuture().isDone());

        staffFuture.complete(new StaffListResponse(Collections.emptyList(), 200));

        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void staffListDataFutureFailsWhenRolesFetchIsNotSuccessful() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174303");
        Cache cache = new Cache(new CachedProfileRegistry());
        cache.cacheStaffPermissions(viewerUuid, "ModlStaff", "staff-1", "Admin",
                Collections.singletonList(Permissions.STAFF_MANAGE));
        TestPlatform platform = new TestPlatform(cache);

        StaffListMenu menu = new StaffListMenu(platform.platform(), httpClient(
                CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 403)),
                CompletableFuture.completedFuture(new StaffListResponse(Collections.emptyList(), 200))),
                viewerUuid, "ModlStaff", true, "https://panel.modl.gg", null);

        assertThrows(CompletionException.class, menu.getDataFuture()::join);
    }

    @Test
    void staffListDataFutureFailsWhenStaffFetchFails() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174304");
        Cache cache = new Cache(new CachedProfileRegistry());
        cache.cacheStaffPermissions(viewerUuid, "ModlStaff", "staff-1", "Admin",
                Collections.singletonList(Permissions.STAFF_MANAGE));
        TestPlatform platform = new TestPlatform(cache);
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();
        staffFuture.completeExceptionally(new IllegalStateException("backend unavailable"));

        StaffListMenu menu = new StaffListMenu(platform.platform(), httpClient(
                CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                staffFuture),
                viewerUuid, "ModlStaff", true, "https://panel.modl.gg", null);

        assertThrows(CompletionException.class, menu.getDataFuture()::join);
    }

    @Test
    void settingsMenuSchedulesLoadedDisplayOnPlatformMainThread() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
        Cache cache = new Cache(new CachedProfileRegistry());
        TestPlatform platform = new TestPlatform(cache);
        CompletableFuture<Void> dataFuture = new CompletableFuture<>();
        AtomicInteger displayCount = new AtomicInteger();

        SettingsMenu.displayWhenLoaded(platform.platform(), dataFuture, playerWrapper(viewerUuid),
                player -> displayCount.incrementAndGet());

        assertEquals(0, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        dataFuture.complete(null);

        assertEquals(1, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        platform.runScheduledTask();

        assertEquals(1, displayCount.get());
    }

    @Test
    void settingsMenuDoesNotDisplayWhenLoadFails() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174305");
        Cache cache = new Cache(new CachedProfileRegistry());
        TestPlatform platform = new TestPlatform(cache);
        CompletableFuture<Void> dataFuture = new CompletableFuture<>();
        AtomicInteger displayCount = new AtomicInteger();

        SettingsMenu.displayWhenLoaded(platform.platform(), dataFuture, playerWrapper(viewerUuid),
                player -> displayCount.incrementAndGet());

        dataFuture.completeExceptionally(new IllegalStateException("load failed"));

        // The failure branch schedules exactly one main-thread task (to notify the player).
        assertEquals(1, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        platform.runScheduledTask();

        // The player is notified of the failure; display still never happens.
        assertNotNull(platform.lastMessage());
        assertEquals(0, displayCount.get());
    }

    @Test
    void staffListCommandSchedulesMenuDisplayOnPlatformMainThreadAfterDataLoads() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
        Cache cache = new Cache(new CachedProfileRegistry());
        TestPlatform platform = new TestPlatform(cache);
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();
        StaffListCommand command = new StaffListCommand(platform.platform(), cache, null, null,
                new HttpClientHolder(httpClient(
                        CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                        staffFuture)),
                "https://panel.modl.gg");

        command.staffList(commandActor(viewerUuid), null);

        assertEquals(0, platform.mainThreadScheduleCount());

        staffFuture.complete(new StaffListResponse(Collections.emptyList(), 200));

        assertEquals(1, platform.mainThreadScheduleCount());
    }

    @Test
    void staffListCommandDoesNotScheduleMenuDisplayWhenStaffLoadIsNotSuccessful() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174306");
        Cache cache = new Cache(new CachedProfileRegistry());
        TestPlatform platform = new TestPlatform(cache);
        StaffListCommand command = new StaffListCommand(platform.platform(), cache, null, null,
                new HttpClientHolder(httpClient(
                        CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                        CompletableFuture.completedFuture(new StaffListResponse(Collections.emptyList(), 403)))),
                "https://panel.modl.gg");

        command.staffList(commandActor(viewerUuid), null);

        assertEquals(0, platform.mainThreadScheduleCount());
    }

    private static ModlHttpClient httpClient(
            CompletableFuture<RolesListResponse> rolesFuture,
            CompletableFuture<StaffListResponse> staffFuture
    ) {
        return (ModlHttpClient) Proxy.newProxyInstance(
                ModlHttpClient.class.getClassLoader(),
                new Class<?>[] {ModlHttpClient.class},
                (proxy, method, args) -> {
                    if ("getRoles".equals(method.getName())) return rolesFuture;
                    if ("getStaffList".equals(method.getName())) return staffFuture;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static CirrusPlayerWrapper playerWrapper(UUID playerUuid) {
        return (CirrusPlayerWrapper) Proxy.newProxyInstance(
                CirrusPlayerWrapper.class.getClassLoader(),
                new Class<?>[] {CirrusPlayerWrapper.class},
                (proxy, method, args) -> {
                    if ("uuid".equals(method.getName())) return playerUuid;
                    if ("protocolVersion".equals(method.getName())) return 0;
                    if ("handle".equals(method.getName())) return null;
                    return null;
                }
        );
    }

    private static CommandActor commandActor(UUID playerUuid) {
        return (CommandActor) Proxy.newProxyInstance(
                CommandActor.class.getClassLoader(),
                new Class<?>[] {CommandActor.class},
                (proxy, method, args) -> {
                    if ("uniqueId".equals(method.getName())) return playerUuid;
                    if ("name".equals(method.getName())) return "ModlStaff";
                    if ("reply".equals(method.getName())) return null;
                    if ("sendRawMessage".equals(method.getName())) return null;
                    if ("sendRawError".equals(method.getName())) return null;
                    if ("lamp".equals(method.getName())) return null;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static class TestPlatform {
        private final Cache cache;
        private final AtomicInteger mainThreadScheduleCount = new AtomicInteger();
        private final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        private final AtomicReference<String> lastMessage = new AtomicReference<>();
        private final gg.modl.minecraft.core.locale.LocaleManager localeManager =
                new gg.modl.minecraft.core.locale.LocaleManager();

        private TestPlatform(Cache cache) {
            this.cache = cache;
        }

        private Platform platform() {
            return (Platform) Proxy.newProxyInstance(
                    Platform.class.getClassLoader(),
                    new Class<?>[] {Platform.class},
                    (proxy, method, args) -> {
                        if ("getCache".equals(method.getName())) return cache;
                        if ("getAbstractPlayer".equals(method.getName()))
                            return new AbstractPlayer((UUID) args[0], "ModlStaff", true);
                        if ("getPlayerWrapper".equals(method.getName())) return null;
                        if ("getLocaleManager".equals(method.getName())) return localeManager;
                        if ("sendMessage".equals(method.getName())) {
                            lastMessage.set((String) args[1]);
                            return null;
                        }
                        if ("runOnMainThread".equals(method.getName())) {
                            mainThreadScheduleCount.incrementAndGet();
                            scheduledTask.set((Runnable) args[0]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private int mainThreadScheduleCount() {
            return mainThreadScheduleCount.get();
        }

        private String lastMessage() {
            return lastMessage.get();
        }

        private void runScheduledTask() {
            scheduledTask.get().run();
        }
    }
}
