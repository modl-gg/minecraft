package gg.modl.minecraft.core.impl.http;

import com.google.gson.Gson;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.ChatLogBatchRequest;
import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.api.http.request.CommandLogBatchRequest;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerGetRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.request.PlayerNoteCreateRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.api.http.response.ChatLogsResponse;
import gg.modl.minecraft.api.http.response.ClaimTicketResponse;
import gg.modl.minecraft.api.http.response.CommandLogsResponse;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.api.http.response.DashboardStatsResponse;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.PaginatedNotesResponse;
import gg.modl.minecraft.api.http.response.PaginatedPunishmentsResponse;
import gg.modl.minecraft.api.http.response.PardonResponse;
import gg.modl.minecraft.api.http.response.PlayerGetResponse;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.api.http.response.PlayerNoteCreateResponse;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.Staff2faTokenResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.api.http.response.StaffPermissionsResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.util.CircuitBreaker;
import org.jetbrains.annotations.NotNull;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ModlHttpClientV2Impl implements ModlHttpClient {
    private static final String HEADER_API_KEY = "X-API-Key", HEADER_SERVER_DOMAIN = "X-Server-Domain",
            HEADER_CONTENT_TYPE = "Content-Type", CONTENT_TYPE_JSON = "application/json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10), LOGIN_TIMEOUT = Duration.ofSeconds(15),
            SYNC_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_LOG_BODY_LENGTH = 1000, HTTP_BAD_GATEWAY = 502;

    private @NotNull final String baseUrl, apiKey, serverDomain;
    private @NotNull final HttpClient httpClient;
    private @NotNull final Gson gson;
    private @NotNull final Logger logger;
    private @NotNull final CircuitBreaker circuitBreaker;
    private final boolean debugMode;

    public ModlHttpClientV2Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain, boolean debugMode) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.debugMode = debugMode;
        this.circuitBreaker = new CircuitBreaker();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(new ThreadPoolExecutor(0, 8, 60L, TimeUnit.SECONDS,
                        new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "modl-http");
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
        this.logger = Logger.getLogger(ModlHttpClientV2Impl.class.getName());
    }

    private HttpRequest.Builder requestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header(HEADER_API_KEY, apiKey)
                .header(HEADER_SERVER_DOMAIN, serverDomain);
    }

    @NotNull @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "?punishmentLimit=14&noteLimit=14")
                .GET()
                .build(), PlayerProfileResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "/linked-accounts")
                .GET()
                .build(), LinkedAccountsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) logger.info(String.format("[V2] Player login request body: %s", requestBody));

        return sendAsync(requestBuilder("/minecraft/players/login")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .timeout(LOGIN_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), PlayerLoginResponse.class, "LOGIN");
    }

    @NotNull @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/disconnect")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return sendAsync(requestBuilder("/minecraft/tickets")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), CreateTicketResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) logger.info(String.format("[V2] Create unfinished ticket request body: %s", requestBody));

        return sendAsync(requestBuilder("/minecraft/tickets/unfinished")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), CreateTicketResponse.class, "CREATE_UNFINISHED_TICKET");
    }

    @NotNull @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {

        return sendAsync(requestBuilder("/minecraft/punishments/create")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/dynamic")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PunishmentCreateResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request) {
        return sendAsync(requestBuilder("/minecraft/players?minecraftUuid=" + request.getMinecraftUuid())
                .GET()
                .build(), PlayerGetResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/by-name?username=" + request.getMinecraftUsername())
                .GET()
                .build(), PlayerNameResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerNoteCreateResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) logger.info(String.format("[V2] Sync request body: %s", requestBody));

        return sendAsync(requestBuilder("/minecraft/players/sync")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .timeout(SYNC_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(), SyncResponse.class, "SYNC");
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/acknowledge")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return sendAsync(requestBuilder("/minecraft/notifications/acknowledge")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        return sendAsync(requestBuilder("/minecraft/punishments/types")
                .GET()
                .build(), PunishmentTypesResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        return sendAsync(requestBuilder("/minecraft/staff/permissions")
                .GET()
                .build(), StaffPermissionsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/lookup")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerLookupResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(@NotNull PlayerLookupRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/lookup-profile?punishmentLimit=14&noteLimit=14")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerProfileResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/pardon")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PardonResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/pardon")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PardonResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return sendAsync(requestBuilder("/minecraft/migration/progress")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                                 String country, String region, String asn, boolean proxy, boolean hosting) {
        Map<String, Object> body = new HashMap<>();
        body.put("minecraftUUID", minecraftUUID);
        body.put("ip", ip);
        body.put("country", country);
        body.put("region", region);
        body.put("asn", asn);
        body.put("proxy", proxy);
        body.put("hosting", hosting);
        return sendAsync(requestBuilder("/minecraft/players/submit-ip-info")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        return sendAsync(requestBuilder("/minecraft/players/online")
                .GET()
                .build(), OnlinePlayersResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        return sendAsync(requestBuilder("/minecraft/punishments/recent?hours=" + hours)
                .GET()
                .build(), RecentPunishmentsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<ReportsResponse> getReports(String status) {
        String endpoint = "/minecraft/reports";
        if (status != null && !status.isEmpty()) endpoint += "?status=" + status;
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<ReportsResponse> getPlayerReports(@NotNull java.util.UUID playerUuid, String status) {
        String endpoint = "/minecraft/reports/player/" + playerUuid;
        if (status != null && !status.isEmpty()) endpoint += "?status=" + status;
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (dismissedBy != null) body.put("dismissedBy", dismissedBy);
        if (reason != null) body.put("reason", reason);

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/dismiss")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (resolvedBy != null) body.put("resolvedBy", resolvedBy);
        if (resolution != null) body.put("resolution", resolution);
        if (punishmentId != null) body.put("punishmentId", punishmentId);

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/resolve")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<TicketsResponse> getTickets(String status, String type) {
        StringBuilder endpoint = new StringBuilder("/minecraft/tickets");
        boolean hasParam = false;

        if (status != null && !status.isEmpty() && !status.equals("all")) {
            endpoint.append("?status=").append(status);
            hasParam = true;
        }
        if (type != null && !type.isEmpty()) endpoint.append(hasParam ? "&" : "?").append("type=").append(type);

        return sendAsync(requestBuilder(endpoint.toString())
                .GET()
                .build(), TicketsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        return sendAsync(requestBuilder("/minecraft/dashboard/stats")
                .GET()
                .build(), DashboardStatsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal) {
        String endpoint = "/minecraft/punishments/preview?playerUuid=" + playerUuid + "&typeOrdinal=" + typeOrdinal;
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), PunishmentPreviewResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        if (request.getIssuerId() != null) body.put("issuerId", request.getIssuerId());
        body.put("note", request.getNote());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/note")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        if (request.getIssuerId() != null) body.put("issuerId", request.getIssuerId());
        body.put("evidenceUrl", request.getEvidenceUrl());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/evidence")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        if (request.getIssuerId() != null) body.put("issuerId", request.getIssuerId());
        body.put("newDuration", request.getNewDuration());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/duration")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        if (request.getIssuerId() != null) body.put("issuerId", request.getIssuerId());
        body.put("option", request.getOption());
        body.put("enabled", request.isEnabled());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/toggle")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("playerUuid", request.getPlayerUuid());
        body.put("playerName", request.getPlayerName());

        return sendAsync(requestBuilder("/minecraft/tickets/" + request.getTicketId() + "/claim")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), ClaimTicketResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        return sendAsync(requestBuilder("/minecraft/staff")
                .GET()
                .build(), StaffListResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> reportStaffDisconnect(@NotNull String minecraftUuid, long sessionDurationMs) {
        Map<String, Object> body = new HashMap<>();
        body.put("minecraftUuid", minecraftUuid);
        body.put("sessionDurationMs", sessionDurationMs);

        return sendAsync(requestBuilder("/minecraft/staff/disconnect")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName) {
        Map<String, String> body = new HashMap<>();
        body.put("role", roleName);

        return sendAsync(requestBuilder("/minecraft/staff/" + staffId + "/role")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return sendAsync(requestBuilder("/minecraft/roles")
                .GET()
                .build(), RolesListResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions) {
        Map<String, Object> body = new HashMap<>();
        body.put("permissions", permissions);

        return sendAsync(requestBuilder("/minecraft/roles/" + roleId + "/permissions")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId) {
        return sendAsync(requestBuilder("/minecraft/punishments/" + punishmentId)
                .GET()
                .build(), PunishmentDetailResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName) {
        Map<String, String> body = new HashMap<>();
        body.put("issuerName", issuerName);

        return sendAsync(requestBuilder("/minecraft/punishments/" + punishmentId + "/upload-token")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), EvidenceUploadTokenResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName) {
        Map<String, String> body = Map.of("minecraftUuid", minecraftUuid, "serverName", serverName);
        return sendAsync(requestBuilder("/minecraft/players/update-server")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> modifyPunishmentTickets(@NotNull ModifyPunishmentTicketsRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("issuerName", request.getIssuerName());
        if (request.getIssuerId() != null) body.put("issuerId", request.getIssuerId());
        body.put("addTicketIds", request.getAddTicketIds());
        body.put("removeTicketIds", request.getRemoveTicketIds());
        body.put("modifyAssociatedTickets", request.isModifyAssociatedTickets());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/tickets")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeStatWipe(@NotNull StatWipeAcknowledgeRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("punishmentId", request.getPunishmentId());
        body.put("serverName", request.getServerName());
        body.put("success", request.isSuccess());

        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/stat-wipe-acknowledge")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<TicketsResponse> getTicketsByIds(@NotNull List<String> ticketIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ticketIds);

        return sendAsync(requestBuilder("/minecraft/tickets/by-ids")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), TicketsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(@NotNull String minecraftUuid, @NotNull String ip) {
        Map<String, String> body = Map.of("minecraftUuid", minecraftUuid, "ip", ip);
        return sendAsync(requestBuilder("/minecraft/staff/2fa/generate")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build(), Staff2faTokenResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest chatLogBatch) {
        return sendAsync(requestBuilder("/minecraft/players/chat-log")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(chatLogBatch)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest commandLogBatch) {
        return sendAsync(requestBuilder("/minecraft/players/command-log")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(commandLogBatch)))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<ChatLogsResponse> getChatLogs(@NotNull String playerUuid, int limit) {
        return sendAsync(requestBuilder("/minecraft/players/" + playerUuid + "/chat-logs?limit=" + limit)
                .GET()
                .build(), ChatLogsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<CommandLogsResponse> getCommandLogs(@NotNull String playerUuid, int limit) {
        return sendAsync(requestBuilder("/minecraft/players/" + playerUuid + "/command-logs?limit=" + limit)
                .GET()
                .build(), CommandLogsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PaginatedPunishmentsResponse> getPlayerPunishments(@NotNull UUID uuid, int page, int limit) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "/punishments?page=" + page + "&limit=" + limit)
                .GET()
                .build(), PaginatedPunishmentsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PaginatedNotesResponse> getPlayerNotes(@NotNull UUID uuid, int page, int limit) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "/notes?page=" + page + "&limit=" + limit)
                .GET()
                .build(), PaginatedNotesResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid, int page, int limit) {
        return sendAsync(requestBuilder("/minecraft/players/" + uuid + "/linked-accounts?page=" + page + "&limit=" + limit)
                .GET()
                .build(), LinkedAccountsResponse.class);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType) {
        return sendAsync(request, responseType, null);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, Class<T> responseType, String operation) {
        final Instant startTime = Instant.now();
        final String requestId = generateRequestId();

        if (!circuitBreaker.allowRequest()) {
            return CompletableFuture.failedFuture(
                    new PanelUnavailableException(request.uri().getPath(), HttpURLConnection.HTTP_UNAVAILABLE,
                            "V2 API is temporarily unavailable (circuit breaker open)"));
        }

        if (debugMode) {
            logger.info(String.format("[V2-REQ-%s] %s %s", requestId, request.method(), request.uri()));
            logger.info(String.format("[V2-REQ-%s] Headers: %s", requestId, request.headers().map()));
            request.bodyPublisher().ifPresent(body -> logger.info(String.format("[V2-REQ-%s] Body present: %s", requestId, body.getClass().getSimpleName())));
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
                            if ("LOGIN".equals(operation) || "SYNC".equals(operation) || body.length() <= MAX_LOG_BODY_LENGTH) logger.info(String.format("[V2-RES-%s] Body: %s", requestId, body));
                            else logger.info(String.format("[V2-RES-%s] Body: %s... (truncated, %d chars total)", requestId, body.substring(0, MAX_LOG_BODY_LENGTH), body.length()));
                        }
                    }

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        circuitBreaker.recordSuccess();

                        if (responseType == Void.class) return null;

                        try {
                            T result = gson.fromJson(response.body(), responseType);
                            if (debugMode) logger.info(String.format("[V2-REQ-%s] Successfully parsed response to %s", requestId, responseType.getSimpleName()));
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
                                if (errorMessage.isEmpty()) errorMessage = String.format("V2 request failed with status code %d: %s", response.statusCode(), response.body());
                            } else {
                                errorMessage = String.format("V2 request failed with status code %d: %s",
                                        response.statusCode(), response.body());
                            }
                        } catch (Exception e) {
                            errorMessage = String.format("V2 request failed with status code %d: %s",
                                    response.statusCode(), response.body());
                        }

                        if (response.statusCode() == HTTP_BAD_GATEWAY) {
                            circuitBreaker.recordFailure();
                            throw new PanelUnavailableException(request.uri().getPath(), HTTP_BAD_GATEWAY,
                                    "V2 API is temporarily unavailable (502 Bad Gateway)");
                        } else if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                            if (debugMode) logger.fine(String.format("[V2-REQ-%s] Not found (404): %s - %s", requestId,
                                request.uri().getPath(), errorMessage));
                        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                            circuitBreaker.recordFailure();
                            logger.severe(String.format("[V2-REQ-%s] Authentication failed - check API key and server domain", requestId));
                        } else if (response.statusCode() == 405) {
                            circuitBreaker.recordFailure();
                            logger.severe(String.format("[V2-REQ-%s] Method Not Allowed (405) - %s %s", requestId, request.method(), request.uri()));
                            logger.severe(String.format("[V2-REQ-%s] This usually means the endpoint exists but doesn't accept %s requests", requestId, request.method()));
                        } else if (response.statusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
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
                    Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                            ? throwable.getCause() : throwable;

                    if (!(cause instanceof PanelUnavailableException)) circuitBreaker.recordFailure();

                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw new RuntimeException("V2 HTTP request failed", throwable);
                });
    }

    private String generateRequestId() {
        return "V2-" + (System.nanoTime() % 1000000);
    }
}
