package gg.modl.minecraft.core.impl.menus.util;

import org.junit.jupiter.api.Test;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.integration.mojang.WebPlayer;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.TestAccounts;
import gg.modl.minecraft.core.support.TestPluginServices;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PlayerHeadItemBuilderTest {
    @Test
    void renderLoreLinesToleratesMissingIpGeoFields() {
        Map<String, String> vars = new HashMap<>();
        vars.put("region", null);
        vars.put("country", null);

        List<String> rendered = assertDoesNotThrow(() -> PlayerHeadItemBuilder.renderLoreLines(
            Collections.singletonList("&7Region: &f{region} (Country: {country})"),
            vars
        ));

        assertEquals(Collections.singletonList("&7Region: &fUnknown (Country: Unknown)"), rendered);
    }

    @Test
    void createReturnsImmediatelyWhenTextureLookupIsPending() {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174400");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = TestAccounts.account(targetUuid, "modltarget");
        PlayerHeadItemBuilder builder = new PlayerHeadItemBuilder(uuid -> new CompletableFuture<>());

        assertTimeoutPreemptively(
                Duration.ofMillis(200),
                () -> builder.create(platform, account, "modltarget", targetUuid));
    }

    @Test
    void createCachesAsyncTextureLookupForSubsequentRenders() throws Exception {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174401");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = TestAccounts.account(targetUuid, "modltarget");
        CompletableFuture<WebPlayer> textureFuture = new CompletableFuture<>();
        AtomicInteger lookupCount = new AtomicInteger();
        PlayerHeadItemBuilder builder = new PlayerHeadItemBuilder(uuid -> {
            lookupCount.incrementAndGet();
            return textureFuture;
        });

        builder.create(platform, account, "modltarget", targetUuid);

        awaitCondition(() -> lookupCount.get() == 1);
        assertEquals(1, lookupCount.get());
        assertNull(cache.getSkinTexture(targetUuid));

        textureFuture.complete(new WebPlayer("modltarget", targetUuid, "skin-id", "texture-value", true));

        awaitCondition(() -> "texture-value".equals(cache.getSkinTexture(targetUuid)));
        assertEquals("texture-value", cache.getSkinTexture(targetUuid));

        builder.create(platform, account, "modltarget", targetUuid);

        assertEquals(1, lookupCount.get());
    }

    @Test
    void createDoesNotRunBlockingTextureLookupOnCaller() {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174402");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = TestAccounts.account(targetUuid, "modltarget");
        CountDownLatch releaseLookup = new CountDownLatch(1);
        PlayerHeadItemBuilder builder = new PlayerHeadItemBuilder(uuid -> {
            try {
                releaseLookup.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CompletableFuture.completedFuture(new WebPlayer("modltarget", uuid, "skin-id", "texture-value", true));
        });

        try {
            assertTimeoutPreemptively(
                    Duration.ofMillis(200),
                    () -> builder.create(platform, account, "modltarget", targetUuid));
        } finally {
            releaseLookup.countDown();
        }
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static Platform platform(Cache cache) {
        TestPluginServices.install(cache);
        return new FakePlatform().withServerName("test");
    }
}
