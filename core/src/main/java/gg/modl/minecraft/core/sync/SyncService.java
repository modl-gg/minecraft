package gg.modl.minecraft.core.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.database.JdbcDatabaseProvider;
import gg.modl.minecraft.core.service.MigrationService;
import gg.modl.minecraft.core.util.PunishmentMessages;

import java.io.File;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SyncService {
    @NotNull
    private final Platform platform;
    @NotNull
    private final HttpClientHolder httpClientHolder; // Shared holder for dynamic V1->V2 upgrade
    @NotNull
    private final Cache cache;
    @NotNull
    private final Logger logger;
    @NotNull
    private final LocaleManager localeManager;
    @NotNull
    private final String apiUrl;
    @NotNull
    private final String apiKey;
    private final int pollingRateSeconds;
    @NotNull
    private final File dataFolder;
    private final DatabaseConfig databaseConfig;
    private final boolean debugMode;

    private String lastSyncTimestamp;
    private ScheduledExecutorService syncExecutor;
    private boolean isRunning = false;
    private MigrationService migrationService;

    private Long lastKnownStaffPermissionsTimestamp = null;
    private Long lastKnownPunishmentTypesTimestamp = null;
    private final List<PunishmentTypesRefreshListener> punishmentTypesListeners = new ArrayList<>();

    /**
     * Create a new SyncService.
     */
    public SyncService(@NotNull Platform platform, @NotNull HttpClientHolder httpClientHolder, @NotNull Cache cache,
                       @NotNull Logger logger, @NotNull LocaleManager localeManager, @NotNull String apiUrl,
                       @NotNull String apiKey, int pollingRateSeconds, @NotNull File dataFolder,
                       DatabaseConfig databaseConfig, boolean debugMode) {
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
    }

    public interface PunishmentTypesRefreshListener {
        void onPunishmentTypesRefreshed(List<PunishmentTypesResponse.PunishmentTypeData> types);
    }

    public void addPunishmentTypesListener(PunishmentTypesRefreshListener listener) {
        punishmentTypesListeners.add(listener);
    }
    
    /**
     * Start the sync service with configurable polling interval
     */
    public void start() {
        if (isRunning) {
            logger.warning("Sync service is already running");
            return;
        }
        
        // Validate polling rate (minimum 1 second)
        int actualPollingRate = Math.max(1, pollingRateSeconds);
        if (actualPollingRate != pollingRateSeconds) {
            logger.warning("Polling rate adjusted from " + pollingRateSeconds + " to " + actualPollingRate + " seconds (minimum is 1 second)");
        }
        
        this.lastSyncTimestamp = Instant.now().toString();
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-sync");
            t.setDaemon(true);
            return t;
        });
        
        // Start sync task with configurable rate
        syncExecutor.scheduleAtFixedRate(this::performSync, 5, actualPollingRate, TimeUnit.SECONDS);
        isRunning = true;
        
        if (debugMode) {
            logger.info("MODL Sync service started - syncing every " + actualPollingRate + " seconds");
        }
    }
    
    /**
     * Stop the sync service
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        if (syncExecutor != null) {
            syncExecutor.shutdown();
            try {
                if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                syncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Shutdown migration service if active
        if (migrationService != null) {
            migrationService.shutdown();
        }
        
        isRunning = false;
        if (debugMode) {
            logger.info("MODL Sync service stopped");
        }
    }
    
    /**
     * Perform a sync operation
     */
    private void performSync() {
        try {
            // Get online players
            Collection<AbstractPlayer> onlinePlayers = platform.getOnlinePlayers();

            // Build sync request
            SyncRequest request = new SyncRequest();
            request.setLastSyncTimestamp(lastSyncTimestamp);
            request.setOnlinePlayers(buildOnlinePlayersList(onlinePlayers));
            request.setServerStatus(buildServerStatus(onlinePlayers));


            // Send sync request
            CompletableFuture<SyncResponse> syncFuture = httpClientHolder.getClient().sync(request);

            syncFuture.thenAccept(response -> {
                try {
                    handleSyncResponse(response);
                } catch (Exception e) {
                    logger.severe("Error handling sync response: " + e.getMessage());
                    e.printStackTrace();
                }
            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof PanelUnavailableException) {
                    //logger.warning("Sync request failed: Panel temporarily unavailable (502 error)");
                } else {
                    logger.warning("Sync request failed: " + throwable.getMessage());
                }
                return null;
            });

        } catch (Exception e) {
            logger.severe("Error during sync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build online players list for sync request
     */
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
    
    /**
     * Build server status for sync request
     */
    private SyncRequest.ServerStatus buildServerStatus(Collection<AbstractPlayer> onlinePlayers) {
        return new SyncRequest.ServerStatus(
                onlinePlayers.size(),
                platform.getMaxPlayers(),
                platform.getServerVersion(),
                Instant.now().toString()
        );
    }
    
    /**
     * Handle sync response from the panel
     */
    private void handleSyncResponse(SyncResponse response) {
        // Update last sync timestamp
        this.lastSyncTimestamp = response.getTimestamp();
        
        SyncResponse.SyncData data = response.getData();

        // Process modified punishments FIRST (pardons, duration changes) so the cache
        // is cleared before new/promoted punishments are added
        for (SyncResponse.ModifiedPunishment modified : data.getRecentlyModifiedPunishments()) {
            processModifiedPunishment(modified);
        }

        // Process pending punishments AFTER modifications
        for (SyncResponse.PendingPunishment pending : data.getPendingPunishments()) {
            processPendingPunishment(pending);
        }
        
        // Process active staff members (only if present - removed from sync in favor of startup loading)
        if (data.getActiveStaffMembers() != null) {
            for (SyncResponse.ActiveStaffMember staffMember : data.getActiveStaffMembers()) {
                processActiveStaffMember(staffMember);
            }
        }
        
        // Process player notifications
        for (SyncResponse.PlayerNotification notification : data.getPlayerNotifications()) {
            processPlayerNotification(notification);
        }

        // Process staff notifications
        if (data.getStaffNotifications() != null) {
            for (SyncResponse.StaffNotification staffNotif : data.getStaffNotifications()) {
                processStaffNotification(staffNotif);
            }
        }
        
        // Process migration task if present
        if (data.getMigrationTask() != null) {
            processMigrationTask(data.getMigrationTask());
        }

        // Check for staff permissions updates
        Long newStaffTimestamp = data.getStaffPermissionsUpdatedAt();
        if (newStaffTimestamp != null && !newStaffTimestamp.equals(lastKnownStaffPermissionsTimestamp)) {
            if (debugMode) {
                logger.info("Staff permissions changed (timestamp: " + newStaffTimestamp + "), refreshing...");
            }
            refreshStaffPermissions();
            lastKnownStaffPermissionsTimestamp = newStaffTimestamp;
        }

        // Check for punishment types updates
        Long newPunishmentTypesTimestamp = data.getPunishmentTypesUpdatedAt();
        if (newPunishmentTypesTimestamp != null && !newPunishmentTypesTimestamp.equals(lastKnownPunishmentTypesTimestamp)) {
            if (debugMode) {
                logger.info("Punishment types changed (timestamp: " + newPunishmentTypesTimestamp + "), refreshing...");
            }
            refreshPunishmentTypes();
            lastKnownPunishmentTypesTimestamp = newPunishmentTypesTimestamp;
        }
    }

    private void refreshStaffPermissions() {
        httpClientHolder.getClient().getStaffPermissions().thenAccept(response -> {
            cache.clearStaffPermissions();
            int loadedCount = 0;
            for (var staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffRole(), staffMember.getPermissions());
                        loadedCount++;
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID for staff member: " + staffMember.getMinecraftUuid());
                    }
                }
            }
            if (debugMode) {
                logger.info("Staff permissions refreshed: " + loadedCount + " staff members");
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                logger.warning("Failed to refresh staff permissions: Panel temporarily unavailable");
            } else {
                logger.warning("Failed to refresh staff permissions: " + throwable.getMessage());
            }
            return null;
        });
    }

    private void refreshPunishmentTypes() {
        httpClientHolder.getClient().getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) {
                for (PunishmentTypesRefreshListener listener : punishmentTypesListeners) {
                    try {
                        listener.onPunishmentTypesRefreshed(response.getData());
                    } catch (Exception e) {
                        logger.warning("Error notifying punishment types listener: " + e.getMessage());
                    }
                }
                if (debugMode) {
                    logger.info("Punishment types refreshed: " + response.getData().size() + " types");
                }
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                logger.warning("Failed to refresh punishment types: Panel temporarily unavailable");
            } else {
                logger.warning("Failed to refresh punishment types: " + throwable.getMessage());
            }
            return null;
        });
    }

    /**
     * Process migration task from sync response
     */
    private void processMigrationTask(SyncResponse.MigrationTask migrationTask) {
        try {
            if (debugMode) {
                logger.info(String.format("Processing migration task %s (type: %s)",
                        migrationTask.getTaskId(), migrationTask.getType()));
            }
            
            // Initialize migration service if not already done
            if (migrationService == null) {
                try {
                    // Try to get LiteBans database provider from platform first
                    DatabaseProvider databaseProvider = platform.createLiteBansDatabaseProvider();
                    
                    if (databaseProvider == null) {
                        // LiteBans not available, use JDBC
                        if (debugMode) {
                            logger.info("[Migration] LiteBans not available, using JDBC connection");
                        }
                        databaseProvider = new JdbcDatabaseProvider(databaseConfig, logger);
                    }
                    
                    migrationService = new MigrationService(logger, httpClientHolder.getClient(), apiUrl, apiKey, dataFolder, databaseProvider);
                } catch (Exception e) {
                    logger.severe("[Migration] Failed to initialize migration service: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
            
            // Start migration based on type
            String taskId = migrationTask.getTaskId();
            String type = migrationTask.getType();
            
            if ("litebans".equalsIgnoreCase(type)) {
                // Export LiteBans data asynchronously
                migrationService.exportLiteBansData(taskId).thenAccept(jsonFile -> {
                    if (jsonFile != null && jsonFile.exists()) {
                        // Upload the file to panel
                        migrationService.uploadMigrationFile(jsonFile, taskId).thenAccept(success -> {
                            if (success) {
                                if (debugMode) {
                                    logger.info("[Migration] Migration task " + taskId + " completed successfully");
                                }
                            } else {
                                logger.warning("[Migration] Migration task " + taskId + " upload failed");
                            }
                        });
                    } else {
                        logger.warning("[Migration] Migration task " + taskId + " export failed - no file generated");
                    }
                }).exceptionally(throwable -> {
                    logger.severe("[Migration] Migration task " + taskId + " failed: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
            } else {
                logger.warning("[Migration] Unknown migration type: " + type);
            }
            
        } catch (Exception e) {
            logger.severe("[Migration] Error processing migration task: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a pending punishment that needs to be executed
     */
    private void processPendingPunishment(SyncResponse.PendingPunishment pending) {
        String playerUuid = pending.getMinecraftUuid();
        String username = pending.getUsername();
        SimplePunishment punishment = pending.getPunishment();
        
        if (debugMode) {
            logger.info(String.format("Processing pending punishment %s for %s - Type: '%s', Ordinal: %d, isBan: %s, isMute: %s, isKick: %s",
                    punishment.getId(), username, punishment.getType(), punishment.getOrdinal(),
                    punishment.isBan(), punishment.isMute(), punishment.isKick()));
        }
        
        // Execute on main thread for platform-specific operations
        platform.runOnMainThread(() -> {
            boolean success = executePunishment(playerUuid, username, punishment);
            
            // Acknowledge the punishment execution (this will start the punishment)
            acknowledgePunishment(punishment.getId(), playerUuid, success, null);
        });
    }
    
    /**
     * Process a recently started punishment
     */
    private void processStartedPunishment(SyncResponse.PendingPunishment started) {
        // Same as pending punishment processing
        processPendingPunishment(started);
    }
    
    /**
     * Process a modified punishment (pardon, duration change)
     */
    private void processModifiedPunishment(SyncResponse.ModifiedPunishment modified) {
        String playerUuid = modified.getMinecraftUuid();
        String username = modified.getUsername();
        SyncResponse.PunishmentWithModifications punishment = modified.getPunishment();
        
        platform.runOnMainThread(() -> {
            for (SyncResponse.PunishmentModification modification : punishment.getModifications()) {
                handlePunishmentModification(playerUuid, username, punishment.getId(), modification);
            }
        });
    }
    
    /**
     * Execute a punishment on the server
     */
    private boolean executePunishment(String playerUuid, String username, SimplePunishment punishment) {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            AbstractPlayer player = platform.getPlayer(uuid);
            
            if (punishment.isBan()) {
                if (debugMode) {
                    logger.info(String.format("Executing BAN for %s (type: %s, ordinal: %d)", username, punishment.getType(), punishment.getOrdinal()));
                }
                return executeBan(uuid, username, punishment);
            } else if (punishment.isMute()) {
                if (debugMode) {
                    logger.info(String.format("Executing MUTE for %s (type: %s, ordinal: %d)", username, punishment.getType(), punishment.getOrdinal()));
                }
                return executeMute(uuid, username, punishment);
            } else if (punishment.isKick()) {
                if (debugMode) {
                    logger.info(String.format("Executing KICK for %s (type: %s, ordinal: %d)", username, punishment.getType(), punishment.getOrdinal()));
                }
                return executeKick(uuid, username, punishment);
            } else {
                logger.warning(String.format("Unknown punishment type for %s - Type: '%s', Ordinal: %d, isBan: %s, isMute: %s, isKick: %s", 
                        username, punishment.getType(), punishment.getOrdinal(), 
                        punishment.isBan(), punishment.isMute(), punishment.isKick()));
                return false;
            }
        } catch (Exception e) {
            logger.severe("Error executing punishment: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Execute a mute punishment
     */
    private boolean executeMute(UUID uuid, String username, SimplePunishment punishment) {
        try {
            // Add to cache for immediate effect
            cache.cacheMute(uuid, punishment);
            
            // Broadcast punishment
            String broadcastMessage = PunishmentMessages.formatPunishmentBroadcast(username, punishment, "muted", localeManager);
            platform.broadcast(broadcastMessage);
            
            if (debugMode) {
                logger.info(String.format("Successfully executed mute for %s: %s", username, punishment.getDescription()));
            }
            return true;
        } catch (Exception e) {
            logger.severe("Error executing mute: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute a ban punishment
     */
    private boolean executeBan(UUID uuid, String username, SimplePunishment punishment) {
        try {
            // Kick player if online
            AbstractPlayer player = platform.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Use proper ban message formatting with SYNC context for dynamic variables
                String kickMsg = PunishmentMessages.formatBanMessage(punishment, localeManager, PunishmentMessages.MessageContext.SYNC);
                platform.kickPlayer(player, kickMsg);
            }
            
            // Broadcast punishment
            String broadcastMessage = PunishmentMessages.formatPunishmentBroadcast(username, punishment, "banned", localeManager);
            platform.broadcast(broadcastMessage);
            
            if (debugMode) {
                logger.info(String.format("Successfully executed ban for %s: %s", username, punishment.getDescription()));
            }
            return true;
        } catch (Exception e) {
            logger.severe("Error executing ban: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute a kick punishment
     */
    private boolean executeKick(UUID uuid, String username, SimplePunishment punishment) {
        try {
            // Kick player if online
            AbstractPlayer player = platform.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Use proper kick message formatting with SYNC context for dynamic variables
                String kickMsg = PunishmentMessages.formatKickMessage(punishment, localeManager, PunishmentMessages.MessageContext.SYNC);
                platform.kickPlayer(player, kickMsg);

                // Broadcast punishment
                String broadcastMessage = PunishmentMessages.formatPunishmentBroadcast(username, punishment, "kicked", localeManager);
                platform.broadcast(broadcastMessage);
                
                if (debugMode) {
                    logger.info(String.format("Successfully executed kick for %s: %s", username, punishment.getDescription()));
                }
                return true;
            } else {
                if (debugMode) {
                    logger.info(String.format("Player %s is not online, kick punishment ignored", username));
                }
                return true; // Still considered successful since player is offline
            }
        } catch (Exception e) {
            logger.severe("Error executing kick: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle punishment modifications (pardons, duration changes)
     */
    private void handlePunishmentModification(String playerUuid, String username, String punishmentId, 
                                               SyncResponse.PunishmentModification modification) {
        try {
            UUID uuid = UUID.fromString(playerUuid);
            
            switch (modification.getType()) {
                case "MANUAL_PARDON":
                case "APPEAL_ACCEPT":
                    handlePardon(uuid, username, punishmentId);
                    break;
                case "MANUAL_DURATION_CHANGE":
                case "APPEAL_DURATION_CHANGE":
                    handleDurationChange(uuid, username, punishmentId, modification.getEffectiveDuration());
                    break;
                default:
                    logger.warning("Unknown modification type: " + modification.getType());
            }
        } catch (Exception e) {
            logger.severe("Error handling punishment modification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle punishment pardon
     */
    private void handlePardon(UUID uuid, String username, String punishmentId) {
        // Check if this punishment matches the cached mute or ban
        boolean wasMute = false;
        boolean wasBan = false;

        // Check cached mute
        SimplePunishment cachedMute = cache.getSimpleMute(uuid);
        if (cachedMute != null && cachedMute.getId().equals(punishmentId)) {
            cache.removeMute(uuid);
            wasMute = true;
            if (debugMode) {
                logger.info(String.format("Removed cached mute for %s (punishment %s)", username, punishmentId));
            }
        }

        // Check cached ban (in case player was banned while online)
        SimplePunishment cachedBan = cache.getSimpleBan(uuid);
        if (cachedBan != null && cachedBan.getId().equals(punishmentId)) {
            cache.removeBan(uuid);
            wasBan = true;
            if (debugMode) {
                logger.info(String.format("Removed cached ban for %s (punishment %s)", username, punishmentId));
            }
        }

        // If neither matched by ID, try removing both (fallback for older data)
        if (!wasMute && !wasBan) {
            // Remove any cached punishment data for safety
            cache.removeMute(uuid);
            cache.removeBan(uuid);
            if (debugMode) {
                logger.info(String.format("Cleared all cached punishments for %s (punishment %s not found in cache)", username, punishmentId));
            }
        }

        if (debugMode) {
            logger.info(String.format("Pardoned punishment %s for %s", punishmentId, username));
        }
        // Staff notification is sent by backend via staffNotifications — don't broadcast here
    }
    
    /**
     * Handle punishment duration change
     */
    private void handleDurationChange(UUID uuid, String username, String punishmentId, Long newDuration) {
        // Update cache if needed
        // Implementation depends on specific cache structure
        
        if (debugMode) {
            logger.info(String.format("Updated punishment %s duration for %s to %d ms",
                    punishmentId, username, newDuration));
        }
    }
    
    /**
     * Acknowledge punishment execution to the panel
     */
    private void acknowledgePunishment(String punishmentId, String playerUuid, boolean success, String errorMessage) {
        PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                punishmentId,
                playerUuid,
                Instant.now().toString(),
                success,
                errorMessage
        );
        
        httpClientHolder.getClient().acknowledgePunishment(request)
                .thenAccept(response -> {
                    if (debugMode) {
                        logger.info(String.format("Acknowledged punishment %s execution: %s",
                                punishmentId, success ? "SUCCESS" : "FAILED"));
                    }
                })
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) {
                        logger.warning("Failed to acknowledge punishment " + punishmentId + ": Panel temporarily unavailable");
                    } else {
                        logger.warning("Failed to acknowledge punishment " + punishmentId + ": " + throwable.getMessage());
                    }
                    return null;
                });
    }
    
    /**
     * Process active staff member information
     */
    private void processActiveStaffMember(SyncResponse.ActiveStaffMember staffMember) {
        try {
            UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
            AbstractPlayer player = platform.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                // Check if this is a new staff member or permissions changed
                SyncResponse.ActiveStaffMember existing = cache.getStaffMember(uuid);
                boolean isNew = existing == null;
                boolean permissionsChanged = existing != null &&
                    !existing.getPermissions().equals(staffMember.getPermissions());

                // Cache staff member information for easy access
                cache.cacheStaffMember(uuid, staffMember);

                // Only log when data is new or changed
                if (debugMode && (isNew || permissionsChanged)) {
                    logger.info(String.format("Staff member data %s for %s (%s) - Role: %s, Permissions: %s",
                            isNew ? "loaded" : "updated",
                            staffMember.getMinecraftUsername(),
                            staffMember.getStaffUsername(),
                            staffMember.getStaffRole(),
                            staffMember.getPermissions()));
                }
            }
        } catch (Exception e) {
            logger.warning("Error processing staff member data: " + e.getMessage());
        }
    }
    
    /**
     * Process a staff notification - broadcast to staff using the standard staff notification format
     */
    private void processStaffNotification(SyncResponse.StaffNotification notification) {
        try {
            if ("TICKET_CREATED".equals(notification.getType()) && notification.getData() != null) {
                processTicketCreatedNotification(notification);
            } else {
                String message = "&7&o[" + notification.getMessage() + "&7&o]";
                platform.staffBroadcast(message);
            }

            if (debugMode) {
                logger.info(String.format("Processed staff notification: %s", notification.getMessage()));
            }
        } catch (Exception e) {
            logger.warning("Error processing staff notification: " + e.getMessage());
        }
    }

    /**
     * Process a TICKET_CREATED staff notification with hover text and clickable link
     */
    private void processTicketCreatedNotification(SyncResponse.StaffNotification notification) {
        Map<String, Object> data = notification.getData();
        String ticketUrl = data.get("ticketUrl") != null ? (String) data.get("ticketUrl") : "";
        String subject = data.get("subject") != null ? (String) data.get("subject") : "";
        String firstReply = data.get("firstReplyContent") != null ? (String) data.get("firstReplyContent") : "";

        // Build hover text: subject + reply #0
        StringBuilder hoverText = new StringBuilder();
        if (!subject.isEmpty()) {
            hoverText.append(subject);
        }
        if (!firstReply.isEmpty()) {
            if (hoverText.length() > 0) hoverText.append("\n\n");
            // Truncate long replies for hover
            String truncated = firstReply.length() > 200 ? firstReply.substring(0, 200) + "..." : firstReply;
            hoverText.append(truncated);
        }

        String escapedMessage = escapeJson(notification.getMessage());
        String escapedHover = escapeJson(hoverText.toString());

        String json = String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"[%s]\",\"color\":\"gray\",\"italic\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"%s\"}}]}",
            escapedMessage, ticketUrl, escapedHover
        );

        platform.staffJsonBroadcast(json);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "\\t");
    }

    /**
     * Process a player notification
     */
    private void processPlayerNotification(SyncResponse.PlayerNotification notification) {
        try {
            if (debugMode) {
                logger.info(String.format("Processing notification %s (type: %s): %s",
                        notification.getId(), notification.getType(), notification.getMessage()));
            }

            // Check if this notification has a target player UUID
            String targetPlayerUuid = notification.getTargetPlayerUuid();
            if (targetPlayerUuid != null && !targetPlayerUuid.isEmpty()) {
                try {
                    UUID playerUuid = UUID.fromString(targetPlayerUuid);
                    boolean delivered = deliverNotificationToPlayerAndCheck(playerUuid, notification);

                    // Acknowledge the notification immediately after delivery to prevent re-sending
                    if (delivered) {
                        acknowledgeNotification(playerUuid, notification.getId());
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID format in notification: " + targetPlayerUuid);
                }
            } else {
                // Fallback for old format - deliver to all online players (deprecated)
                handleNotificationForAllOnlinePlayers(notification);
            }

        } catch (Exception e) {
            logger.severe("Error processing player notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Deliver a notification to a specific player
     */
    private void deliverNotificationToPlayer(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        deliverNotificationToPlayerAndCheck(playerUuid, notification);
    }

    /**
     * Deliver a notification to a specific player and return whether it was delivered
     * @return true if the notification was delivered to an online player, false if cached for later
     */
    private boolean deliverNotificationToPlayerAndCheck(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        AbstractPlayer player = platform.getPlayer(playerUuid);

        if (player != null && player.isOnline()) {
            // Player is online - deliver immediately
            String message;
            Map<String, Object> data = notification.getData();

            // Check if we have a ticket URL for clickable messages
            if (data != null && data.containsKey("ticketUrl")) {
                String ticketUrl = (String) data.get("ticketUrl");
                String ticketId = (String) data.get("ticketId");

                // Create a simpler clickable message format that works across platforms
                message = String.format(
                    "{\"text\":\"\",\"extra\":[" +
                    "{\"text\":\"[Ticket] \",\"color\":\"gold\"}," +
                    "{\"text\":\"%s \",\"color\":\"white\"}," +
                    "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
                    "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                    "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
                    notification.getMessage().replace("\"", "\\\""), ticketUrl, ticketId
                );

                if (debugMode) {
                    logger.info("Sending clickable notification JSON: " + message);
                }

                platform.runOnMainThread(() -> {
                    platform.sendJsonMessage(playerUuid, message);
                });
            } else {
                // Fallback to regular message format
                message = localeManager.getMessage("notification.ticket_reply", Map.of(
                    "message", notification.getMessage()
                ));

                platform.runOnMainThread(() -> {
                    platform.sendMessage(playerUuid, message);
                });
            }

            if (debugMode) {
                logger.info(String.format("Delivered notification %s to online player %s",
                        notification.getId(), player.getName()));
            }
            return true;
        } else {
            // Player is offline - don't cache here, the notification stays in backend pending list
            // It will be delivered when the player comes online and triggers a sync
            if (debugMode) {
                logger.info(String.format("Player %s is offline, notification %s will be delivered on next login",
                        playerUuid, notification.getId()));
            }
            return false;
        }
    }
    
    /**
     * Deliver a notification immediately for a player who just logged in
     */
    public void deliverLoginNotification(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        if (debugMode) {
            logger.info(String.format("Delivering login notification %s to player %s",
                    notification.getId(), playerUuid));
        }
        deliverNotificationToPlayer(playerUuid, notification);
    }
    
    /**
     * Handle notification delivery for online players
     * This is a temporary solution until we have proper player UUID targeting
     */
    private void handleNotificationForAllOnlinePlayers(SyncResponse.PlayerNotification notification) {
        Collection<AbstractPlayer> onlinePlayers = platform.getOnlinePlayers();
        
        for (AbstractPlayer player : onlinePlayers) {
            try {
                UUID playerUuid = player.getUuid();
                
                // Check if this notification is relevant to this player
                // For ticket notifications, we might check if they created recent tickets
                // For now, we'll cache it for all players and let them see it on login
                
                // Cache the notification
                cache.cacheNotification(playerUuid, notification);
                
                // Deliver immediately if player is online
                deliverNotificationToPlayer(playerUuid, notification);
                
            } catch (Exception e) {
                logger.warning("Error handling notification for player " + player.getName() + ": " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Deliver all pending notifications to a player (called on login)
     */
    public void deliverPendingNotifications(UUID playerUuid) {
        try {
            List<Cache.PendingNotification> pendingNotifications = cache.getPendingNotifications(playerUuid);
            
            if (pendingNotifications.isEmpty()) {
                return;
            }
            
            // Create a copy of the list to avoid ConcurrentModificationException
            List<Cache.PendingNotification> notificationsToProcess = new ArrayList<>(pendingNotifications);
            
            if (debugMode) {
                logger.info(String.format("Delivering %d pending notifications to player %s",
                        notificationsToProcess.size(), playerUuid));
            }
            
            List<String> deliveredNotificationIds = new ArrayList<>();
            List<String> expiredNotificationIds = new ArrayList<>();
            
            // Deliver notifications with delays and sound effects
            deliverNotificationsWithDelay(playerUuid, notificationsToProcess, deliveredNotificationIds, expiredNotificationIds);
            
            // Remove all expired notifications in batch
            for (String expiredId : expiredNotificationIds) {
                cache.removeNotification(playerUuid, expiredId);
            }
            
            // Remove all delivered notifications in batch
            for (String deliveredId : deliveredNotificationIds) {
                cache.removeNotification(playerUuid, deliveredId);
            }
            
            // Acknowledge all delivered notifications
            if (!deliveredNotificationIds.isEmpty()) {
                acknowledgeNotifications(playerUuid, deliveredNotificationIds);
            }
            
        } catch (Exception e) {
            logger.severe("Error delivering pending notifications: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Format notification message for display
     */
    private String formatNotificationMessage(SyncResponse.PlayerNotification notification) {
        // Return the message directly without prefix
        return notification.getMessage();
    }
    
    /**
     * Format pending notification message for display
     */
    private String formatNotificationMessage(Cache.PendingNotification notification) {
        // Return the message directly without prefix
        return notification.getMessage();
    }
    
    /**
     * Deliver a pending notification to a player with clickable links for ticket notifications
     */
    private void deliverPendingNotificationToPlayer(UUID playerUuid, Cache.PendingNotification pending) {
        AbstractPlayer player = platform.getPlayer(playerUuid);
        
        if (player != null && player.isOnline()) {
            // Check if we have ticket data for clickable messages
            Map<String, Object> data = pending.getData();
            if (data != null && data.containsKey("ticketUrl")) {
                String ticketUrl = (String) data.get("ticketUrl");
                String ticketId = (String) data.get("ticketId");
                
                // Create a clickable message format similar to sync notifications
                String message = String.format(
                    "{\"text\":\"\",\"extra\":[" +
                    "{\"text\":\"[Ticket] \",\"color\":\"gold\"}," +
                    "{\"text\":\"%s \",\"color\":\"white\"}," +
                    "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
                    "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                    "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
                    pending.getMessage().replace("\"", "\\\""), ticketUrl, ticketId
                );
                
                platform.runOnMainThread(() -> {
                    platform.sendJsonMessage(playerUuid, message);
                });
            } else {
                // Fallback to regular message format
                String message = formatNotificationMessage(pending);
                platform.runOnMainThread(() -> {
                    platform.sendMessage(playerUuid, message);
                });
            }
        }
    }
    
    /**
     * Deliver notifications with delays and sound effects for login
     */
    private void deliverNotificationsWithDelay(UUID playerUuid, List<Cache.PendingNotification> notifications, 
                                             List<String> deliveredIds, List<String> expiredIds) {
        if (notifications.isEmpty()) {
            return;
        }
        
        // Start delivery after initial delay (2 seconds after login)
        syncExecutor.schedule(() -> {
            platform.runOnMainThread(() -> {
                deliverNotificationAtIndex(playerUuid, notifications, 0, deliveredIds, expiredIds);
            });
        }, 2000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Recursively deliver notifications with delays between each one
     */
    private void deliverNotificationAtIndex(UUID playerUuid, List<Cache.PendingNotification> notifications, 
                                          int index, List<String> deliveredIds, List<String> expiredIds) {
        if (index >= notifications.size()) {
            // All notifications processed, clean up
            finalizePendingNotificationDelivery(playerUuid, deliveredIds, expiredIds);
            return;
        }
        
        Cache.PendingNotification pending = notifications.get(index);
        
        try {
            // Skip expired notifications
            if (pending.isExpired()) {
                expiredIds.add(pending.getId());
            } else {
                // Check if player is still online before delivering
                AbstractPlayer player = platform.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    // Play notification sound effect (placeholder for now)
                    playNotificationSound(playerUuid);
                    
                    // Format and send the notification (with clickable links for ticket notifications)
                    deliverPendingNotificationToPlayer(playerUuid, pending);
                    
                    // Track for acknowledgment and removal
                    deliveredIds.add(pending.getId());
                    
                    if (debugMode) {
                        logger.info(String.format("Delivered pending notification %s to player %s",
                                pending.getId(), playerUuid));
                    }
                } else {
                    // Player disconnected, stop delivery
                    if (debugMode) {
                        logger.info(String.format("Player %s disconnected during notification delivery", playerUuid));
                    }
                    return;
                }
            }
        } catch (Exception e) {
            logger.warning("Error delivering pending notification " + pending.getId() + ": " + e.getMessage());
        }
        
        // Schedule next notification delivery with delay (1.5 seconds between notifications)
        syncExecutor.schedule(() -> {
            platform.runOnMainThread(() -> {
                deliverNotificationAtIndex(playerUuid, notifications, index + 1, deliveredIds, expiredIds);
            });
        }, 1500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Finalize the pending notification delivery by cleaning up and acknowledging
     */
    private void finalizePendingNotificationDelivery(UUID playerUuid, List<String> deliveredIds, List<String> expiredIds) {
        try {
            // Remove all expired notifications in batch
            for (String expiredId : expiredIds) {
                cache.removeNotification(playerUuid, expiredId);
            }
            
            // Remove all delivered notifications in batch
            for (String deliveredId : deliveredIds) {
                cache.removeNotification(playerUuid, deliveredId);
            }
            
            // Acknowledge all delivered notifications
            if (!deliveredIds.isEmpty()) {
                acknowledgeNotifications(playerUuid, deliveredIds);
            }
            
            if (debugMode) {
                logger.info(String.format("Completed pending notification delivery for player %s. " +
                        "Delivered: %d, Expired: %d", playerUuid, deliveredIds.size(), expiredIds.size()));
            }
                    
        } catch (Exception e) {
            logger.severe("Error finalizing pending notification delivery: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Play notification sound effect (placeholder - to be implemented per platform)
     */
    private void playNotificationSound(UUID playerUuid) {
        // TODO: Implement sound playing per platform
        // For now, this is a placeholder that can be extended in platform implementations
        // Example sounds: "entity.experience_orb.pickup", "block.note_block.pling", "entity.player.levelup"
        
        // This could be implemented by:
        // 1. Adding a playSound method to the Platform interface
        // 2. Implementing it in each platform (Spigot, Velocity, BungeeCord)
        // 3. Using the appropriate platform-specific sound API
        
        logger.fine("Playing notification sound for player " + playerUuid + " (placeholder)");
    }
    
    /**
     * Acknowledge a single notification to the panel
     */
    private void acknowledgeNotification(UUID playerUuid, String notificationId) {
        acknowledgeNotifications(playerUuid, List.of(notificationId));
    }
    
    /**
     * Acknowledge multiple notifications to the panel
     */
    private void acknowledgeNotifications(UUID playerUuid, List<String> notificationIds) {
        try {
            NotificationAcknowledgeRequest request = new NotificationAcknowledgeRequest(
                    playerUuid.toString(),
                    notificationIds,
                    Instant.now().toString()
            );
            
            httpClientHolder.getClient().acknowledgeNotifications(request)
                    .thenAccept(response -> {
                        if (debugMode) {
                            logger.info(String.format("Acknowledged %d notifications for player %s",
                                    notificationIds.size(), playerUuid));
                        }
                    })
                    .exceptionally(throwable -> {
                        if (throwable.getCause() instanceof PanelUnavailableException) {
                            logger.warning("Failed to acknowledge notifications for player " + playerUuid + ": Panel temporarily unavailable");
                        } else {
                            logger.warning("Failed to acknowledge notifications for player " + playerUuid + ": " + throwable.getMessage());
                        }
                        return null;
                    });
                    
        } catch (Exception e) {
            logger.severe("Error acknowledging notifications: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}