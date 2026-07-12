package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.service.MigrationService;
import lombok.Setter;

import static gg.modl.minecraft.core.util.Java8Collections.orTimeout;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import gg.modl.minecraft.core.util.PluginLogger;
import java.util.stream.Collectors;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;

public class SyncService {
    private static final int INITIAL_SYNC_DELAY_SECONDS = 5;
    private static final long FALLBACK_FETCH_INTERVAL_SECONDS = 60, MIN_FALLBACK_FETCH_INTERVAL_SECONDS = 30,
            MAINTENANCE_INTERVAL_SECONDS = 60;
    private static final long SYNC_HTTP_TIMEOUT_SECONDS = 5, SYNC_TASK_TIMEOUT_SECONDS = 10, EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]{2,16}$");

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final PluginLogger logger;
    private final int fallbackFetchRateSeconds;
    private final boolean debugMode;

    private final PunishmentExecutor punishmentExecutor;
    private final NotificationService notificationService;
    private final LogUploadService logUploadService;
    private final StaffSyncProcessor staffSyncProcessor;
    private final Staff2faSyncProcessor staff2faSyncProcessor;
    private final RefreshCoordinator refreshCoordinator;
    private final MigrationServiceFactory migrationServiceFactory;

    private volatile String lastSyncTimestamp;
    private volatile ScheduledExecutorService syncExecutor;
    private volatile ExecutorService taskExecutor;
    private volatile MigrationService migrationService;
    @Setter private StatWipeExecutor statWipeExecutor;
    private volatile boolean isRunning = false;
    private volatile boolean realtimeConnected = false;
    private final AtomicBoolean forcedSyncPending = new AtomicBoolean(false);

    public SyncService(SyncServiceContext context) {
        this.platform = context.getPlatform();
        this.httpClientHolder = context.getHttpClientHolder();
        this.cache = context.getCache();
        this.logger = context.getLogger();
        this.fallbackFetchRateSeconds = context.getPollingRateSeconds();
        this.debugMode = context.isDebugMode();

        this.punishmentExecutor = new PunishmentExecutor(platform, httpClientHolder, cache, logger,
                context.getPunishmentMessageService(), debugMode);
        this.notificationService = new NotificationService(platform, httpClientHolder, cache, logger,
                context.getLocaleManager(), context.getPanelUrl(), debugMode);
        this.logUploadService = new LogUploadService(httpClientHolder, context.getChatCommandLogService(), logger, debugMode);
        this.staffSyncProcessor = new StaffSyncProcessor(platform, cache, logger, context.getLocaleManager(),
                context.getStaff2faService(), debugMode);
        this.staff2faSyncProcessor = new Staff2faSyncProcessor(platform, cache, logger, context.getLocaleManager(),
                context.getStaff2faService());
        this.refreshCoordinator = new RefreshCoordinator(context.getStaffPermissionService(), httpClientHolder, logger, debugMode);
        this.migrationServiceFactory = new MigrationServiceFactory(platform, httpClientHolder,
                context.getDatabaseConfig(), context.getDataFolder(), context.getLocaleManager(), logger);
    }

    public interface PunishmentTypesRefreshListener {
        void onPunishmentTypesRefreshed(List<PunishmentTypesResponse.PunishmentTypeData> types);
    }

    public void addPunishmentTypesListener(PunishmentTypesRefreshListener listener) {
        refreshCoordinator.addPunishmentTypesListener(listener);
    }

    public void start() {
        if (isRunning) {
            logger.warning("Sync service is already running");
            return;
        }

        long fallbackInterval = Math.max(MIN_FALLBACK_FETCH_INTERVAL_SECONDS,
                fallbackFetchRateSeconds > 0 ? fallbackFetchRateSeconds : FALLBACK_FETCH_INTERVAL_SECONDS);

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
        syncExecutor.scheduleWithFixedDelay(this::runFallbackFetchIfDisconnected,
                INITIAL_SYNC_DELAY_SECONDS, fallbackInterval, TimeUnit.SECONDS);
        syncExecutor.scheduleWithFixedDelay(this::runMaintenance,
                MAINTENANCE_INTERVAL_SECONDS, MAINTENANCE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logUploadService.start();
        isRunning = true;
        if (debugMode) logger.info("modl.gg Sync service started - websocket-push driven (fallback fetch every " + fallbackInterval + "s while disconnected)");
    }

    public void stop() {
        if (!isRunning) return;

        logUploadService.stop();
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

    public void setRealtimeConnected(boolean connected) {
        this.realtimeConnected = connected;
    }

    private void runFallbackFetchIfDisconnected() {
        if (realtimeConnected) {
            if (debugMode) logger.info("Skipping fallback fetch; websocket connected");
            return;
        }
        performSync();
    }

    private void runMaintenance() {
        try {
            for (CachedProfile profile : cache.getRegistry().getAllProfiles()) {
                profile.cleanupExpiredNotifications();
            }
            PluginServices.chatInput().cleanupExpired();
        } catch (Exception e) {
            logger.warning("Sync maintenance pass failed: " + e.getMessage());
        }
    }

    public void forceSync(String reason) {
        runBaselineFetch(reason);
    }

    public void runBaselineFetch(String reason) {
        ScheduledExecutorService executor = syncExecutor;
        if (!isRunning || executor == null || executor.isShutdown()) {
            if (debugMode) logger.info("Skipping baseline fetch while sync service is stopped: " + reason);
            return;
        }

        try {
            if (!forcedSyncPending.compareAndSet(false, true)) {
                if (debugMode) logger.info("Coalescing baseline fetch while another is pending: " + reason);
                return;
            }
            executor.execute(() -> {
                try {
                    if (debugMode) logger.info("Running baseline fetch: " + reason);
                    performSync();
                } finally {
                    forcedSyncPending.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            forcedSyncPending.set(false);
            if (debugMode) logger.warning("Baseline fetch rejected: " + e.getMessage());
        }
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
        SyncRequest.ServerStatus serverStatus = new SyncRequest.ServerStatus(onlinePlayers.size(), platform.getMaxPlayers(),
                platform.getServerVersion(), platform.getPlatformType(), PluginInfo.VERSION, System.currentTimeMillis());
        return new SyncRequest(lastSyncTimestamp, buildOnlinePlayersList(onlinePlayers), platform.getServerName(),
                StartupClient.getServerInstanceId(), null, null, serverStatus);
    }

    static <T> List<T> filterByUsername(List<T> entries, Function<T, String> usernameAccessor) {
        if (entries == null || entries.isEmpty()) return new ArrayList<>();
        List<T> retained = new ArrayList<>(entries.size());
        for (T entry : entries) {
            if (entry == null) continue;
            String username = usernameAccessor.apply(entry);
            if (username != null && MINECRAFT_USERNAME_PATTERN.matcher(username).matches()) retained.add(entry);
        }
        return retained;
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

        staffSyncProcessor.reconcileActiveStaff(data.getActiveStaffMembers());

        for (SyncResponse.PlayerNotification notification : data.getPlayerNotifications()) notificationService.processPlayerNotification(notification);

        if (data.getStaffNotifications() != null) {
            for (SyncResponse.StaffNotification staffNotif : data.getStaffNotifications()) notificationService.processStaffNotification(staffNotif);
        }

        if (data.getMigrationTask() != null) processMigrationTask(data.getMigrationTask());

        refreshCoordinator.onSyncTimestamps(data.getStaffPermissionsUpdatedAt(), data.getPunishmentTypesUpdatedAt());

        staff2faSyncProcessor.processVerifications(data.getStaff2faVerifications());
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

            startLiteBansMigration(taskId);
        } catch (Exception e) {
            logger.severe("Error processing migration task: " + e.getMessage());
        }
    }

    private void startLiteBansMigration(String taskId) {
        migrationService.exportLiteBansData(taskId).thenAccept(jsonFile ->
                handleMigrationExportResult(taskId, jsonFile)
        ).exceptionally(throwable -> {
            logger.severe("Task " + taskId + " failed: " + throwable.getMessage());
            return null;
        });
    }

    private void handleMigrationExportResult(String taskId, File jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            logger.warning("Task " + taskId + " export failed - no file generated");
            return;
        }
        migrationService.uploadMigrationFile(jsonFile, taskId).thenAccept(success -> {
            if (!success) logger.warning("Task " + taskId + " upload failed");
        });
    }

    private boolean ensureMigrationServiceInitialized() {
        if (migrationService != null) return true;
        try {
            migrationService = migrationServiceFactory.create();
            return migrationService != null;
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

    public void deliverPendingNotifications(UUID playerUuid) {
        notificationService.deliverPendingNotifications(playerUuid);
    }

    public boolean submitRealtimeApply(Runnable apply) {
        ScheduledExecutorService executor = syncExecutor;
        if (!isRunning || executor == null || executor.isShutdown()) return false;
        try {
            executor.execute(apply);
            return true;
        } catch (RejectedExecutionException e) {
            if (debugMode) logger.warning("Realtime apply rejected: " + e.getMessage());
            return false;
        }
    }

    public void applyPendingPunishment(SyncResponse.PendingPunishment pending) {
        punishmentExecutor.processPendingPunishment(pending);
    }

    public void applyModifiedPunishment(SyncResponse.ModifiedPunishment modified) {
        punishmentExecutor.processModifiedPunishment(modified);
    }

    public void applyPlayerNotification(SyncResponse.PlayerNotification notification) {
        notificationService.processPlayerNotification(notification);
    }

    public void applyStaffNotification(SyncResponse.StaffNotification notification) {
        notificationService.processStaffNotification(notification);
    }

    public void applyStaff2faVerification(SyncResponse.Staff2faVerification verification) {
        staff2faSyncProcessor.processVerifications(listOf(verification));
    }

    public void applyMigrationTask(SyncResponse.MigrationTask task) {
        if (task != null) processMigrationTask(task);
    }

    public void applyActiveStaffMember(SyncResponse.ActiveStaffMember staffMember) {
        staffSyncProcessor.processActiveStaffMember(staffMember);
    }

    public void applyStatWipe(SyncResponse.PendingStatWipe statWipe) {
        executeStatWipeFromLogin(statWipe);
    }

    public void refreshStaffPermissionsNow() {
        refreshCoordinator.refreshStaffPermissions();
    }

    public void refreshPunishmentTypesNow() {
        refreshCoordinator.refreshPunishmentTypes();
    }
}
