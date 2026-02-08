package gg.modl.minecraft.core.impl.http;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import com.google.gson.Gson;
import gg.modl.minecraft.api.http.request.*;
import gg.modl.minecraft.api.http.response.*;
import gg.modl.minecraft.core.util.CircuitBreaker;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

public class ModlHttpClientImpl implements ModlHttpClient {
    @NotNull
    private final String baseUrl;
    @NotNull
    private final String apiKey;
    @NotNull
    private final HttpClient httpClient;
    @NotNull
    private final Gson gson;
    @NotNull
    private final Logger logger;
    private final boolean debugMode;
    @NotNull
    private final CircuitBreaker circuitBreaker;

    public ModlHttpClientImpl(@NotNull String baseUrl, @NotNull String apiKey) {
        this(baseUrl, apiKey, false);
    }

    public ModlHttpClientImpl(@NotNull String baseUrl, @NotNull String apiKey, boolean debugMode) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.debugMode = debugMode;
        this.circuitBreaker = new CircuitBreaker("modl-panel");

        // Create custom thread factory for HTTP client threads
        ThreadFactory httpThreadFactory = r -> {
            Thread t = new Thread(r, "modl-http-client");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };

        // Configure HTTP client with optimized settings
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newCachedThreadPool(httpThreadFactory))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
        this.logger = Logger.getLogger(ModlHttpClientImpl.class.getName());
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/" + uuid))
                .header("X-API-Key", apiKey)
                .build(), PlayerProfileResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/" + uuid + "/linked-accounts"))
                .header("X-API-Key", apiKey)
                .build(), LinkedAccountsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) {
            logger.info(String.format("Player login request body: %s", requestBody));
        }

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/login"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15)) // Longer timeout for login checks
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), PlayerLoginResponse.class, "LOGIN");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/disconnect"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/public/tickets"))
                .header("X-Ticket-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), CreateTicketResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        String requestBody = gson.toJson(request);
        String url = baseUrl + "/public/tickets/unfinished";
        
        if (debugMode) {
            logger.info(String.format("Create unfinished ticket request URL: %s", url));
            logger.info(String.format("Create unfinished ticket request body: %s", requestBody));
        }
        
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), CreateTicketResponse.class, "CREATE_UNFINISHED_TICKET");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishment/create"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/note/create"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishment/dynamic"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PunishmentCreateResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player?minecraftUuid=" + request.getMinecraftUuid()))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), PlayerGetResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player-name?username=" + request.getMinecraftUsername()))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), PlayerNameResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/player/" + request.getTargetUuid() + "/notes"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerNoteCreateResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) {
            logger.info(String.format("Sync request body: %s", requestBody));
        }

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/sync"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20)) // Longer timeout for sync operations
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), SyncResponse.class, "SYNC");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishment/acknowledge"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/notifications/acknowledge"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/staff"))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), StaffListResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("role", roleName);

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/staff/" + staffId + "/role"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/roles"))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), RolesListResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("permissions", permissions);

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/roles/" + roleId + "/permissions"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType) {
        return sendAsync(request, responseType, null);
    }
    
    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType, String operation) {
        final Instant startTime = Instant.now();
        final String requestId = generateRequestId();

        // Check circuit breaker before making request
        if (!circuitBreaker.allowRequest()) {
            //logger.warning(String.format("[REQ-%s] Circuit breaker is OPEN - blocking request to %s", requestId, request.uri()));
            return CompletableFuture.failedFuture(
                new PanelUnavailableException(503, request.uri().getPath(),
                    "Panel is temporarily unavailable (circuit breaker open)"));
        }

        if (debugMode) {
            logger.info(String.format("[REQ-%s] %s %s", requestId, request.method(), request.uri()));
            logger.info(String.format("[REQ-%s] Headers: %s", requestId, request.headers().map()));

            // Log request body if present (for POST/PUT requests)
            request.bodyPublisher().ifPresent(body -> {
                logger.info(String.format("[REQ-%s] Body present: %s", requestId, body.getClass().getSimpleName()));
            });
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    final Duration duration = Duration.between(startTime, Instant.now());
                    
                    if (debugMode) {
                        logger.info(String.format("[RES-%s] Status: %d (took %dms)", 
                                requestId, response.statusCode(), duration.toMillis()));
                        logger.info(String.format("[RES-%s] Headers: %s", requestId, response.headers().map()));
                        
                        String body = response.body();
                        if (body != null && !body.isEmpty()) {
                            // Always show full JSON for LOGIN operations, truncate others
                            if ("LOGIN".equals(operation) || body.length() <= 1000) {
                                logger.info(String.format("[RES-%s] Body: %s", requestId, body));
                            } else {
                                logger.info(String.format("[RES-%s] Body: %s... (truncated, %d chars total)", 
                                        requestId, body.substring(0, 1000), body.length()));
                            }
                        }
                    }
                    
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        // Record successful request for circuit breaker
                        circuitBreaker.recordSuccess();

                        if (responseType == Void.class) {
                            return null;
                        }

                        try {
                            T result = gson.fromJson(response.body(), responseType);
                            if (debugMode) {
                                logger.info(String.format("[REQ-%s] Successfully parsed response to %s",
                                        requestId, responseType.getSimpleName()));
                            }
                            return result;
                        } catch (Exception e) {
                            logger.severe(String.format("[REQ-%s] Failed to parse response: %s", requestId, e.getMessage()));
                            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
                        }
                    } else {
                        // Try to extract error message from JSON response
                        String errorMessage;
                        try {
                            // Attempt to parse JSON error response
                            com.google.gson.JsonObject errorResponse = gson.fromJson(response.body(), com.google.gson.JsonObject.class);
                            if (errorResponse != null && errorResponse.has("message")) {
                                errorMessage = errorResponse.get("message").getAsString();
                            } else {
                                errorMessage = String.format("Request failed with status code %d: %s", 
                                        response.statusCode(), response.body());
                            }
                        } catch (Exception e) {
                            // If JSON parsing fails, use the raw response
                            errorMessage = String.format("Request failed with status code %d: %s", 
                                    response.statusCode(), response.body());
                        }
                        
                        // Record failure for circuit breaker
                        circuitBreaker.recordFailure();

                        // Log additional details for common errors
                        if (response.statusCode() == 502) {
                            //logger.warning(String.format("[REQ-%s] Panel returned 502 (Bad Gateway) - likely restarting. Endpoint: %s", requestId, request.uri().getPath()));
                            throw new PanelUnavailableException(502, request.uri().getPath(),
                                    "Panel is temporarily unavailable (502 Bad Gateway)");
                        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                            logger.severe(String.format("[V1-REQ-%s] Authentication failed - check API key", requestId));
                        } else if (response.statusCode() == 404) {
                            logger.severe(String.format("[V1-REQ-%s] Endpoint not found: %s %s", requestId, request.method(), request.uri()));
                        } else if (response.statusCode() == 405) {
                            logger.severe(String.format("[V1-REQ-%s] Method Not Allowed (405) - %s %s", requestId, request.method(), request.uri()));
                            logger.severe(String.format("[V1-REQ-%s] NOTE: V1 API uses different paths than V2! Backend may not support V1 paths.", requestId));
                            logger.severe(String.format("[V1-REQ-%s] V1 paths: /api/minecraft/player/login, /api/minecraft/sync", requestId));
                            logger.severe(String.format("[V1-REQ-%s] V2 paths: /v1/minecraft/players/login, /v1/minecraft/players/sync", requestId));
                        }

                        logger.warning(String.format("[REQ-%s] %s", requestId, errorMessage));
                        throw new RuntimeException(errorMessage);
                    }
                })
                .exceptionally(throwable -> {
                    // Record failure for circuit breaker (unless it's already a PanelUnavailableException)
                    if (!(throwable instanceof PanelUnavailableException)) {
                        circuitBreaker.recordFailure();
                    }

                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("HTTP request failed", throwable);
                });
    }
    
    @NotNull
    @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        return sendAsync(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/minecraft/punishment-types"))
                        .header("X-API-Key", apiKey)
                        .GET()
                        .build(),
                PunishmentTypesResponse.class);
    }

    @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        return sendAsync(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/minecraft/staff-permissions"))
                        .header("X-API-Key", apiKey)
                        .GET()
                        .build(),
                StaffPermissionsResponse.class);
    }

    @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/minecraft/player-lookup"))
                        .header("X-API-Key", apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                        .build(),
                PlayerLookupResponse.class);
    }

    @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        // V1 API does not support pardon-by-punishment-ID; return failure so callers can fall back to player-based pardon
        return CompletableFuture.failedFuture(new RuntimeException("Sorry! Pardoning from Minecraft temporarily disabled, use panel."));
    }

    @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/minecraft/player/pardon"))
                        .header("X-API-Key", apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                        .build(), PardonResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/migration/progress"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                                 String country, String region, String asn, boolean proxy, boolean hosting) {
        // V1 API does not support this endpoint - log a warning so it's visible
        logger.warning("submitIpInfo called on V1 client which does not support this endpoint - IP geo data will not be stored");
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/players/online"))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), OnlinePlayersResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/recent?hours=" + hours))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), RecentPunishmentsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<ReportsResponse> getReports(String status) {
        String endpoint = "/minecraft/reports";
        if (status != null && !status.isEmpty()) {
            endpoint += "?status=" + status;
        }
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<ReportsResponse> getPlayerReports(@NotNull java.util.UUID playerUuid, String status) {
        String endpoint = "/minecraft/reports/player/" + playerUuid.toString();
        if (status != null && !status.isEmpty()) {
            endpoint += "?status=" + status;
        }
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (dismissedBy != null) body.put("dismissedBy", dismissedBy);
        if (reason != null) body.put("reason", reason);

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/reports/" + reportId + "/dismiss"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (resolvedBy != null) body.put("resolvedBy", resolvedBy);
        if (resolution != null) body.put("resolution", resolution);
        if (punishmentId != null) body.put("punishmentId", punishmentId);

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/reports/" + reportId + "/resolve"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<TicketsResponse> getTickets(String status, String type) {
        StringBuilder endpoint = new StringBuilder("/minecraft/tickets");
        boolean hasParam = false;

        if (status != null && !status.isEmpty() && !status.equals("all")) {
            endpoint.append("?status=").append(status);
            hasParam = true;
        }
        if (type != null && !type.isEmpty()) {
            endpoint.append(hasParam ? "&" : "?").append("type=").append(type);
        }

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint.toString()))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), TicketsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/dashboard/stats"))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), DashboardStatsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal) {
        String endpoint = "/minecraft/punishments/preview?playerUuid=" + playerUuid.toString() + "&typeOrdinal=" + typeOrdinal;
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), PunishmentPreviewResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("note", request.getNote());

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + request.getPunishmentId() + "/note"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("evidenceUrl", request.getEvidenceUrl());

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + request.getPunishmentId() + "/evidence"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("newDuration", request.getNewDuration());

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + request.getPunishmentId() + "/duration"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("option", request.getOption());
        body.put("enabled", request.isEnabled());

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + request.getPunishmentId() + "/toggle"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("playerUuid", request.getPlayerUuid());
        body.put("playerName", request.getPlayerName());

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/tickets/" + request.getTicketId() + "/claim"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), ClaimTicketResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId) {
        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + punishmentId))
                .header("X-API-Key", apiKey)
                .GET()
                .build(), PunishmentDetailResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("issuerName", issuerName);

        return sendAsync(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/minecraft/punishments/" + punishmentId + "/upload-token"))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), EvidenceUploadTokenResponse.class);
    }

    private String generateRequestId() {
        return String.valueOf(System.nanoTime() % 1000000);
    }
}