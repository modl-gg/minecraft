package gg.modl.minecraft.core.impl.menus.util;

import org.junit.jupiter.api.Test;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PlayerHeadItemBuilderTest {
    @Test
    void renderLoreLines_tolerates_missing_ip_geo_fields() {
        Map<String, String> vars = new HashMap<>();
        vars.put("region", null);
        vars.put("country", null);

        List<String> rendered = assertDoesNotThrow(() -> PlayerHeadItemBuilder.renderLoreLines(
            List.of("&7Region: &f{region} (Country: {country})"),
            vars
        ));

        assertEquals(List.of("&7Region: &fUnknown (Country: Unknown)"), rendered);
    }

    @Test
    void create_returns_immediately_when_texture_lookup_is_pending() throws Exception {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174400");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = account(targetUuid);

        withNonRunningWebPlayerExecutor(() -> assertTimeoutPreemptively(
                Duration.ofMillis(200),
                () -> PlayerHeadItemBuilder.create(platform, account, "modltarget", targetUuid)
        ));
    }

    @Test
    void create_caches_async_texture_lookup_for_subsequent_renders() throws Exception {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174401");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = account(targetUuid);
        CompletableFuture<WebPlayer> textureFuture = new CompletableFuture<>();
        AtomicInteger lookupCount = new AtomicInteger();

        PlayerHeadItemBuilder.setTextureLookupForTesting(uuid -> {
            lookupCount.incrementAndGet();
            return textureFuture;
        });
        try {
            PlayerHeadItemBuilder.create(platform, account, "modltarget", targetUuid);

            awaitCondition(() -> lookupCount.get() == 1);
            assertEquals(1, lookupCount.get());
            assertNull(cache.getSkinTexture(targetUuid));

            textureFuture.complete(new WebPlayer("modltarget", targetUuid, "skin-id", "texture-value", true));

            awaitCondition(() -> "texture-value".equals(cache.getSkinTexture(targetUuid)));
            assertEquals("texture-value", cache.getSkinTexture(targetUuid));

            PlayerHeadItemBuilder.create(platform, account, "modltarget", targetUuid);

            assertEquals(1, lookupCount.get());
        } finally {
            PlayerHeadItemBuilder.setTextureLookupForTesting(null);
        }
    }

    @Test
    void create_does_not_run_blocking_texture_lookup_on_caller() {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174402");
        Cache cache = new Cache(new CachedProfileRegistry());
        Platform platform = platform(cache);
        Account account = account(targetUuid);
        CountDownLatch releaseLookup = new CountDownLatch(1);

        PlayerHeadItemBuilder.setTextureLookupForTesting(uuid -> {
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
                    () -> PlayerHeadItemBuilder.create(platform, account, "modltarget", targetUuid)
            );
        } finally {
            releaseLookup.countDown();
            PlayerHeadItemBuilder.setTextureLookupForTesting(null);
        }
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static Account account(UUID targetUuid) {
        return new Account(
                "player-1",
                targetUuid,
                List.of(new Account.Username("modltarget", null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private static Platform platform(Cache cache) {
        LocaleManager localeManager = new LocaleManager();
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("getCache".equals(method.getName())) return cache;
                    if ("getLocaleManager".equals(method.getName())) return localeManager;
                    if ("getPlayerServer".equals(method.getName())) return "test";
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static void withNonRunningWebPlayerExecutor(ThrowingRunnable action) throws Exception {
        Field field = WebPlayer.class.getDeclaredField("LOOKUP_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor original = (ThreadPoolExecutor) field.get(null);
        ThreadPoolExecutor nonRunningExecutor = new NonRunningExecutor();
        field.set(null, nonRunningExecutor);
        try {
            action.run();
        } finally {
            field.set(null, original);
            nonRunningExecutor.shutdownNow();
        }
    }

    private static class NonRunningExecutor extends ThreadPoolExecutor {
        private NonRunningExecutor() {
            super(0, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        public void execute(Runnable command) {}
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
