package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;

import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.MigrationService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.database.JdbcDatabaseProvider;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import gg.modl.minecraft.core.util.PluginLogger;
import java.util.stream.Collectors;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public class SyncService {
    private static final int MIN_POLLING_RATE_SECONDS = 1,
            INITIAL_SYNC_DELAY_SECONDS = 5, MAINTENANCE_CYCLE_INTERVAL = 60;
    private static final long SYNC_HTTP_TIMEOUT_SECONDS = 5, SYNC_TASK_TIMEOUT_SECONDS = 10, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final PluginLogger logger;
    private final LocaleManager localeManager;
    private final String apiUrl, apiKey;
    private final File dataFolder;
    private final DatabaseConfig databaseConfig;
    private final PunishmentExecutor punishmentExecutor;
    private final NotificationService notificationService;
    private final List<PunishmentTypesRefreshListener> punishmentTypesListeners = new CopyOnWriteArrayList<>();
    private final Staff2faService staff2faService;
    private final ChatCommandLogService chatCommandLogService;

    private volatile String lastSyncTimestamp;
    private volatile ScheduledExecutorService syncExecutor;
    private volatile ExecutorService taskExecutor;
    private volatile MigrationService migrationService;
    private volatile Long lastKnownStaffPermissionsTimestamp = null, lastKnownPunishmentTypesTimestamp = null;
    @Setter private StatWipeExecutor statWipeExecutor;
    private final int pollingRateSeconds;
    private final boolean debugMode;
    private volatile boolean isRunning = false;
    private final AtomicInteger syncCycleCount = new AtomicInteger(0);

    public SyncService(@NotNull Platform platform, @NotNull HttpClientHolder httpClientHolder, @NotNull Cache cache,
                       @NotNull PluginLogger logger, @NotNull LocaleManager localeManager, @NotNull String apiUrl,
                       @NotNull String apiKey, @NotNull String panelUrl, int pollingRateSeconds, @NotNull File dataFolder,
                       DatabaseConfig databaseConfig, boolean debugMode, Staff2faService staff2faService,
                       ChatCommandLogService chatCommandLogService) {
        this.platform = platform;
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.logger = logger;
        this.localeManager = localeManager;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.pollingRateSeconds = pollingRateSeconds;
        this.dataFolder = dataFolder;
        this.databaseConfig = databaseConfig;
        this.debugMode = debugMode;
        this.staff2faService = staff2faService;
        this.chatCommandLogService = chatCommandLogService;
        this.punishmentExecutor = new PunishmentExecutor(platform, httpClientHolder, cache, logger, localeManager, debugMode);
        this.notificationService = new NotificationService(platform, httpClientHolder, cache, logger, localeManager, panelUrl, debugMode);
    }

    public interface PunishmentTypesRefreshListener {
        void onPunishmentTypesRefreshed(List<PunishmentTypesResponse.PunishmentTypeData> types);
    }

    public void addPunishmentTypesListener(PunishmentTypesRefreshListener listener) {
        punishmentTypesListeners.add(listener);
    }

    public void start() {
        if (isRunning) {
            logger.warning("Sync service is already running");
            return;
        }

        int actualPollingRate = Math.max(MIN_POLLING_RATE_SECONDS, pollingRateSeconds);
        if (actualPollingRate != pollingRateSeconds) {
            logger.warning("Polling rate adjusted from " + pollingRateSeconds + " to " + actualPollingRate + " seconds (minimum is " + MIN_POLLING_RATE_SECONDS + ")");
        }

        this.lastSyncTimestamp = Instant.now().toString();
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-sync");
            t.setDaemon(true);
            return t;
        });
        this.taskExecutor = new ThreadPoolExecutor(0, 4, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "modl-sync-task");
            t.setDaemon(true);
            return t;
        });
        notificationService.setExecutor(syncExecutor);
        syncExecutor.scheduleWithFixedDelay(this::performSync, INITIAL_SYNC_DELAY_SECONDS, actualPollingRate, TimeUnit.SECONDS);
        isRunning = true;
        if (debugMode) logger.info("modl.gg Sync service started - syncing every " + actualPollingRate + " seconds");
    }

    public void stop() {
        if (!isRunning) return;

        if (syncExecutor != null) {
            syncExecutor.shutdown();
            try {
                if (!syncExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) syncExecutor.shutdownNow();
            } catch (InterruptedException e) {
                syncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (taskExecutor != null) taskExecutor.shutdownNow();
        if (migrationService != null) migrationService.shutdown();

        isRunning = false;
        if (debugMode) logger.info("modl.gg Sync service stopped");
    }

    private void performSync() {
        final Callable<Void> work = () -> {
            try {
                Collection<AbstractPlayer> onlinePlayers = platform.getOnlinePlayers();
                SyncRequest request = buildSyncRequest(onlinePlayers);

                SyncResponse response = orTimeout(httpClientHolder.getClient().sync(request),
                    SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
                handleSyncResponse(response);
            } catch (CompletionException e) {
                handleSyncException(e);
            } catch (Exception e) {
                logger.warning("Sync request failed: " + e.getMessage());
            } catch (Throwable t) {
                logger.severe("Error during sync: " + t.getMessage());
            }
            return null;
        };

        final Future<Void> future = taskExecutor.submit(work);
        try {
            future.get(SYNC_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warning("Sync task timed out, cancelling...");
            future.cancel(true);
        } catch (Exception e) {
            logger.severe("Sync task failed: " + e.getMessage());
        }
    }

    private SyncRequest buildSyncRequest(Collection<AbstractPlayer> onlinePlayers) {
        SyncRequest request = new SyncRequest();
        request.setLastSyncTimestamp(lastSyncTimestamp);
        request.setOnlinePlayers(buildOnlinePlayersList(onlinePlayers));
        request.setServerName(platform.getServerName());
        if (chatCommandLogService != null) {
            request.setChatLogs(chatCommandLogService.drainChatBuffer());
            request.setCommandLogs(chatCommandLogService.drainCommandBuffer());
        }
        return request;
    }

    private void handleSyncException(CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof PanelUnavailableException) return;
        if (cause instanceof TimeoutException) logger.warning("Sync request timed out");
        else logger.warning("Sync request failed: " + cause.getMessage());
    }

    private List<SyncRequest.OnlinePlayer> buildOnlinePlayersList(Collection<AbstractPlayer> onlinePlayers) {
        return onlinePlayers.stream()
                .map(player -> {
                    CachedProfile profile = cache.getPlayerProfile(player.getUuid());
                    long sessionDuration = profile != null ? profile.getSessionDuration() : 0;
                    return new SyncRequest.OnlinePlayer(
                            player.getUuid().toString(),
                            player.getName(),
                            player.getIpAddress(),
                            sessionDuration
                    );
                })
                .collect(Collectors.toList());
    }

    private void handleSyncResponse(SyncResponse response) {
        this.lastSyncTimestamp = response.getTimestamp();
        SyncResponse.SyncData data = response.getData();

        for (SyncResponse.ModifiedPunishment modified : data.getRecentlyModifiedPunishments()) punishmentExecutor.processModifiedPunishment(modified);
        for (SyncResponse.PendingPunishment pending : data.getPendingPunishments()) punishmentExecutor.processPendingPunishment(pending);

        Set<UUID> activeStaffUuids = new HashSet<>();
        for (SyncResponse.ActiveStaffMember staffMember : data.getActiveStaffMembers()) {
            processActiveStaffMember(staffMember);
            try {
                activeStaffUuids.add(UUID.fromString(staffMember.getMinecraftUuid()));
            } catch (IllegalArgumentException ignored) {}
        }

        for (CachedProfile profile : cache.getRegistry().getAllProfiles()) {
            if (profile.getStaffMember() != null && !activeStaffUuids.contains(profile.getUuid())) {
                profile.setStaffMember(null);
                cache.removeStaffPermissions(profile.getUuid());
                if (debugMode) logger.info("Evicted stale staff data for " + profile.getUuid());
            }
        }

        for (SyncResponse.PlayerNotification notification : data.getPlayerNotifications()) notificationService.processPlayerNotification(notification);

        if (data.getStaffNotifications() != null) {
            for (SyncResponse.StaffNotification staffNotif : data.getStaffNotifications()) notificationService.processStaffNotification(staffNotif);
        }

        if (data.getMigrationTask() != null) processMigrationTask(data.getMigrationTask());

        refreshIfTimestampChanged(data.getStaffPermissionsUpdatedAt(), lastKnownStaffPermissionsTimestamp,
                "Staff permissions", this::refreshStaffPermissions, ts -> lastKnownStaffPermissionsTimestamp = ts);

        refreshIfTimestampChanged(data.getPunishmentTypesUpdatedAt(), lastKnownPunishmentTypesTimestamp,
                "Punishment types", this::refreshPunishmentTypes, ts -> lastKnownPunishmentTypesTimestamp = ts);

        if (syncCycleCount.incrementAndGet() % MAINTENANCE_CYCLE_INTERVAL == 0) {
            for (CachedProfile profile : cache.getRegistry().getAllProfiles()) {
                profile.cleanupExpiredNotifications();
            }
            platform.getChatInputManager().cleanupExpired();
        }

        processStaff2faVerifications(data.getStaff2faVerifications());
    }

    private void refreshIfTimestampChanged(Long newTimestamp, Long lastKnown, String label,
                                            Runnable refreshAction, Consumer<Long> updateLastKnown) {
        if (newTimestamp == null || newTimestamp.equals(lastKnown)) return;
        if (debugMode) logger.info(label + " changed (timestamp: " + newTimestamp + "), refreshing...");
        refreshAction.run();
        updateLastKnown.accept(newTimestamp);
    }

    private void processStaff2faVerifications(List<SyncResponse.Staff2faVerification> verifications) {
        if (verifications == null) return;
        for (SyncResponse.Staff2faVerification verification : verifications) {
            try {
                UUID uuid = UUID.fromString(verification.getMinecraftUuid());
                staff2faService.handleVerification(uuid);
                logger.info("[Sync] Staff 2FA verified for " + verification.getMinecraftUuid());
                notifyStaff2faVerified(uuid);
            } catch (Exception e) {
                logger.warning("[Sync] Failed to process staff 2FA verification: " + e.getMessage());
            }
        }
    }

    private void notifyStaff2faVerified(UUID uuid) {
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player == null) return;
        platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.verify_success"));
        String inGameName = player.getUsername();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.verified",
                mapOf("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }

    private void refreshStaffPermissions() {
        orTimeout(StaffPermissionLoader.load(httpClientHolder.getClient(), cache, logger, debugMode, true),
            SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshPunishmentTypes() {
        orTimeout(httpClientHolder.getClient().getPunishmentTypes().thenAccept(response -> {
            if (!response.isSuccess()) return;
            for (PunishmentTypesRefreshListener listener : punishmentTypesListeners) {
                try {
                    listener.onPunishmentTypesRefreshed(response.getData());
                } catch (Exception e) {
                    logger.warning("Error notifying punishment types listener: " + e.getMessage());
                }
            }
            if (debugMode) logger.info("Punishment types refreshed: " + response.getData().size() + " types");
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (cause instanceof PanelUnavailableException) logger.warning("Failed to refresh punishment types: Panel temporarily unavailable");
            else logger.warning("Failed to refresh punishment types: " + throwable.getMessage());
            return null;
        }), SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void processMigrationTask(SyncResponse.MigrationTask migrationTask) {
        try {
            if (!ensureMigrationServiceInitialized()) return;

            String taskId = migrationTask.getTaskId();
            String type = migrationTask.getType();

            if (!"litebans".equalsIgnoreCase(type)) {
                logger.warning("Unknown migration type: " + type);
                return;
            }

            migrationService.exportLiteBansData(taskId).thenAccept(jsonFile -> {
                if (jsonFile == null || !jsonFile.exists()) {
                    logger.warning("Task " + taskId + " export failed - no file generated");
                    return;
                }
                migrationService.uploadMigrationFile(jsonFile, taskId).thenAccept(success -> {
                    if (!success) logger.warning("Task " + taskId + " upload failed");
                });
            }).exceptionally(throwable -> {
                logger.severe("Task " + taskId + " failed: " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.severe("Error processing migration task: " + e.getMessage());
        }
    }

    private boolean ensureMigrationServiceInitialized() {
        if (migrationService != null) return true;
        try {
            DatabaseProvider databaseProvider = platform.createLiteBansDatabaseProvider();
            if (databaseProvider == null) {
                databaseProvider = new JdbcDatabaseProvider(databaseConfig, logger);
            }
            migrationService = new MigrationService(logger, httpClientHolder.getClient(), apiUrl, apiKey, dataFolder, databaseProvider, localeManager.getMessage("config.default_reason"));
            return true;
        } catch (Exception e) {
            logger.severe("Failed to initialize migration service: " + e.getMessage());
            return false;
        }
    }

    public boolean isStatWipeAvailable() {
        return statWipeExecutor != null;
    }

    public void executeStatWipeFromLogin(SyncResponse.PendingStatWipe statWipe) {
        if (statWipeExecutor == null) return;
        logger.info("[bridge] Executing for " + statWipe.getUsername() + " (punishment: " + statWipe.getPunishmentId() + ")");

        statWipeExecutor.executeStatWipe(
                statWipe.getUsername(), statWipe.getMinecraftUuid(), statWipe.getPunishmentId(),
                (success, serverName) -> handleStatWipeResult(statWipe, success, serverName)
        );
    }

    private void handleStatWipeResult(SyncResponse.PendingStatWipe statWipe, boolean success, String serverName) {
        if (!success) {
            logger.warning("[bridge] Failed for " + statWipe.getUsername() + ", will retry on next sync");
            return;
        }
        logger.info("[bridge] Completed for " + statWipe.getUsername() + " on " + serverName + ", acknowledging");
        httpClientHolder.getClient().acknowledgeStatWipe(
                new StatWipeAcknowledgeRequest(statWipe.getPunishmentId(), serverName, true))
                .exceptionally(throwable -> {
                    logger.warning("[bridge] Failed to acknowledge for " + statWipe.getPunishmentId() + ": " + throwable.getMessage());
                    return null;
                });
    }

    private void processActiveStaffMember(SyncResponse.ActiveStaffMember staffMember) {
        try {
            UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
            AbstractPlayer player = platform.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                handle2faForStaffMember(uuid, player, staffMember);
                updateStaffMemberCache(uuid, staffMember);
            }
        } catch (Exception e) {
            logger.warning("Error processing staff member data: " + e.getMessage());
        }
    }

    private void handle2faForStaffMember(UUID uuid, AbstractPlayer player, SyncResponse.ActiveStaffMember staffMember) {
        if (staff2faService == null || !staff2faService.isEnabled()) return;
        if (staff2faService.getAuthState(uuid) != Staff2faService.AuthState.PENDING) return;

        if (Boolean.TRUE.equals(staffMember.getTwoFactorSessionValid())) {
            staff2faService.handleVerification(uuid);
            platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.auto_verified"));
            broadcastStaffJoin(uuid, player);
        } else {
            if (staff2faService.markNotified(uuid)) {
                platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.not_verified"));
            }
        }
    }

    private void broadcastStaffJoin(UUID uuid, AbstractPlayer player) {
        String inGameName = player.getUsername();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.join",
                mapOf("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }

    private void updateStaffMemberCache(UUID uuid, SyncResponse.ActiveStaffMember staffMember) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return;

        SyncResponse.ActiveStaffMember existing = profile.getStaffMember();
        boolean isNew = existing == null;
        boolean permissionsChanged = existing != null && !existing.getPermissions().equals(staffMember.getPermissions());

        profile.setStaffMember(staffMember);

        if (debugMode && (isNew || permissionsChanged)) {
            logger.info(String.format("Staff member data %s for %s (%s) - Role: %s, Permissions: %s",
                    isNew ? "loaded" : "updated",
                    staffMember.getMinecraftUsername(), staffMember.getStaffUsername(),
                    staffMember.getStaffRole(), staffMember.getPermissions()));
        }
    }

    public void deliverPendingNotifications(UUID playerUuid) {
        notificationService.deliverPendingNotifications(playerUuid);
    }
}
