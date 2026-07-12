package gg.modl.minecraft.core.impl.commands.player;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.impl.menus.StandingMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.MapLocaleManager;
import gg.modl.minecraft.core.support.RecordingPluginLogger;
import gg.modl.minecraft.core.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class StandingCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void standingReturnsWithoutWaitingForPreviewAndTypesRequests() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174100");
        CompletableFuture<PlayerProfileResponse> profileFuture = CompletableFuture.completedFuture(profileResponse(playerUuid));
        CompletableFuture<PunishmentPreviewResponse> previewFuture = new CompletableFuture<>();
        CompletableFuture<PunishmentTypesResponse> typesFuture = new CompletableFuture<>();
        FakePlatform platform = platform(playerUuid, true);
        TestStandingCommand command = command(platform, httpClient(profileFuture, previewFuture, typesFuture));
        TestActor actor = new TestActor(playerUuid);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "standing-command-test");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Future<?> invocation = executor.submit(() -> command.standing(actor));

            assertDoesNotThrow(() -> invocation.get(200, TimeUnit.MILLISECONDS));
            assertEquals(Collections.singletonList(message("standing.loading")), actor.messages);
            assertEquals(0, command.displayCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void standingRepliesErrorWithoutDisplayWhenPlayerWrapperIsMissing() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174101");
        FakePlatform platform = new FakePlatform().register(new AbstractPlayer(playerUuid, "modlplayer", true));
        TestStandingCommand command = command(platform, httpClient(
                CompletableFuture.completedFuture(profileResponse(playerUuid)),
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);

        assertEquals(2, platform.mainThreadScheduleCount());
        assertEquals(0, command.displayCount.get());
        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
    }

    @Test
    void standingSchedulesErrorReplyWhenProfileResponseIsNull() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174102");
        FakePlatform platform = platform(playerUuid, false);
        CompletableFuture<PlayerProfileResponse> profileFuture = new CompletableFuture<>();
        TestStandingCommand command = command(platform, httpClient(
                profileFuture,
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);
        profileFuture.complete(null);

        assertEquals(2, platform.mainThreadScheduleCount());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runScheduledTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    @Test
    void standingSchedulesErrorReplyWhenProfileAccountIsNull() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174103");
        FakePlatform platform = platform(playerUuid, false);
        PlayerProfileResponse response = profileResponse(playerUuid);
        setProfileFieldToNull(response);
        TestStandingCommand command = command(platform, httpClient(
                CompletableFuture.completedFuture(response),
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);

        assertEquals(2, platform.mainThreadScheduleCount());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runScheduledTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    @Test
    void standingSchedulesErrorReplyWhenProfileRequestFails() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174104");
        FakePlatform platform = platform(playerUuid, false);
        CompletableFuture<PlayerProfileResponse> profileFuture = new CompletableFuture<>();
        TestStandingCommand command = command(platform, httpClient(
                profileFuture,
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);
        profileFuture.completeExceptionally(new IllegalStateException("boom"));

        assertEquals(2, platform.mainThreadScheduleCount());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runScheduledTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    private static FakePlatform platform(UUID playerUuid, boolean autoRunMainThread) {
        return new FakePlatform()
                .register(new AbstractPlayer(playerUuid, "modlplayer", true))
                .registerWrapper(playerUuid, playerWrapper(playerUuid))
                .autoRunMainThread(autoRunMainThread);
    }

    private TestStandingCommand command(FakePlatform platform, ModlHttpClient httpClient) {
        LocaleManager localeManager = new MapLocaleManager().withFallback(StandingCommandTest::message);
        ConfigManager configManager = new ConfigManager(tempDir, new RecordingPluginLogger());
        Cache cache = new Cache(new CachedProfileRegistry());
        return new TestStandingCommand(new HttpClientHolder(httpClient), platform, localeManager, configManager, cache);
    }

    private static ModlHttpClient httpClient(
            CompletableFuture<PlayerProfileResponse> profileFuture,
            CompletableFuture<PunishmentPreviewResponse> previewFuture,
            CompletableFuture<PunishmentTypesResponse> typesFuture
    ) {
        return new FakeModlHttpClient() {
            @Override
            public CompletableFuture<PlayerProfileResponse> getPlayerProfile(UUID uuid) {
                return profileFuture;
            }

            @Override
            public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(UUID playerUuid, int typeOrdinal) {
                return previewFuture;
            }

            @Override
            public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
                return typesFuture;
            }
        };
    }

    private static CirrusPlayerWrapper playerWrapper(UUID playerUuid) {
        return (CirrusPlayerWrapper) Proxy.newProxyInstance(
                CirrusPlayerWrapper.class.getClassLoader(),
                new Class<?>[]{CirrusPlayerWrapper.class},
                (proxy, method, args) -> {
                    if ("uuid".equals(method.getName())) return playerUuid;
                    if ("protocolVersion".equals(method.getName())) return 0;
                    if ("handle".equals(method.getName())) return null;
                    return null;
                }
        );
    }

    private static PlayerProfileResponse profileResponse(UUID playerUuid) {
        return TestAccounts.profileResponse(playerUuid, "modlplayer");
    }

    private static PunishmentPreviewResponse successfulPreview() {
        return PunishmentPreviewResponse.builder().success(true).build();
    }

    private static PunishmentTypesResponse successfulTypes() {
        return new PunishmentTypesResponse(Collections.emptyList(), 200);
    }

    private static String message(String path) {
        return "message:" + path;
    }

    private static void setProfileFieldToNull(PlayerProfileResponse response) {
        try {
            Field profile = PlayerProfileResponse.class.getDeclaredField("profile");
            profile.setAccessible(true);
            profile.set(response, null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static class TestStandingCommand extends StandingCommand {
        private final AtomicInteger displayCount = new AtomicInteger();

        TestStandingCommand(HttpClientHolder httpClientHolder, Platform platform, LocaleManager localeManager,
                            ConfigManager configManager, Cache cache) {
            super(httpClientHolder, platform, localeManager, configManager, cache);
        }

        @Override
        protected StandingMenu createMenu(ModlHttpClient httpClient, UUID uuid, String username, Account account,
                                          PunishmentPreviewResponse previewData,
                                          Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal) {
            return null;
        }

        @Override
        protected void displayMenu(StandingMenu menu, CirrusPlayerWrapper player) {
            displayCount.incrementAndGet();
        }
    }

    private static class TestActor implements CommandActor {
        private final UUID uuid;
        private final List<String> messages = new CopyOnWriteArrayList<>();

        TestActor(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public String name() {
            return "modlplayer";
        }

        @Override
        public UUID uniqueId() {
            return uuid;
        }

        @Override
        public void reply(String message) {
            messages.add(message);
        }

        @Override
        public void sendRawMessage(String message) {
            messages.add(message);
        }

        @Override
        public void sendRawError(String message) {
            fail(message);
        }

        @Override
        public Lamp<?> lamp() {
            return null;
        }
    }
}
