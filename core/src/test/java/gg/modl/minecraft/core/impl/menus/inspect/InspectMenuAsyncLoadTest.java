package gg.modl.minecraft.core.impl.menus.inspect;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.TestAccounts;
import gg.modl.minecraft.core.support.TestPluginServices;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectMenuAsyncLoadTest {
    private static final UUID VIEWER = UUID.fromString("123e4567-e89b-12d3-a456-426614174111");
    private static final UUID TARGET = UUID.fromString("123e4567-e89b-12d3-a456-426614174222");

    @Test
    void reportsMenuDataFutureWaitsForFetch() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<ReportsResponse> reports = new CompletableFuture<>();

        ReportsMenu menu = new ReportsMenu(platform, httpClient(reports), VIEWER, "Staff", account(TARGET), null);

        assertNotNull(menu.getDataFuture());
        assertFalse(menu.getDataFuture().isDone());

        reports.complete(new ReportsResponse(Collections.emptyList(), 200));
        assertTrue(menu.getDataFuture().isDone());
        assertFalse(menu.getDataFuture().isCompletedExceptionally());
    }

    @Test
    void reportsMenuDataFutureCompletesNormallyOnUnsuccessfulResponse() {
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        CompletableFuture<ReportsResponse> reports = new CompletableFuture<>();

        ReportsMenu menu = new ReportsMenu(platform, httpClient(reports), VIEWER, "Staff", account(TARGET), null);

        assertFalse(menu.getDataFuture().isDone());

        reports.complete(new ReportsResponse(Collections.emptyList(), 403));
        assertTrue(menu.getDataFuture().isDone());
        assertFalse(menu.getDataFuture().isCompletedExceptionally());
    }

    private static Account account(UUID uuid) {
        return TestAccounts.account(uuid, "modltarget");
    }

    private static ModlHttpClient httpClient(CompletableFuture<ReportsResponse> playerReports) {
        return new FakeModlHttpClient() {
            @Override
            public CompletableFuture<ReportsResponse> getPlayerReports(UUID playerUuid, String status) {
                return playerReports;
            }
        };
    }

    private static Platform platform(Cache cache) {
        TestPluginServices.install(cache);
        return new FakePlatform();
    }
}
