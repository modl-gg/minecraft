package gg.modl.minecraft.core.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.MigrationService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.database.JdbcDatabaseProvider;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SyncService {
    private static final int MAX_HOVER_TEXT_LENGTH = 200;
    private static final long NOTIFICATION_INITIAL_DELAY_MS = 2000;
    private static final long NOTIFICATION_INTER_DELAY_MS = 1500;
    private static final int MIN_POLLING_RATE_SECONDS = 1;
    private static final int INITIAL_SYNC_DELAY_SECONDS = 5;
    private static final long SYNC_HTTP_TIMEOUT_SECONDS = 5;
    private static final long SYNC_TASK_TIMEOUT_SECONDS = 10;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final int MAINTENANCE_CYCLE_INTERVAL = 60;
    private static final String TICKET_CREATED_TYPE = "TICKET_CREATED";
    private static final String TICKET_TYPE_REPORT = "REPORT";

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final Logger logger;
    private final LocaleManager localeManager;
    private final String apiUrl;
    private final String apiKey;
    private final int pollingRateSeconds;
    private final File dataFolder;
    private final DatabaseConfig databaseConfig;
    private final boolean debugMode;
    private final PunishmentExecutor punishmentExecutor;
    private final List<PunishmentTypesRefreshListener> punishmentTypesListeners = new CopyOnWriteArrayList<>();

    private volatile String lastSyncTimestamp;
    private volatile ScheduledExecutorService syncExecutor;
    private volatile ExecutorService taskExecutor;
    private volatile boolean isRunning = false;
    private volatile MigrationService migrationService;
    private volatile Long lastKnownStaffPermissionsTimestamp = null;
    private volatile Long lastKnownPunishmentTypesTimestamp = null;
    private int syncCycleCount = 0;
    private StatWipeExecutor statWipeExecutor;
    private Staff2faService staff2faService;
    private ChatCommandLogService chatCommandLogService;

    public SyncService(@NotNull Platform platform, @NotNull HttpClientHolder httpClientHolder, @NotNull Cache cache,
                       @NotNull Logger logger, @NotNull LocaleManager localeManager, @NotNull String apiUrl,
                       @NotNull String apiKey, int pollingRateSeconds, @NotNull File dataFolder,
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
    }

    public interface PunishmentTypesRefreshListener {
        void onPunishmentTypesRefreshed(List<PunishmentTypesResponse.PunishmentTypeData> types);
    }

    public void addPunishmentTypesListener(PunishmentTypesRefreshListener listener) {
        punishmentTypesListeners.add(listener);
    }

    public void setStatWipeExecutor(StatWipeExecutor executor) {
        this.statWipeExecutor = executor;
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
        this.taskExecutor = Executors.newCachedThreadPool();

        // Next sync waits until previous finishes; timeouts prevent thread stalls
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

                SyncResponse response = httpClientHolder.getClient().sync(request)
                    .orTimeout(SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
                handleSyncResponse(response);
            } catch (CompletionException e) {
                handleSyncException(e);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Sync request failed", e);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Error during sync: " + t.getMessage(), t);
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
            logger.log(Level.SEVERE, "Sync task failed", e);
        }
    }

    private SyncRequest buildSyncRequest(Collection<AbstractPlayer> onlinePlayers) {
        SyncRequest request = new SyncRequest();
        request.setLastSyncTimestamp(lastSyncTimestamp);
        request.setOnlinePlayers(buildOnlinePlayersList(onlinePlayers));
        request.setServerStatus(buildServerStatus(onlinePlayers));
        request.setServerName(platform.getServerName());
        if (chatCommandLogService != null) {
            request.setChatLogs(chatCommandLogService.drainChatBuffer());
            request.setCommandLogs(chatCommandLogService.drainCommandBuffer());
        }
        return request;
    }

    private void handleSyncException(CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        // Silently ignore PanelUnavailableException to avoid log spam during backend outages
        if (cause instanceof PanelUnavailableException) return;
        if (cause instanceof TimeoutException) logger.warning("Sync request timed out");
        else logger.warning("Sync request failed: " + cause.getMessage());
    }

    private List<SyncRequest.OnlinePlayer> buildOnlinePlayersList(Collection<AbstractPlayer> onlinePlayers) {
        return onlinePlayers.stream()
                .map(player -> new SyncRequest.OnlinePlayer(
                        player.getUuid().toString(),
                        player.getName(),
                        player.getIpAddress(),
                        cache.getSessionDuration(player.getUuid())
                ))
                .collect(Collectors.toList());
    }

    private SyncRequest.ServerStatus buildServerStatus(Collection<AbstractPlayer> onlinePlayers) {
        return new SyncRequest.ServerStatus(
                onlinePlayers.size(),
                platform.getMaxPlayers(),
                platform.getServerVersion(),
                Instant.now().toString()
        );
    }

    private void handleSyncResponse(SyncResponse response) {
        this.lastSyncTimestamp = response.getTimestamp();
        SyncResponse.SyncData data = response.getData();

        // Modifications first (pardons, duration changes) so cache is cleared before new punishments
        for (SyncResponse.ModifiedPunishment modified : data.getRecentlyModifiedPunishments()) punishmentExecutor.processModifiedPunishment(modified);
        for (SyncResponse.PendingPunishment pending : data.getPendingPunishments()) punishmentExecutor.processPendingPunishment(pending);

        if (data.getActiveStaffMembers() != null) {
            for (SyncResponse.ActiveStaffMember staffMember : data.getActiveStaffMembers()) processActiveStaffMember(staffMember);
        }

        for (SyncResponse.PlayerNotification notification : data.getPlayerNotifications()) processPlayerNotification(notification);

        if (data.getStaffNotifications() != null) {
            for (SyncResponse.StaffNotification staffNotif : data.getStaffNotifications()) processStaffNotification(staffNotif);
        }

        if (data.getMigrationTask() != null) processMigrationTask(data.getMigrationTask());

        refreshIfTimestampChanged(data.getStaffPermissionsUpdatedAt(), lastKnownStaffPermissionsTimestamp,
                "Staff permissions", this::refreshStaffPermissions, ts -> lastKnownStaffPermissionsTimestamp = ts);

        refreshIfTimestampChanged(data.getPunishmentTypesUpdatedAt(), lastKnownPunishmentTypesTimestamp,
                "Punishment types", this::refreshPunishmentTypes, ts -> lastKnownPunishmentTypesTimestamp = ts);

        if (++syncCycleCount % MAINTENANCE_CYCLE_INTERVAL == 0) {
            cache.cleanupExpiredNotifications();
            ChatInputManager.cleanupExpired();
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
                logger.log(Level.WARNING, "[Sync] Failed to process staff 2FA verification", e);
            }
        }
    }

    private void notifyStaff2faVerified(UUID uuid) {
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player == null) return;
        platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.verify_success"));
        String inGameName = player.username();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.verified",
                Map.of("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }

    private void refreshStaffPermissions() {
        StaffPermissionLoader.load(httpClientHolder.getClient(), cache, logger, debugMode, true)
            .orTimeout(SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshPunishmentTypes() {
        httpClientHolder.getClient().getPunishmentTypes().thenAccept(response -> {
            if (!response.isSuccess()) return;
            for (PunishmentTypesRefreshListener listener : punishmentTypesListeners) {
                try {
                    listener.onPunishmentTypesRefreshed(response.getData());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error notifying punishment types listener", e);
                }
            }
            if (debugMode) logger.info("Punishment types refreshed: " + response.getData().size() + " types");
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (cause instanceof PanelUnavailableException) logger.warning("Failed to refresh punishment types: Panel temporarily unavailable");
            else logger.warning("Failed to refresh punishment types: " + throwable.getMessage());
            return null;
        }).orTimeout(SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void processMigrationTask(SyncResponse.MigrationTask migrationTask) {
        try {
            if (debugMode) logger.info("Processing migration task " + migrationTask.getTaskId() + " (type: " + migrationTask.getType() + ")");

            if (!ensureMigrationServiceInitialized()) return;

            String taskId = migrationTask.getTaskId();
            String type = migrationTask.getType();

            if (!"litebans".equalsIgnoreCase(type)) {
                logger.warning("[Migration] Unknown migration type: " + type);
                return;
            }

            migrationService.exportLiteBansData(taskId).thenAccept(jsonFile -> {
                if (jsonFile == null || !jsonFile.exists()) {
                    logger.warning("[Migration] Task " + taskId + " export failed - no file generated");
                    return;
                }
                migrationService.uploadMigrationFile(jsonFile, taskId).thenAccept(success -> {
                    if (success && debugMode) logger.info("[Migration] Task " + taskId + " completed successfully");
                    else if (!success) logger.warning("[Migration] Task " + taskId + " upload failed");
                });
            }).exceptionally(throwable -> {
                logger.log(Level.SEVERE, "[Migration] Task " + taskId + " failed: " + throwable.getMessage(), throwable);
                return null;
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Migration] Error processing migration task: " + e.getMessage(), e);
        }
    }

    private boolean ensureMigrationServiceInitialized() {
        if (migrationService != null) return true;
        try {
            DatabaseProvider databaseProvider = platform.createLiteBansDatabaseProvider();
            if (databaseProvider == null) {
                if (debugMode) logger.info("[Migration] LiteBans not available, using JDBC connection");
                databaseProvider = new JdbcDatabaseProvider(databaseConfig, logger);
            }
            migrationService = new MigrationService(logger, httpClientHolder.getClient(), apiUrl, apiKey, dataFolder, databaseProvider);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Migration] Failed to initialize migration service: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean isStatWipeAvailable() {
        return statWipeExecutor != null;
    }

    public void executeStatWipeFromLogin(SyncResponse.PendingStatWipe statWipe) {
        if (statWipeExecutor == null) return;
        logger.info("[StatWipe] Executing for " + statWipe.getUsername() + " (punishment: " + statWipe.getPunishmentId() + ")");

        statWipeExecutor.executeStatWipe(
                statWipe.getUsername(), statWipe.getMinecraftUuid(), statWipe.getPunishmentId(),
                (success, serverName) -> handleStatWipeResult(statWipe, success, serverName)
        );
    }

    private void handleStatWipeResult(SyncResponse.PendingStatWipe statWipe, boolean success, String serverName) {
        if (!success) {
            logger.warning("[StatWipe] Failed for " + statWipe.getUsername() + " — will retry on next sync");
            return;
        }
        logger.info("[StatWipe] Completed for " + statWipe.getUsername() + " on " + serverName + " — acknowledging");
        httpClientHolder.getClient().acknowledgeStatWipe(
                new StatWipeAcknowledgeRequest(statWipe.getPunishmentId(), serverName, true))
                .exceptionally(throwable -> {
                    logger.warning("[StatWipe] Failed to acknowledge for " + statWipe.getPunishmentId() + ": " + throwable.getMessage());
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
            logger.log(Level.WARNING, "Error processing staff member data", e);
        }
    }

    private void handle2faForStaffMember(UUID uuid, AbstractPlayer player, SyncResponse.ActiveStaffMember staffMember) {
        if (staff2faService == null || !staff2faService.isEnabled()) return;
        if (staff2faService.getAuthState(uuid) != Staff2faService.AuthState.PENDING) return;

        if (Boolean.TRUE.equals(staffMember.getTwoFactorSessionValid())) {
            // Backend says session is valid — auto-authenticate
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
        String inGameName = player.username();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.join",
                Map.of("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }

    private void updateStaffMemberCache(UUID uuid, SyncResponse.ActiveStaffMember staffMember) {
        SyncResponse.ActiveStaffMember existing = cache.getStaffMember(uuid);
        boolean isNew = existing == null;
        boolean permissionsChanged = existing != null && !existing.getPermissions().equals(staffMember.getPermissions());

        cache.cacheStaffMember(uuid, staffMember);

        if (debugMode && (isNew || permissionsChanged)) {
            logger.info(String.format("Staff member data %s for %s (%s) - Role: %s, Permissions: %s",
                    isNew ? "loaded" : "updated",
                    staffMember.getMinecraftUsername(), staffMember.getStaffUsername(),
                    staffMember.getStaffRole(), staffMember.getPermissions()));
        }
    }

    private void processStaffNotification(SyncResponse.StaffNotification notification) {
        try {
            if (TICKET_CREATED_TYPE.equals(notification.getType()) && notification.getData() != null) {
                processTicketCreatedNotification(notification);
            } else {
                platform.staffBroadcast("&7&o[" + notification.getMessage() + "&7&o]");
            }
            if (debugMode) logger.info("Processed staff notification: " + notification.getMessage());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing staff notification", e);
        }
    }

    /**
     * Gameplay/player reports use /target click action; other types open the ticket URL.
     */
    private void processTicketCreatedNotification(SyncResponse.StaffNotification notification) {
        Map<String, Object> data = notification.getData();
        String ticketUrl = extractString(data, "ticketUrl");
        String subject = extractString(data, "subject");
        String firstReply = extractString(data, "firstReplyContent");
        String ticketType = extractString(data, "ticketType");
        String category = extractString(data, "category");
        String reportedPlayer = extractString(data, "reportedPlayer");

        String hoverText = buildTicketHoverText(subject, firstReply);
        boolean isGameplayReport = TICKET_TYPE_REPORT.equalsIgnoreCase(ticketType)
                && !category.toLowerCase().contains("chat")
                && !reportedPlayer.isEmpty();

        String clickAction;
        String clickValue;
        if (isGameplayReport) {
            clickAction = "run_command";
            clickValue = "/target " + escapeJson(reportedPlayer);
            hoverText += (hoverText.isEmpty() ? "" : "\n\n") + "Click to target " + reportedPlayer;
        } else {
            clickAction = "open_url";
            clickValue = ticketUrl;
        }

        String json = String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"[%s]\",\"color\":\"gray\",\"italic\":true," +
            "\"clickEvent\":{\"action\":\"%s\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"%s\"}}]}",
            escapeJson(notification.getMessage()), clickAction, clickValue, escapeJson(hoverText)
        );
        platform.staffJsonBroadcast(json);
    }

    private String buildTicketHoverText(String subject, String firstReply) {
        StringBuilder hover = new StringBuilder();
        if (!subject.isEmpty()) hover.append(subject);
        if (!firstReply.isEmpty()) {
            if (hover.length() > 0) hover.append("\n\n");
            hover.append(firstReply.length() > MAX_HOVER_TEXT_LENGTH
                    ? firstReply.substring(0, MAX_HOVER_TEXT_LENGTH) + "..."
                    : firstReply);
        }
        return hover.toString();
    }

    private static String extractString(Map<String, Object> data, String key) {
        return data.get(key) instanceof String s ? s : "";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "\\t");
    }

    private void processPlayerNotification(SyncResponse.PlayerNotification notification) {
        try {
            if (debugMode) logger.info("Processing notification " + notification.getId() + " (type: " + notification.getType() + "): " + notification.getMessage());

            String targetPlayerUuid = notification.getTargetPlayerUuid();
            if (targetPlayerUuid == null || targetPlayerUuid.isEmpty()) {
                handleNotificationForAllOnlinePlayers(notification);
                return;
            }

            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(targetPlayerUuid);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid UUID format in notification: " + targetPlayerUuid);
                return;
            }

            if (deliverNotificationToPlayerAndCheck(playerUuid, notification)) {
                acknowledgeNotification(playerUuid, notification.getId());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing player notification: " + e.getMessage(), e);
        }
    }

    /** @return true if the notification was delivered to an online player */
    private boolean deliverNotificationToPlayerAndCheck(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        AbstractPlayer player = platform.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            if (debugMode) logger.info("Player " + playerUuid + " is offline, notification " + notification.getId() + " deferred");
            return false;
        }

        Map<String, Object> data = notification.getData();
        if (data != null && data.containsKey("ticketUrl")) {
            sendClickableTicketNotification(playerUuid, notification, data);
        } else {
            String message = localeManager.getMessage("notification.ticket_reply", Map.of("message", notification.getMessage()));
            platform.runOnMainThread(() -> platform.sendMessage(playerUuid, message));
        }

        if (debugMode) logger.info("Delivered notification " + notification.getId() + " to " + player.getName());
        return true;
    }

    private void sendClickableTicketNotification(UUID playerUuid, SyncResponse.PlayerNotification notification, Map<String, Object> data) {
        String json = buildClickableTicketJson(notification.getMessage(), extractString(data, "ticketUrl"), extractString(data, "ticketId"));
        if (debugMode) logger.info("Sending clickable notification JSON: " + json);
        platform.runOnMainThread(() -> platform.sendJsonMessage(playerUuid, json));
    }

    public void deliverLoginNotification(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        if (debugMode) logger.info("Delivering login notification " + notification.getId() + " to " + playerUuid);
        deliverNotificationToPlayerAndCheck(playerUuid, notification);
    }

    /** Temporary: broadcasts untargeted notifications to all online players. */
    private void handleNotificationForAllOnlinePlayers(SyncResponse.PlayerNotification notification) {
        for (AbstractPlayer player : platform.getOnlinePlayers()) {
            try {
                UUID playerUuid = player.getUuid();
                cache.cacheNotification(playerUuid, notification);
                deliverNotificationToPlayerAndCheck(playerUuid, notification);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error handling notification for player " + player.getName(), e);
            }
        }
    }

    public void deliverPendingNotifications(UUID playerUuid) {
        try {
            List<Cache.PendingNotification> pending = cache.getPendingNotifications(playerUuid);
            if (pending.isEmpty()) return;

            List<Cache.PendingNotification> toProcess = new ArrayList<>(pending);
            if (debugMode) logger.info("Delivering " + toProcess.size() + " pending notifications to " + playerUuid);

            List<String> deliveredIds = new ArrayList<>();
            List<String> expiredIds = new ArrayList<>();
            deliverNotificationsWithDelay(playerUuid, toProcess, deliveredIds, expiredIds);

            for (String id : expiredIds) cache.removeNotification(playerUuid, id);
            for (String id : deliveredIds) cache.removeNotification(playerUuid, id);
            if (!deliveredIds.isEmpty()) acknowledgeNotifications(playerUuid, deliveredIds);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error delivering pending notifications: " + e.getMessage(), e);
        }
    }

    private void deliverPendingNotificationToPlayer(UUID playerUuid, Cache.PendingNotification pending) {
        AbstractPlayer player = platform.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;

        Map<String, Object> data = pending.getData();
        if (data != null && data.containsKey("ticketUrl")) {
            String ticketUrl = extractString(data, "ticketUrl");
            String ticketId = extractString(data, "ticketId");
            String json = buildClickableTicketJson(pending.getMessage(), ticketUrl, ticketId);
            platform.runOnMainThread(() -> platform.sendJsonMessage(playerUuid, json));
        } else {
            String message = pending.getMessage();
            platform.runOnMainThread(() -> platform.sendMessage(playerUuid, message));
        }
    }

    private String buildClickableTicketJson(String message, String ticketUrl, String ticketId) {
        return String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"[Ticket] \",\"color\":\"gold\"}," +
            "{\"text\":\"%s \",\"color\":\"white\"}," +
            "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
            message.replace("\"", "\\\""), ticketUrl, ticketId
        );
    }

    private void deliverNotificationsWithDelay(UUID playerUuid, List<Cache.PendingNotification> notifications,
                                             List<String> deliveredIds, List<String> expiredIds) {
        if (notifications.isEmpty()) return;
        syncExecutor.schedule(() ->
            platform.runOnMainThread(() ->
                deliverNotificationAtIndex(playerUuid, notifications, 0, deliveredIds, expiredIds)),
            NOTIFICATION_INITIAL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Recursively delivers notifications with delays between each one. */
    private void deliverNotificationAtIndex(UUID playerUuid, List<Cache.PendingNotification> notifications,
                                          int index, List<String> deliveredIds, List<String> expiredIds) {
        if (index >= notifications.size()) {
            finalizePendingNotificationDelivery(playerUuid, deliveredIds, expiredIds);
            return;
        }

        Cache.PendingNotification pending = notifications.get(index);
        try {
            if (pending.isExpired()) {
                expiredIds.add(pending.getId());
            } else {
                AbstractPlayer player = platform.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) {
                    if (debugMode) logger.info("Player " + playerUuid + " disconnected during notification delivery");
                    return;
                }
                deliverPendingNotificationToPlayer(playerUuid, pending);
                deliveredIds.add(pending.getId());
                if (debugMode) logger.info("Delivered pending notification " + pending.getId() + " to " + playerUuid);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error delivering pending notification " + pending.getId(), e);
        }

        syncExecutor.schedule(() ->
            platform.runOnMainThread(() ->
                deliverNotificationAtIndex(playerUuid, notifications, index + 1, deliveredIds, expiredIds)),
            NOTIFICATION_INTER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void finalizePendingNotificationDelivery(UUID playerUuid, List<String> deliveredIds, List<String> expiredIds) {
        try {
            for (String id : expiredIds) cache.removeNotification(playerUuid, id);
            for (String id : deliveredIds) cache.removeNotification(playerUuid, id);
            if (!deliveredIds.isEmpty()) acknowledgeNotifications(playerUuid, deliveredIds);
            if (debugMode) logger.info("Notification delivery complete for " + playerUuid + ". Delivered: " + deliveredIds.size() + ", Expired: " + expiredIds.size());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finalizing pending notification delivery: " + e.getMessage(), e);
        }
    }

    private void acknowledgeNotification(UUID playerUuid, String notificationId) {
        acknowledgeNotifications(playerUuid, List.of(notificationId));
    }

    private void acknowledgeNotifications(UUID playerUuid, List<String> notificationIds) {
        try {
            NotificationAcknowledgeRequest request = new NotificationAcknowledgeRequest(
                    playerUuid.toString(), notificationIds, Instant.now().toString());

            httpClientHolder.getClient().acknowledgeNotifications(request)
                    .thenAccept(response -> {
                        if (debugMode) logger.info("Acknowledged " + notificationIds.size() + " notifications for " + playerUuid);
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof PanelUnavailableException) {
                            logger.warning("Failed to acknowledge notifications for " + playerUuid + ": Panel temporarily unavailable");
                        } else {
                            logger.warning("Failed to acknowledge notifications for " + playerUuid + ": " + throwable.getMessage());
                        }
                        return null;
                    })
                    .orTimeout(SYNC_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error acknowledging notifications: " + e.getMessage(), e);
        }
    }
}