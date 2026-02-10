package gg.modl.minecraft.core.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV2Impl;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.database.JdbcDatabaseProvider;
import gg.modl.minecraft.core.service.MigrationService;
import gg.modl.minecraft.core.util.PunishmentMessages;
import lombok.RequiredArgsConstructor;

import java.io.File;
import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    // V2 upgrade settings
    private final String serverDomain;
    private final boolean useTestingApi;
    private final boolean debugMode;
    private boolean hasUpgradedToV2 = false;
    private int v2CheckCounter = 0;
    private static final int V2_CHECK_INTERVAL = 30; // Check V2 every 30 sync cycles

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
                       DatabaseConfig databaseConfig) {
        this(platform, httpClientHolder, cache, logger, localeManager, apiUrl, apiKey, pollingRateSeconds,
             dataFolder, databaseConfig, null, false, false);
    }

    /**
     * Create a new SyncService with V2 upgrade support.
     */
    public SyncService(@NotNull Platform platform, @NotNull HttpClientHolder httpClientHolder, @NotNull Cache cache,
                       @NotNull Logger logger, @NotNull LocaleManager localeManager, @NotNull String apiUrl,
                       @NotNull String apiKey, int pollingRateSeconds, @NotNull File dataFolder,
                       DatabaseConfig databaseConfig,
                       String serverDomain, boolean useTestingApi, boolean debugMode) {
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
        this.serverDomain = serverDomain;
        this.useTestingApi = useTestingApi;
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
        
        // Perform initial diagnostic check only for V1 API (V2 already validated during HttpManager init)
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            if (debugMode) {
                logger.info("MODL Sync service starting - performing initial diagnostic check...");
            }
            performDiagnosticCheck();
        }

        // Start sync task with configurable rate (delayed by 5 seconds for V2, 10 seconds for V1 to allow diagnostic check)
        int initialDelay = httpClientHolder.getApiVersion() == ApiVersion.V2 ? 5 : 10;
        syncExecutor.scheduleAtFixedRate(this::performSync, initialDelay, actualPollingRate, TimeUnit.SECONDS);
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
     * Perform diagnostic check to validate API connectivity
     */
    private void performDiagnosticCheck() {
        try {
            if (debugMode) {
                logger.info("Diagnostic: Testing API connectivity...");
                logger.info("Diagnostic: API Base URL: " + apiUrl);
                logger.info("Diagnostic: API Key: " + (apiKey.length() > 8 ?
                    apiKey.substring(0, 8) + "..." : "***"));
            }
            
            // First, test basic connectivity to the panel URL
            testBasicConnectivity();
            
            // Test with a minimal sync request
            SyncRequest testRequest = new SyncRequest();
            testRequest.setLastSyncTimestamp(Instant.now().toString());
            testRequest.setOnlinePlayers(List.of()); // Empty list
            testRequest.setServerStatus(new SyncRequest.ServerStatus(
                0, 
                platform.getMaxPlayers(),
                platform.getServerVersion(),
                Instant.now().toString()
            ));
            
            CompletableFuture<SyncResponse> testFuture = httpClientHolder.getClient().sync(testRequest);
            testFuture.thenAccept(response -> {
                if (debugMode) {
                    logger.info("Diagnostic: API connectivity test PASSED");
                    logger.info("Diagnostic: Server response timestamp: " + response.getTimestamp());
                }
            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof PanelUnavailableException) {
                    logger.warning("Diagnostic: Panel is temporarily unavailable (502 error) - likely restarting");
                } else {
                    logger.severe("Diagnostic: API connectivity test FAILED: " + throwable.getMessage());
                    if (throwable.getMessage().contains("502")) {
                        logger.severe("Diagnostic: 502 Bad Gateway indicates the API server is unreachable");
                        logger.severe("Diagnostic: This usually means:");
                        logger.severe("Diagnostic:   1. The panel server is down");
                        logger.severe("Diagnostic:   2. The reverse proxy/load balancer is misconfigured");
                        logger.severe("Diagnostic:   3. The API endpoint URL is incorrect");
                        logger.severe("Diagnostic: Expected sync endpoint: " + apiUrl + "/sync");
                    } else if (throwable.getMessage().contains("401") || throwable.getMessage().contains("403")) {
                        logger.severe("Diagnostic: Authentication failed - API key may be invalid");
                    } else if (throwable.getMessage().contains("404")) {
                        logger.severe("Diagnostic: Endpoint not found - the sync endpoint may not exist");
                    } else if (throwable.getMessage().contains("ConnectException") || throwable.getMessage().contains("UnknownHostException")) {
                        logger.severe("Diagnostic: Network connectivity issue - cannot reach " + apiUrl);
                    }
                }
                return null;
            });
            
        } catch (Exception e) {
            logger.severe("Diagnostic: Failed to perform connectivity test: " + e.getMessage());
        }
    }
    
    /**
     * Test basic connectivity to the panel server
     */
    private void testBasicConnectivity() {
        try {
            if (debugMode) {
                logger.info("Diagnostic: Testing basic connectivity to " + apiUrl);
            }
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (debugMode) {
                logger.info("Diagnostic: Basic connectivity test - Response code: " + responseCode);
            }

            if (responseCode >= 200 && responseCode < 400) {
                if (debugMode) {
                    logger.info("Diagnostic: Panel server is reachable");
                }
            } else if (responseCode == 502) {
                logger.warning("Diagnostic: Panel server returned 502 - server may be misconfigured");
            } else {
                logger.warning("Diagnostic: Panel server returned unexpected code: " + responseCode);
            }
            
            connection.disconnect();
        } catch (Exception e) {
            logger.severe("Diagnostic: Failed to connect to panel server: " + e.getMessage());
            if (e.getMessage().contains("UnknownHostException")) {
                logger.severe("Diagnostic: Domain name '123.modl.top' cannot be resolved - check DNS");
            } else if (e.getMessage().contains("ConnectException")) {
                logger.severe("Diagnostic: Connection refused - server may be down");
            } else if (e.getMessage().contains("SocketTimeoutException")) {
                logger.severe("Diagnostic: Connection timeout - server may be slow or unreachable");
            }
        }
    }
    
    /**
     * Perform a sync operation
     */
    private void performSync() {
        try {
            // Check if we should try upgrading to V2 (only for V1 clients)
            if (httpClientHolder.getApiVersion() == ApiVersion.V1 && !hasUpgradedToV2 && serverDomain != null) {
                v2CheckCounter++;
                if (v2CheckCounter >= V2_CHECK_INTERVAL) {
                    v2CheckCounter = 0;
                    tryUpgradeToV2();
                }
            }

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
     * Check if V2 API is available and switch to it if so.
     */
    private void tryUpgradeToV2() {
        try {
            String v2BaseUrl = useTestingApi ? HttpManager.TESTING_API_URL : HttpManager.V2_API_URL;
            String healthUrl = v2BaseUrl + "/v1/health";

            if (debugMode) {
                logger.info("Checking V2 API availability at: " + healthUrl);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .header("X-API-Key", apiKey)
                    .header("X-Server-Domain", serverDomain)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("V2 API health check PASSED - upgrading from V1 to V2");
                upgradeToV2(v2BaseUrl);
            } else {
                logger.fine("V2 API health check failed (status: " + response.statusCode() + ") - staying on V1");
            }
        } catch (Exception e) {
            logger.fine("V2 API health check failed: " + e.getMessage() + " - staying on V1");
        }
    }

    /**
     * Upgrade to V2 API client.
     * Updates the shared HttpClientHolder so all components automatically use the new V2 client.
     */
    private void upgradeToV2(String v2BaseUrl) {
        try {
            String v2ApiUrl = v2BaseUrl + ApiVersion.V2.getBasePath();

            // Create new V2 client
            ModlHttpClient v2Client = new ModlHttpClientV2Impl(v2ApiUrl, apiKey, serverDomain, false);

            // Update the shared holder - this makes all components use V2
            httpClientHolder.update(v2Client, ApiVersion.V2);
            this.hasUpgradedToV2 = true;

            logger.info("==============================================");
            logger.info("MODL API: Successfully upgraded to V2 API!");
            logger.info("  Base URL: " + v2ApiUrl);
            logger.info("  Server Domain: " + serverDomain);
            logger.info("  All components now using V2 client");
            logger.info("==============================================");

            // Refresh staff permissions and punishment types with new client
            refreshStaffPermissions();
            refreshPunishmentTypes();

        } catch (Exception e) {
            logger.warning("Failed to upgrade to V2 API: " + e.getMessage());
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
        platform.broadcast(String.format("§a%s has been pardoned", username));
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
            String message = "&7&o[" + notification.getMessage() + "&7&o]";
            platform.staffBroadcast(message);

            if (debugMode) {
                logger.info(String.format("Processed staff notification: %s", notification.getMessage()));
            }
        } catch (Exception e) {
            logger.warning("Error processing staff notification: " + e.getMessage());
        }
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