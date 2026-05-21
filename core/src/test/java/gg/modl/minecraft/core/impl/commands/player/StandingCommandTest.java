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
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    void standing_returns_without_waiting_for_preview_and_types_requests() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174100");
        CompletableFuture<PlayerProfileResponse> profileFuture = CompletableFuture.completedFuture(profileResponse(playerUuid));
        CompletableFuture<PunishmentPreviewResponse> previewFuture = new CompletableFuture<>();
        CompletableFuture<PunishmentTypesResponse> typesFuture = new CompletableFuture<>();
        TestPlatform platform = new TestPlatform(playerUuid);
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
    void standing_replies_error_without_display_when_player_wrapper_is_missing() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174101");
        TestPlatform platform = new TestPlatform(playerUuid);
        platform.wrapper = null;
        TestStandingCommand command = command(platform, httpClient(
                CompletableFuture.completedFuture(profileResponse(playerUuid)),
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);

        assertEquals(2, platform.mainThreadTasks.get());
        assertEquals(0, command.displayCount.get());
        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
    }

    @Test
    void standing_schedules_error_reply_when_profile_response_is_null() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174102");
        TestPlatform platform = new TestPlatform(playerUuid);
        platform.autoRunMainThreadTasks = false;
        CompletableFuture<PlayerProfileResponse> profileFuture = new CompletableFuture<>();
        TestStandingCommand command = command(platform, httpClient(
                profileFuture,
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);
        profileFuture.complete(null);

        assertEquals(2, platform.mainThreadTasks.get());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runQueuedMainThreadTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    @Test
    void standing_schedules_error_reply_when_profile_account_is_null() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174103");
        TestPlatform platform = new TestPlatform(playerUuid);
        platform.autoRunMainThreadTasks = false;
        PlayerProfileResponse response = profileResponse(playerUuid);
        setProfileFieldToNull(response);
        TestStandingCommand command = command(platform, httpClient(
                CompletableFuture.completedFuture(response),
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);

        assertEquals(2, platform.mainThreadTasks.get());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runQueuedMainThreadTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    @Test
    void standing_schedules_error_reply_when_profile_request_fails() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174104");
        TestPlatform platform = new TestPlatform(playerUuid);
        platform.autoRunMainThreadTasks = false;
        CompletableFuture<PlayerProfileResponse> profileFuture = new CompletableFuture<>();
        TestStandingCommand command = command(platform, httpClient(
                profileFuture,
                CompletableFuture.completedFuture(successfulPreview()),
                CompletableFuture.completedFuture(successfulTypes())
        ));
        TestActor actor = new TestActor(playerUuid);

        command.standing(actor);
        profileFuture.completeExceptionally(new IllegalStateException("boom"));

        assertEquals(2, platform.mainThreadTasks.get());
        assertEquals(Collections.emptyList(), actor.messages);

        platform.runQueuedMainThreadTasks();

        assertEquals(Arrays.asList(message("standing.loading"), message("standing.error")), actor.messages);
        assertEquals(0, command.displayCount.get());
    }

    private TestStandingCommand command(TestPlatform platform, ModlHttpClient httpClient) {
        LocaleManager localeManager = new LocaleManager("en_US") {
            @Override
            public String getMessage(String path) {
                return message(path);
            }

            @Override
            public String getMessage(String path, Map<String, String> placeholders) {
                return message(path);
            }
        };
        ConfigManager configManager = new ConfigManager(tempDir, noopLogger());
        Cache cache = new Cache(new CachedProfileRegistry());
        return new TestStandingCommand(new HttpClientHolder(httpClient), platform.asPlatform(), localeManager, configManager, cache);
    }

    private static ModlHttpClient httpClient(
            CompletableFuture<PlayerProfileResponse> profileFuture,
            CompletableFuture<PunishmentPreviewResponse> previewFuture,
            CompletableFuture<PunishmentTypesResponse> typesFuture
    ) {
        return (ModlHttpClient) Proxy.newProxyInstance(
                ModlHttpClient.class.getClassLoader(),
                new Class<?>[]{ModlHttpClient.class},
                (proxy, method, args) -> {
                    if ("getPlayerProfile".equals(method.getName())) return profileFuture;
                    if ("getPunishmentPreview".equals(method.getName())) return previewFuture;
                    if ("getPunishmentTypes".equals(method.getName())) return typesFuture;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
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

    private static PluginLogger noopLogger() {
        return (PluginLogger) Proxy.newProxyInstance(
                PluginLogger.class.getClassLoader(),
                new Class<?>[]{PluginLogger.class},
                (proxy, method, args) -> null
        );
    }

    private static PlayerProfileResponse profileResponse(UUID playerUuid) {
        PlayerProfileResponse response = new PlayerProfileResponse(new Account(
                "player-1",
                playerUuid,
                Collections.singletonList(new Account.Username("modlplayer", null)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()
        ));
        response.setStatus(200);
        return response;
    }

    private static PunishmentPreviewResponse successfulPreview() {
        PunishmentPreviewResponse response = new PunishmentPreviewResponse();
        response.setStatus(200);
        return response;
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

    private static class TestPlatform {
        private final UUID playerUuid;
        private final AtomicInteger mainThreadTasks = new AtomicInteger();
        private final Queue<Runnable> queuedMainThreadTasks = new ArrayDeque<>();
        private boolean autoRunMainThreadTasks = true;
        private AbstractPlayer abstractPlayer;
        private CirrusPlayerWrapper wrapper;

        TestPlatform(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.abstractPlayer = new AbstractPlayer(playerUuid, "modlplayer", true);
            this.wrapper = playerWrapper(playerUuid);
        }

        private Platform asPlatform() {
            return (Platform) Proxy.newProxyInstance(
                    Platform.class.getClassLoader(),
                    new Class<?>[]{Platform.class},
                    (proxy, method, args) -> {
                        if ("getAbstractPlayer".equals(method.getName()) && args[0] instanceof UUID)
                            return playerUuid.equals(args[0]) ? abstractPlayer : null;
                        if ("getPlayerWrapper".equals(method.getName()))
                            return playerUuid.equals(args[0]) ? wrapper : null;
                        if ("runOnMainThread".equals(method.getName())) {
                            mainThreadTasks.incrementAndGet();
                            Runnable task = (Runnable) args[0];
                            if (autoRunMainThreadTasks) {
                                task.run();
                            } else {
                                queuedMainThreadTasks.add(task);
                            }
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void runQueuedMainThreadTasks() {
            Runnable task;
            while ((task = queuedMainThreadTasks.poll()) != null) {
                task.run();
            }
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
