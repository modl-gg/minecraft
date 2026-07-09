package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.util.Permissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffListMenuAsyncLoadTest {
    private static final UUID VIEWER = UUID.fromString("123e4567-e89b-12d3-a456-426614174999");

    @Test
    void onlinePlayersDataFutureWaitsForOnlineAndReportsBeforeCompleting() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<OnlinePlayersResponse> online = new CompletableFuture<>();
        CompletableFuture<ReportsResponse> reports = new CompletableFuture<>();
        Map<String, Object> futures = new HashMap<>();
        futures.put("getOnlinePlayers", online);
        futures.put("getReports", reports);

        OnlinePlayersMenu menu = new OnlinePlayersMenu(platform, httpClient(futures), VIEWER, "Staff",
                true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        online.complete(new OnlinePlayersResponse(Collections.emptyList(), 200));
        // Still waiting on getReports("open").
        assertFalse(menu.getDataFuture().isDone());

        reports.complete(new ReportsResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void staffReportsDataFutureCompletesNormallyEvenOnUnsuccessfulResponse() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<ReportsResponse> reports = new CompletableFuture<>();
        Map<String, Object> futures = new HashMap<>();
        futures.put("getReports", reports);

        StaffReportsMenu menu = new StaffReportsMenu(platform, httpClient(futures), VIEWER, "Staff",
                true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        // Unsuccessful response: the menu must still complete its future normally so it opens empty.
        reports.complete(new ReportsResponse(Collections.emptyList(), 403));
        assertTrue(menu.getDataFuture().isDone());
        assertFalse(menu.getDataFuture().isCompletedExceptionally());
    }

    @Test
    void recentPunishmentsDataFutureCompletesAfterFetch() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<RecentPunishmentsResponse> punishments = new CompletableFuture<>();
        Map<String, Object> futures = new HashMap<>();
        futures.put("getRecentPunishments", punishments);

        RecentPunishmentsMenu menu = new RecentPunishmentsMenu(platform, httpClient(futures), VIEWER, "Staff",
                true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        punishments.complete(new RecentPunishmentsResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void ticketsDataFutureCompletesAfterFetch() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<TicketsResponse> tickets = new CompletableFuture<>();
        Map<String, Object> futures = new HashMap<>();
        futures.put("getTickets", tickets);

        TicketsMenu menu = new TicketsMenu(platform, httpClient(futures), VIEWER, "Staff",
                true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        tickets.complete(new TicketsResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void roleListDataFutureCompletesAfterFetchWithPermission() {
        Cache cache = new Cache(new CachedProfileRegistry());
        cache.cacheStaffPermissions(VIEWER, "Staff", "staff-1", "Admin",
                Collections.singletonList(Permissions.SETTINGS_MODIFY));
        Platform platform = platform(cache);
        CompletableFuture<RolesListResponse> roles = new CompletableFuture<>();
        Map<String, Object> futures = new HashMap<>();
        futures.put("getRoles", roles);

        RoleListMenu menu = new RoleListMenu(platform, httpClient(futures), VIEWER, "Staff",
                true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        roles.complete(new RolesListResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void roleListWithoutPermissionHasCompletedDataFuture() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);

        RoleListMenu menu = new RoleListMenu(platform, httpClient(new HashMap<>()), VIEWER, "Staff",
                false, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertTrue(menu.getDataFuture().isDone());
    }

    private static ModlHttpClient httpClient(Map<String, Object> futuresByMethod) {
        return (ModlHttpClient) Proxy.newProxyInstance(
                ModlHttpClient.class.getClassLoader(),
                new Class<?>[] {ModlHttpClient.class},
                (proxy, method, args) -> {
                    Object future = futuresByMethod.get(method.getName());
                    if (future != null) return future;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Platform platform(Cache cache) {
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("getCache".equals(method.getName())) return cache;
                    if ("getAbstractPlayer".equals(method.getName()))
                        return new AbstractPlayer((UUID) args[0], "Staff", true);
                    if ("getPlayerWrapper".equals(method.getName())) return null;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
