package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.impl.commands.staff.StaffListCommand;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.support.FakeCirrusPlayerWrapper;
import gg.modl.minecraft.core.support.FakeCommandActor;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.TestPluginServices;
import gg.modl.minecraft.core.util.Permissions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

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
        FakePlatform platform = platform(cache);
        CompletableFuture<RolesListResponse> rolesFuture = new CompletableFuture<>();
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();

        StaffListMenu menu = new StaffListMenu(platform, httpClient(rolesFuture, staffFuture),
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
        FakePlatform platform = platform(cache);

        StaffListMenu menu = new StaffListMenu(platform, httpClient(
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
        FakePlatform platform = platform(cache);
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();
        staffFuture.completeExceptionally(new IllegalStateException("backend unavailable"));

        StaffListMenu menu = new StaffListMenu(platform, httpClient(
                CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                staffFuture),
                viewerUuid, "ModlStaff", true, "https://panel.modl.gg", null);

        assertThrows(CompletionException.class, menu.getDataFuture()::join);
    }

    @Test
    void settingsMenuSchedulesLoadedDisplayOnPlatformMainThread() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
        Cache cache = new Cache(new CachedProfileRegistry());
        FakePlatform platform = platform(cache);
        CompletableFuture<Void> dataFuture = new CompletableFuture<>();
        AtomicInteger displayCount = new AtomicInteger();

        MenuAsync.displayWhenLoaded(platform, dataFuture, new FakeCirrusPlayerWrapper(viewerUuid),
                player -> displayCount.incrementAndGet());

        assertEquals(0, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        dataFuture.complete(null);

        assertEquals(1, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        platform.runScheduledTasks();

        assertEquals(1, displayCount.get());
    }

    @Test
    void settingsMenuDoesNotDisplayWhenLoadFails() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174305");
        Cache cache = new Cache(new CachedProfileRegistry());
        FakePlatform platform = platform(cache);
        CompletableFuture<Void> dataFuture = new CompletableFuture<>();
        AtomicInteger displayCount = new AtomicInteger();

        MenuAsync.displayWhenLoaded(platform, dataFuture, new FakeCirrusPlayerWrapper(viewerUuid),
                player -> displayCount.incrementAndGet());

        dataFuture.completeExceptionally(new IllegalStateException("load failed"));

        assertEquals(1, platform.mainThreadScheduleCount());
        assertEquals(0, displayCount.get());

        platform.runScheduledTasks();

        assertNotNull(platform.lastMessage());
        assertEquals(0, displayCount.get());
    }

    @Test
    void staffListCommandSchedulesMenuDisplayOnPlatformMainThreadAfterDataLoads() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
        Cache cache = new Cache(new CachedProfileRegistry());
        FakePlatform platform = platform(cache);
        CompletableFuture<StaffListResponse> staffFuture = new CompletableFuture<>();
        StaffListCommand command = new StaffListCommand(platform, cache, null, null,
                new HttpClientHolder(httpClient(
                        CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                        staffFuture)),
                "https://panel.modl.gg");

        command.staffList(new FakeCommandActor(viewerUuid, "ModlStaff"), null);

        assertEquals(0, platform.mainThreadScheduleCount());

        staffFuture.complete(new StaffListResponse(Collections.emptyList(), 200));

        assertEquals(1, platform.mainThreadScheduleCount());
    }

    @Test
    void staffListCommandDoesNotScheduleMenuDisplayWhenStaffLoadIsNotSuccessful() {
        UUID viewerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174306");
        Cache cache = new Cache(new CachedProfileRegistry());
        FakePlatform platform = platform(cache);
        StaffListCommand command = new StaffListCommand(platform, cache, null, null,
                new HttpClientHolder(httpClient(
                        CompletableFuture.completedFuture(new RolesListResponse(Collections.emptyList(), 200)),
                        CompletableFuture.completedFuture(new StaffListResponse(Collections.emptyList(), 403)))),
                "https://panel.modl.gg");

        command.staffList(new FakeCommandActor(viewerUuid, "ModlStaff"), null);

        assertEquals(0, platform.mainThreadScheduleCount());
    }

    private static FakeModlHttpClient httpClient(
            CompletableFuture<RolesListResponse> rolesFuture,
            CompletableFuture<StaffListResponse> staffFuture
    ) {
        return new FakeModlHttpClient() {
            @Override
            public CompletableFuture<RolesListResponse> getRoles() {
                return rolesFuture;
            }

            @Override
            public CompletableFuture<StaffListResponse> getStaffList() {
                return staffFuture;
            }
        };
    }

    private static FakePlatform platform(Cache cache) {
        TestPluginServices.install(cache);
        return new FakePlatform() {
            @Override
            public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
                return new AbstractPlayer(uuid, "ModlStaff", true);
            }
        }.autoRunMainThread(false);
    }
}
