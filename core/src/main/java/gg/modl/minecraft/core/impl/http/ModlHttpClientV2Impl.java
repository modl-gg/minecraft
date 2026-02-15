package gg.modl.minecraft.core.impl.http;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import com.google.gson.Gson;
import gg.modl.minecraft.api.http.request.*;
import gg.modl.minecraft.api.http.request.v2.*;
import gg.modl.minecraft.api.http.response.*;
import java.util.HashMap;
import java.util.Map;
import gg.modl.minecraft.core.util.CircuitBreaker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * V2 API HTTP client implementation.
 * Connects to centralized API at api.modl.gg or api.modl.top.
 * Uses X-API-Key and X-Server-Domain headers for authentication.
 */
public class ModlHttpClientV2Impl implements ModlHttpClient {
    @NotNull
    private final String baseUrl;
    @NotNull
    private final String apiKey;
    @NotNull
    private final String serverDomain;
    @NotNull
    private final HttpClient httpClient;
    @NotNull
    private final Gson gson;
    @NotNull
    private final Logger logger;
    private final boolean debugMode;
    @NotNull
    private final CircuitBreaker circuitBreaker;

    public ModlHttpClientV2Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain) {
        this(baseUrl, apiKey, serverDomain, false);
    }

    public ModlHttpClientV2Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain, boolean debugMode) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.debugMode = debugMode;
        this.circuitBreaker = new CircuitBreaker("modl-api-v2");

        ThreadFactory httpThreadFactory = r -> {
            Thread t = new Thread(r, "modl-http-v2-client");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newCachedThreadPool(httpThreadFactory))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
        this.logger = Logger.getLogger(ModlHttpClientV2Impl.class.getName());
    }

    /**
     * Build a request with standard V2 headers.
     */
    private HttpRequest.Builder requestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid)
                .GET()
                .build(), PlayerProfileResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "/linked-accounts")
                .GET()
                .build(), LinkedAccountsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request) {
        // Convert to V2 format with optional IP geo data
        Map<String, Object> ipInfoMap = null;
        if (request.getIpInfo() != null) {
            ipInfoMap = new HashMap<>();
            com.google.gson.JsonObject ipInfo = request.getIpInfo();
            if (ipInfo.has("countryCode")) ipInfoMap.put("country", ipInfo.get("countryCode").getAsString());
            if (ipInfo.has("regionName")) ipInfoMap.put("region", ipInfo.get("regionName").getAsString());
            if (ipInfo.has("as")) ipInfoMap.put("asn", ipInfo.get("as").getAsString());
            if (ipInfo.has("proxy")) ipInfoMap.put("proxy", ipInfo.get("proxy").getAsBoolean());
            if (ipInfo.has("hosting")) ipInfoMap.put("hosting", ipInfo.get("hosting").getAsBoolean());
        }
        V2LoginRequest v2Request = new V2LoginRequest(
                request.getMinecraftUuid(),
                request.getUsername(),
                request.getIpAddress(),
                ipInfoMap,
                request.getSkinHash(),
                request.getServerName()
        );
        String requestBody = gson.toJson(v2Request);
        if (debugMode) {
            logger.info(String.format("[V2] Player login request body: %s", requestBody));
        }

        return sendAsync(requestBuilder("/minecraft/players/login")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), PlayerLoginResponse.class, "LOGIN");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        // Convert to V2 format
        V2DisconnectRequest v2Request = new V2DisconnectRequest(request.getMinecraftUuid(), request.getSessionDurationMs());
        return sendAsync(requestBuilder("/minecraft/players/disconnect")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return sendAsync(requestBuilder("/minecraft/tickets")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), CreateTicketResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) {
            logger.info(String.format("[V2] Create unfinished ticket request body: %s", requestBody));
        }

        return sendAsync(requestBuilder("/minecraft/tickets/unfinished")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), CreateTicketResponse.class, "CREATE_UNFINISHED_TICKET");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {
        // Note: CreatePunishmentRequest is not currently used - see createPunishmentWithResponse
        return sendAsync(requestBuilder("/minecraft/punishments/create")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        // Build body without targetUuid since it's in the path
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("text", request.getText());

        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        // Convert to V2 format - backend expects typeOrdinal not typeOrdinal
        V2CreatePunishmentRequest v2Request = new V2CreatePunishmentRequest(
                request.getTargetUuid(),
                request.getIssuerName(),
                request.getTypeOrdinal() != null ? request.getTypeOrdinal() : 0,
                request.getReason(),
                request.getDuration(),
                request.getData(),
                request.getNotes(),
                request.getAttachedTicketIds(),
                request.getSeverity(),
                request.getStatus()
        );
        return sendAsync(requestBuilder("/minecraft/punishments/dynamic")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), PunishmentCreateResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request) {
        return sendAsync(requestBuilder("/minecraft/players?minecraftUuid=" + request.getMinecraftUuid())
                .GET()
                .build(), PlayerGetResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/by-name?username=" + request.getMinecraftUsername())
                .GET()
                .build(), PlayerNameResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        // Convert to V2 format - backend expects only text and issuerName in body, targetUuid in path
        V2CreateNoteRequest v2Request = new V2CreateNoteRequest(
                request.getText(),
                request.getIssuerName()
        );
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), PlayerNoteCreateResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        // Convert to V2 format
        V2SyncRequest v2Request = new V2SyncRequest(
                request.getLastSyncTimestamp(),
                request.getOnlinePlayers().stream()
                        .map(p -> new V2SyncRequest.OnlinePlayer(p.getUuid(), p.getUsername(), p.getIpAddress(), p.getSessionDurationMs()))
                        .collect(Collectors.toList()),
                new V2SyncRequest.ServerStatus(
                        request.getServerStatus().getOnlinePlayerCount(),
                        request.getServerStatus().getMaxPlayers(),
                        request.getServerStatus().getServerVersion(),
                        request.getServerStatus().getTimestamp()
                )
        );
        String requestBody = gson.toJson(v2Request);
        if (debugMode) {
            logger.info(String.format("[V2] Sync request body: %s", requestBody));
        }

        return sendAsync(requestBuilder("/minecraft/players/sync")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), SyncResponse.class, "SYNC");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        // Convert to V2 format
        V2PunishmentAcknowledgeRequest v2Request = new V2PunishmentAcknowledgeRequest(
                request.getPunishmentId(),
                request.getPlayerUuid(),
                request.getExecutedAt(),
                request.isSuccess(),
                request.getErrorMessage()
        );
        return sendAsync(requestBuilder("/minecraft/punishments/acknowledge")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        // Convert to V2 format
        V2NotificationAcknowledgeRequest v2Request = new V2NotificationAcknowledgeRequest(
                request.getPlayerUuid(),
                request.getNotificationIds(),
                request.getAcknowledgedAt()
        );
        return sendAsync(requestBuilder("/minecraft/notifications/acknowledge")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        return sendAsync(requestBuilder("/minecraft/punishments/types")
                .GET()
                .build(), PunishmentTypesResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        return sendAsync(requestBuilder("/minecraft/staff/permissions")
                .GET()
                .build(), StaffPermissionsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request) {
        // Convert to V2 format
        V2LookupRequest v2Request = new V2LookupRequest(request.getQuery(), false);
        return sendAsync(requestBuilder("/minecraft/players/lookup")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), PlayerLookupResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        // Convert to V2 format - backend expects issuerName, reason, expectedType in body
        V2PardonPunishmentRequest v2Request = new V2PardonPunishmentRequest(
                request.getIssuerName(),
                request.getReason(),
                request.getExpectedType()
        );
        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/pardon")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), PardonResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        // Convert to V2 format
        V2PardonPlayerRequest v2Request = new V2PardonPlayerRequest(
                request.getPlayerName(),
                request.getIssuerName(),
                request.getPunishmentType(),
                request.getReason()
        );
        return sendAsync(requestBuilder("/minecraft/players/pardon")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), PardonResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return sendAsync(requestBuilder("/minecraft/migration/progress")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                                 String country, String region, String asn, boolean proxy, boolean hosting) {
        V2SubmitIpInfoRequest v2Request = new V2SubmitIpInfoRequest(minecraftUUID, ip, country, region, asn, proxy, hosting);
        return sendAsync(requestBuilder("/minecraft/players/submit-ip-info")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(v2Request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        return sendAsync(requestBuilder("/minecraft/players/online")
                .GET()
                .build(), OnlinePlayersResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        return sendAsync(requestBuilder("/minecraft/punishments/recent?hours=" + hours)
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
        return sendAsync(requestBuilder(endpoint)
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
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (dismissedBy != null) body.put("dismissedBy", dismissedBy);
        if (reason != null) body.put("reason", reason);

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/dismiss")
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

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/resolve")
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

        return sendAsync(requestBuilder(endpoint.toString())
                .GET()
                .build(), TicketsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        return sendAsync(requestBuilder("/minecraft/dashboard/stats")
                .GET()
                .build(), DashboardStatsResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal) {
        String endpoint = "/minecraft/punishments/preview?playerUuid=" + playerUuid.toString() + "&typeOrdinal=" + typeOrdinal;
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), PunishmentPreviewResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("note", request.getNote());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/note")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("evidenceUrl", request.getEvidenceUrl());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/evidence")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("newDuration", request.getNewDuration());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/duration")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        body.put("option", request.getOption());
        body.put("enabled", request.isEnabled());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/toggle")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("playerUuid", request.getPlayerUuid());
        body.put("playerName", request.getPlayerName());

        return sendAsync(requestBuilder("/minecraft/tickets/" + request.getTicketId() + "/claim")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), ClaimTicketResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        return sendAsync(requestBuilder("/minecraft/staff")
                .GET()
                .build(), StaffListResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName) {
        Map<String, String> body = new HashMap<>();
        body.put("role", roleName);

        return sendAsync(requestBuilder("/minecraft/staff/" + staffId + "/role")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return sendAsync(requestBuilder("/minecraft/roles")
                .GET()
                .build(), RolesListResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions) {
        Map<String, Object> body = new HashMap<>();
        body.put("permissions", permissions);

        return sendAsync(requestBuilder("/minecraft/roles/" + roleId + "/permissions")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId) {
        return sendAsync(requestBuilder("/minecraft/punishments/" + punishmentId)
                .GET()
                .build(), PunishmentDetailResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", issuerName);

        return sendAsync(requestBuilder("/minecraft/punishments/" + punishmentId + "/upload-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), EvidenceUploadTokenResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName) {
        Map<String, String> body = Map.of("minecraftUuid", minecraftUuid, "serverName", serverName);
        return sendAsync(requestBuilder("/minecraft/players/update-server")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType) {
        return sendAsync(request, responseType, null);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType, String operation) {
        final Instant startTime = Instant.now();
        final String requestId = generateRequestId();

        if (!circuitBreaker.allowRequest()) {
            return CompletableFuture.failedFuture(
                    new PanelUnavailableException(503, request.uri().getPath(),
                            "V2 API is temporarily unavailable (circuit breaker open)"));
        }

        if (debugMode) {
            logger.info(String.format("[V2-REQ-%s] %s %s", requestId, request.method(), request.uri()));
            logger.info(String.format("[V2-REQ-%s] Headers: %s", requestId, request.headers().map()));
            request.bodyPublisher().ifPresent(body -> {
                logger.info(String.format("[V2-REQ-%s] Body present: %s", requestId, body.getClass().getSimpleName()));
            });
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    final Duration duration = Duration.between(startTime, Instant.now());

                    if (debugMode) {
                        logger.info(String.format("[V2-RES-%s] Status: %d (took %dms)",
                                requestId, response.statusCode(), duration.toMillis()));
                        logger.info(String.format("[V2-RES-%s] Headers: %s", requestId, response.headers().map()));

                        String body = response.body();
                        if (body != null && !body.isEmpty()) {
                            if ("LOGIN".equals(operation) || "SYNC".equals(operation) || body.length() <= 1000) {
                                logger.info(String.format("[V2-RES-%s] Body: %s", requestId, body));
                            } else {
                                logger.info(String.format("[V2-RES-%s] Body: %s... (truncated, %d chars total)",
                                        requestId, body.substring(0, 1000), body.length()));
                            }
                        }
                    }

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        circuitBreaker.recordSuccess();

                        if (responseType == Void.class) {
                            return null;
                        }

                        try {
                            T result = gson.fromJson(response.body(), responseType);
                            if (debugMode) {
                                logger.info(String.format("[V2-REQ-%s] Successfully parsed response to %s",
                                        requestId, responseType.getSimpleName()));
                            }
                            return result;
                        } catch (Exception e) {
                            logger.severe(String.format("[V2-REQ-%s] Failed to parse response: %s", requestId, e.getMessage()));
                            throw new RuntimeException("Failed to parse V2 response: " + e.getMessage(), e);
                        }
                    } else {
                        String errorMessage;
                        try {
                            com.google.gson.JsonObject errorResponse = gson.fromJson(response.body(), com.google.gson.JsonObject.class);
                            if (errorResponse != null) {
                                String msg = errorResponse.has("message") ? errorResponse.get("message").getAsString() : "";
                                String errors = errorResponse.has("errors") ? errorResponse.get("errors").getAsString() : "";
                                errorMessage = !errors.isEmpty() ? msg + " Details: " + errors : msg;
                                if (errorMessage.isEmpty()) {
                                    errorMessage = String.format("V2 request failed with status code %d: %s",
                                            response.statusCode(), response.body());
                                }
                            } else {
                                errorMessage = String.format("V2 request failed with status code %d: %s",
                                        response.statusCode(), response.body());
                            }
                        } catch (Exception e) {
                            errorMessage = String.format("V2 request failed with status code %d: %s",
                                    response.statusCode(), response.body());
                        }

                        if (response.statusCode() == 502) {
                            circuitBreaker.recordFailure();
                            throw new PanelUnavailableException(502, request.uri().getPath(),
                                    "V2 API is temporarily unavailable (502 Bad Gateway)");
                        } else if (response.statusCode() == 404) {
                            // 404 is expected for player/resource lookups — not a real error
                            if (debugMode) {
                                logger.fine(String.format("[V2-REQ-%s] Not found (404): %s — %s", requestId, request.uri().getPath(), errorMessage));
                            }
                        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                            circuitBreaker.recordFailure();
                            logger.severe(String.format("[V2-REQ-%s] Authentication failed - check API key and server domain", requestId));
                        } else if (response.statusCode() == 405) {
                            circuitBreaker.recordFailure();
                            logger.severe(String.format("[V2-REQ-%s] Method Not Allowed (405) - %s %s", requestId, request.method(), request.uri()));
                            logger.severe(String.format("[V2-REQ-%s] This usually means the endpoint exists but doesn't accept %s requests", requestId, request.method()));
                        } else if (response.statusCode() == 500) {
                            circuitBreaker.recordFailure();
                            logger.severe(String.format("[V2-REQ-%s] Server Error (500) - %s %s", requestId, request.method(), request.uri()));
                            logger.severe(String.format("[V2-REQ-%s] Response body: %s", requestId, response.body()));
                        } else {
                            circuitBreaker.recordFailure();
                            logger.warning(String.format("[V2-REQ-%s] %s", requestId, errorMessage));
                        }

                        throw new RuntimeException(errorMessage);
                    }
                })
                .exceptionally(throwable -> {
                    if (!(throwable instanceof PanelUnavailableException)) {
                        circuitBreaker.recordFailure();
                    }

                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException("V2 HTTP request failed", throwable);
                });
    }

    private String generateRequestId() {
        return "V2-" + String.valueOf(System.nanoTime() % 1000000);
    }
}
