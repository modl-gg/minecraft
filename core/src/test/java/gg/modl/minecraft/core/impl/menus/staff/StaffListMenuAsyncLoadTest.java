package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.TestPluginServices;
import gg.modl.minecraft.core.util.Permissions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
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

        OnlinePlayersMenu menu = new OnlinePlayersMenu(platform, new FakeModlHttpClient() {
            @Override
            public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
                return online;
            }

            @Override
            public CompletableFuture<ReportsResponse> getReports(String status) {
                return reports;
            }
        }, VIEWER, "Staff", true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        online.complete(new OnlinePlayersResponse(Collections.emptyList(), 200));
        assertFalse(menu.getDataFuture().isDone());

        reports.complete(new ReportsResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void staffReportsDataFutureCompletesNormallyEvenOnUnsuccessfulResponse() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<ReportsResponse> reports = new CompletableFuture<>();

        StaffReportsMenu menu = new StaffReportsMenu(platform, new FakeModlHttpClient() {
            @Override
            public CompletableFuture<ReportsResponse> getReports(String status) {
                return reports;
            }
        }, VIEWER, "Staff", true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        reports.complete(new ReportsResponse(Collections.emptyList(), 403));
        assertTrue(menu.getDataFuture().isDone());
        assertFalse(menu.getDataFuture().isCompletedExceptionally());
    }

    @Test
    void recentPunishmentsDataFutureCompletesAfterFetch() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<RecentPunishmentsResponse> punishments = new CompletableFuture<>();

        RecentPunishmentsMenu menu = new RecentPunishmentsMenu(platform, new FakeModlHttpClient() {
            @Override
            public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
                return punishments;
            }
        }, VIEWER, "Staff", true, "https://panel.modl.gg", null);

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

        TicketsMenu menu = new TicketsMenu(platform, new FakeModlHttpClient() {
            @Override
            public CompletableFuture<TicketsResponse> getTickets(String status, String type) {
                return tickets;
            }
        }, VIEWER, "Staff", true, "https://panel.modl.gg", null);

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

        RoleListMenu menu = new RoleListMenu(platform, new FakeModlHttpClient() {
            @Override
            public CompletableFuture<RolesListResponse> getRoles() {
                return roles;
            }
        }, VIEWER, "Staff", true, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        roles.complete(new RolesListResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
    }

    @Test
    void roleListWithoutPermissionHasCompletedDataFuture() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);

        RoleListMenu menu = new RoleListMenu(platform, new FakeModlHttpClient(), VIEWER, "Staff",
                false, "https://panel.modl.gg", null);

        assertNotNull(menu.getDataFuture());
        assertTrue(menu.getDataFuture().isDone());
    }

    private static Platform platform(Cache cache) {
        TestPluginServices.install(cache);
        return new FakePlatform() {
            @Override
            public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
                return new AbstractPlayer(uuid, "Staff", true);
            }
        };
    }
}
