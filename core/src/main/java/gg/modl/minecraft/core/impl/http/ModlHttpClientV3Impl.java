package gg.modl.minecraft.core.impl.http;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import gg.modl.minecraft.api.http.ApiClientException;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import java.io.File;
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
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.impl.http.proto.PlayerProtoMapper;
import gg.modl.minecraft.core.impl.http.proto.PunishmentProtoMapper;
import gg.modl.minecraft.core.impl.http.proto.StaffRoleProtoMapper;
import gg.modl.minecraft.core.impl.http.proto.SyncProtoMapper;
import gg.modl.minecraft.core.impl.http.proto.TicketProtoMapper;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.util.CircuitBreaker;
import gg.modl.minecraft.core.util.Java8Collections;
import gg.modl.proto.modl.v1.ApiError;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Proto V3 implementation of {@link ModlHttpClient}. Calls {@code /v3/minecraft/...} with
 * {@code application/x-protobuf} bodies, building proto request messages and parsing proto
 * response messages via the plugin-side {@code *ProtoMapper} classes.
 *
 * <p>Four interface methods have no V3 controller (migration status, staff 2FA token, chat/command
 * log retrieval); those are delegated to a retained {@link ModlHttpClientV2Impl} (V2 JSON), making
 * this a hybrid client until the matching V3 endpoints land.</p>
 *
 * <p>Transport scaffolding (executor, circuit breaker, timeouts, connection lifecycle) mirrors
 * {@link ModlHttpClientV2Impl}; the difference is binary {@code byte[]} bodies instead of JSON strings.</p>
 */
public class ModlHttpClientV3Impl implements ModlHttpClient {
    private static final String HEADER_API_KEY = "X-API-Key", HEADER_SERVER_DOMAIN = "X-Server-Domain",
        HEADER_CONTENT_TYPE = "Content-Type", HEADER_ACCEPT = "Accept",
        HEADER_ACTING_STAFF_ID = "X-Acting-Staff-Id",
        CONTENT_TYPE_PROTOBUF = "application/x-protobuf";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10), LOGIN_TIMEOUT = Duration.ofSeconds(15),
        SYNC_TIMEOUT = Duration.ofSeconds(20);
    private static final int HTTP_BAD_GATEWAY = 502, STATUS_UNREACHABLE = -1;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;

    private final @NotNull String baseUrl, apiKey, serverDomain;
    private final @NotNull ThreadPoolExecutor executor;
    private final @NotNull Logger logger;
    private final @NotNull CircuitBreaker backgroundCircuitBreaker;
    private final @NotNull CircuitBreaker loginCircuitBreaker;
    private final @NotNull ModlHttpClientV2Impl legacyClient;
    private final boolean debugMode;

    public ModlHttpClientV3Impl(@NotNull String baseUrl, @NotNull String apiKey,
                                @NotNull String serverDomain, boolean debugMode) {
        this(baseUrl, apiKey, serverDomain, debugMode, deriveLegacyBaseUrl(baseUrl));
    }

    ModlHttpClientV3Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain,
                         boolean debugMode, @NotNull String legacyBaseUrl) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.debugMode = debugMode;
        this.backgroundCircuitBreaker = new CircuitBreaker();
        this.loginCircuitBreaker = new CircuitBreaker();

        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(0, 8, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "modl-http-v3-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        this.logger = Logger.getLogger(ModlHttpClientV3Impl.class.getName());
        this.legacyClient = new ModlHttpClientV2Impl(legacyBaseUrl, apiKey, serverDomain, debugMode);
    }

    /**
     * Derives the {@code /v1} legacy base URL from the {@code /v3} base, so the retained V2 client
     * (used for the gap methods) targets the still-live JSON endpoints.
     */
    private static String deriveLegacyBaseUrl(String v3BaseUrl) {
        int index = v3BaseUrl.lastIndexOf("/v3");
        return index >= 0 ? v3BaseUrl.substring(0, index) + "/v1" : v3BaseUrl;
    }

    @Override
    public void shutdown() {
        legacyClient.shutdown();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- Players ----

    @NotNull @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request) {
        return post("/minecraft/players/login", PlayerProtoMapper.toProto(request).toByteArray(), LOGIN_TIMEOUT,
            gg.modl.proto.modl.v1.PlayerLoginResponse.parser(), PlayerProtoMapper::toLoginResponse, "LOGIN",
            loginCircuitBreaker);
    }

    @NotNull @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        return postVoid("/minecraft/players/disconnect", PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName) {
        byte[] body = PlayerProtoMapper.toUpdateServerRequest(
            minecraftUuid, serverName, StartupClient.getServerInstanceId()).toByteArray();
        return postVoid("/minecraft/players/update-server", body);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                                String country, String region, String asn, boolean proxy, boolean hosting) {
        byte[] body = PlayerProtoMapper.toSubmitIpInfoRequest(minecraftUUID, ip, country, region, asn, proxy, hosting)
            .toByteArray();
        return postVoid("/minecraft/players/submit-ip-info", body);
    }

    @NotNull @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        return get("/minecraft/players/online",
            gg.modl.proto.modl.v1.OnlinePlayersResponse.parser(), PlayerProtoMapper::toOnlinePlayersResponse);
    }

    @NotNull @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid) {
        return get("/minecraft/players/" + uuid + "?punishmentLimit=14&noteLimit=14",
            gg.modl.proto.modl.v1.PlayerProfileResponse.parser(), PlayerProtoMapper::toPlayerProfileResponse);
    }

    @NotNull @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request) {
        return get("/minecraft/players?minecraftUuid=" + request.getMinecraftUuid() + "&queryMojang=true",
            gg.modl.proto.modl.v1.PlayerGetResponse.parser(), PlayerProtoMapper::toPlayerGetResponse);
    }

    @NotNull @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request) {
        return get("/minecraft/players/by-name?username=" + request.getMinecraftUsername() + "&queryMojang=true",
            gg.modl.proto.modl.v1.PlayerNameResponse.parser(), PlayerProtoMapper::toPlayerNameResponse);
    }

    @NotNull @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request) {
        return post("/minecraft/players/lookup", PlayerProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PlayerLookupResponse.parser(), PlayerProtoMapper::toPlayerLookupResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(@NotNull PlayerLookupRequest request) {
        return post("/minecraft/players/lookup-profile", PlayerProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PlayerProfileResponse.parser(), PlayerProtoMapper::toPlayerProfileResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return postVoid("/minecraft/players/" + request.getTargetUuid() + "/notes",
            PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        return post("/minecraft/players/" + request.getTargetUuid() + "/notes",
            PlayerProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PlayerNoteCreateResponse.parser(), PlayerProtoMapper::toPlayerNoteCreateResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid) {
        return get("/minecraft/players/" + uuid + "/linked-accounts",
            gg.modl.proto.modl.v1.LinkedAccountsResponse.parser(), PlayerProtoMapper::toLinkedAccountsResponse);
    }

    @NotNull @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid, int page, int limit) {
        return get("/minecraft/players/" + uuid + "/linked-accounts?page=" + page + "&limit=" + limit,
            gg.modl.proto.modl.v1.LinkedAccountsResponse.parser(), PlayerProtoMapper::toLinkedAccountsResponse);
    }

    @NotNull @Override
    public CompletableFuture<PaginatedPunishmentsResponse> getPlayerPunishments(@NotNull UUID uuid, int page, int limit) {
        return get("/minecraft/players/" + uuid + "/punishments?page=" + page + "&limit=" + limit,
            gg.modl.proto.modl.v1.PaginatedPunishmentsResponse.parser(), PlayerProtoMapper::toPaginatedPunishmentsResponse);
    }

    @NotNull @Override
    public CompletableFuture<PaginatedNotesResponse> getPlayerNotes(@NotNull UUID uuid, int page, int limit) {
        return get("/minecraft/players/" + uuid + "/notes?page=" + page + "&limit=" + limit,
            gg.modl.proto.modl.v1.PaginatedNotesResponse.parser(), PlayerProtoMapper::toPaginatedNotesResponse);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return post("/minecraft/players/pardon", PlayerProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PardonResponse.parser(), PlayerProtoMapper::toPardonResponse, null);
    }

    // ---- Notifications ----

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return postVoid("/minecraft/notifications/acknowledge", PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest request) {
        gg.modl.proto.modl.v1.ChatLogBatchRequest.Builder builder = gg.modl.proto.modl.v1.ChatLogBatchRequest.newBuilder();
        if (request.getEntries() != null) {
            request.getEntries().forEach(entry -> builder.addEntries(gg.modl.proto.modl.v1.ChatLogEntry.newBuilder()
                .setUuid(nullToEmpty(entry.getUuid()))
                .setUsername(nullToEmpty(entry.getUsername()))
                .setMessage(nullToEmpty(entry.getMessage()))
                .setServer(nullToEmpty(entry.getServer()))
                .setTimestamp(entry.getTimestamp())
                .build()));
        }
        return postVoid("/minecraft/players/chat-log", builder.build().toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest request) {
        gg.modl.proto.modl.v1.CommandLogBatchRequest.Builder builder =
            gg.modl.proto.modl.v1.CommandLogBatchRequest.newBuilder();
        if (request.getEntries() != null) {
            request.getEntries().forEach(entry -> builder.addEntries(gg.modl.proto.modl.v1.CommandLogEntry.newBuilder()
                .setUuid(nullToEmpty(entry.getUuid()))
                .setUsername(nullToEmpty(entry.getUsername()))
                .setCommand(nullToEmpty(entry.getCommand()))
                .setServer(nullToEmpty(entry.getServer()))
                .setTimestamp(entry.getTimestamp())
                .build()));
        }
        return postVoid("/minecraft/players/command-log", builder.build().toByteArray());
    }

    // ---- Punishments ----

    @NotNull @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {
        return postVoid("/minecraft/punishments/create", PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return post("/minecraft/punishments/dynamic", PunishmentProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PunishmentCreateResponse.parser(), PunishmentProtoMapper::toPunishmentCreateResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        return postVoid("/minecraft/punishments/acknowledge", PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeStatWipe(@NotNull StatWipeAcknowledgeRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/stat-wipe-acknowledge",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/duration",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/toggle",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/note",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/evidence",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId,
                                                                                    @NotNull String issuerName) {
        return post("/minecraft/punishments/" + punishmentId + "/upload-token",
            PunishmentProtoMapper.toUploadTokenRequest(issuerName).toByteArray(), null,
            gg.modl.proto.modl.v1.EvidenceUploadTokenResponse.parser(), PunishmentProtoMapper::toUploadTokenResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        return post("/minecraft/punishments/" + request.getPunishmentId() + "/pardon",
            PunishmentProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.PardonResponse.parser(), PlayerProtoMapper::toPardonResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<Void> modifyPunishmentTickets(@NotNull ModifyPunishmentTicketsRequest request) {
        return postVoid("/minecraft/punishments/" + request.getPunishmentId() + "/tickets",
            PunishmentProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal) {
        return get("/minecraft/punishments/preview?playerUuid=" + playerUuid + "&typeOrdinal=" + typeOrdinal,
            gg.modl.proto.modl.v1.PunishmentPreviewResponse.parser(), PunishmentProtoMapper::toPreviewResponse);
    }

    @NotNull @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        return get("/minecraft/punishments/recent?hours=" + hours,
            gg.modl.proto.modl.v1.RecentPunishmentsResponse.parser(), PunishmentProtoMapper::toRecentResponse);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId) {
        return get("/minecraft/punishments/" + punishmentId,
            gg.modl.proto.modl.v1.PunishmentDetailResponse.parser(), PunishmentProtoMapper::toDetailResponse);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        return get("/minecraft/punishments/types",
            gg.modl.proto.modl.v1.PunishmentTypesResponse.parser(), StaffRoleProtoMapper::toPunishmentTypesResponse);
    }

    // ---- Sync ----

    @NotNull @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        return post("/minecraft/players/sync", SyncProtoMapper.toProto(request).toByteArray(), SYNC_TIMEOUT,
            gg.modl.proto.modl.v1.SyncResponse.parser(), SyncProtoMapper::toSyncResponse, "SYNC");
    }

    // ---- Tickets ----

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return post("/minecraft/tickets", TicketProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.MinecraftCreateTicketResponse.parser(), TicketProtoMapper::toCreateTicketResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        return post("/minecraft/tickets/unfinished", TicketProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.MinecraftCreateTicketResponse.parser(), TicketProtoMapper::toCreateTicketResponse,
            "CREATE_UNFINISHED_TICKET");
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
        return get(endpoint.toString(),
            gg.modl.proto.modl.v1.TicketsResponse.parser(), TicketProtoMapper::toTicketsResponse);
    }

    @NotNull @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        return post("/minecraft/tickets/" + request.getTicketId() + "/claim",
            TicketProtoMapper.toProto(request).toByteArray(), null,
            gg.modl.proto.modl.v1.ClaimTicketResponse.parser(), TicketProtoMapper::toClaimTicketResponse, null);
    }

    @NotNull @Override
    public CompletableFuture<TicketsResponse> getTicketsByIds(@NotNull List<String> ticketIds) {
        return post("/minecraft/tickets/by-ids", TicketProtoMapper.toTicketsByIdsRequest(ticketIds).toByteArray(), null,
            gg.modl.proto.modl.v1.TicketsResponse.parser(), TicketProtoMapper::toTicketsResponse, null);
    }

    // ---- Reports ----

    @NotNull @Override
    public CompletableFuture<ReportsResponse> getReports(String status) {
        String endpoint = "/minecraft/reports";
        if (status != null && !status.isEmpty()) endpoint += "?status=" + status;
        return get(endpoint, gg.modl.proto.modl.v1.ReportsResponse.parser(), PlayerProtoMapper::toReportsResponse);
    }

    @NotNull @Override
    public CompletableFuture<ReportsResponse> getPlayerReports(@NotNull UUID playerUuid, String status) {
        String endpoint = "/minecraft/reports/player/" + playerUuid;
        if (status != null && !status.isEmpty()) endpoint += "?status=" + status;
        return get(endpoint, gg.modl.proto.modl.v1.ReportsResponse.parser(), PlayerProtoMapper::toReportsResponse);
    }

    @NotNull @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        return postVoid("/minecraft/reports/" + reportId + "/dismiss",
            TicketProtoMapper.toDismissReportRequest(dismissedBy, reason).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution,
                                                String punishmentId) {
        return postVoid("/minecraft/reports/" + reportId + "/resolve",
            TicketProtoMapper.toResolveReportRequest(resolvedBy, resolution, punishmentId).toByteArray());
    }

    // ---- Staff / Roles / Dashboard ----

    @NotNull @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        return get("/minecraft/staff",
            gg.modl.proto.modl.v1.StaffListResponse.parser(), StaffRoleProtoMapper::toStaffListResponse);
    }

    @NotNull @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        return get("/minecraft/staff/permissions",
            gg.modl.proto.modl.v1.StaffPermissionsListResponse.parser(), StaffRoleProtoMapper::toStaffPermissionsResponse);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName, String actingStaffId) {
        return patchVoid("/minecraft/staff/" + staffId + "/role",
            StaffRoleProtoMapper.toUpdateStaffRoleRequest(roleName).toByteArray(), actingStaffId);
    }

    @NotNull @Override
    public CompletableFuture<Void> reportStaffDisconnect(@NotNull String minecraftUuid, long sessionDurationMs) {
        return postVoid("/minecraft/staff/disconnect",
            StaffRoleProtoMapper.toStaffDisconnectRequest(minecraftUuid, sessionDurationMs).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return get("/minecraft/roles",
            gg.modl.proto.modl.v1.MinecraftRoleListResponse.parser(), StaffRoleProtoMapper::toRolesListResponse);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions, String actingStaffId) {
        return patchVoid("/minecraft/roles/" + roleId + "/permissions",
            StaffRoleProtoMapper.toUpdateRolePermissionsRequest(permissions).toByteArray(), actingStaffId);
    }

    @NotNull @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        return get("/minecraft/dashboard/stats",
            gg.modl.proto.modl.v1.MinecraftDashboardResponse.parser(), StaffRoleProtoMapper::toDashboardStatsResponse);
    }

    // ---- Gap methods: no V3 controller, delegated to V2 JSON (hybrid) ----

    @NotNull @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return legacyClient.updateMigrationStatus(request);
    }

    @NotNull @Override
    public CompletableFuture<Boolean> uploadMigrationFile(@NotNull File file) {
        return legacyClient.uploadMigrationFile(file);
    }

    @NotNull @Override
    public CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(@NotNull String minecraftUuid, @NotNull String ip) {
        return legacyClient.generateStaff2faToken(minecraftUuid, ip);
    }

    @NotNull @Override
    public CompletableFuture<ChatLogsResponse> getChatLogs(@NotNull String playerUuid, int limit) {
        return legacyClient.getChatLogs(playerUuid, limit);
    }

    @NotNull @Override
    public CompletableFuture<CommandLogsResponse> getCommandLogs(@NotNull String playerUuid, int limit) {
        return legacyClient.getCommandLogs(playerUuid, limit);
    }

    // ---- Transport ----

    private <P extends com.google.protobuf.Message, R> CompletableFuture<R> get(
        String endpoint, Parser<P> parser, Function<P, R> mapper) {
        return send(new ProtoRequest(baseUrl + endpoint, "GET", null, null), parser, mapper, null);
    }

    private <P extends com.google.protobuf.Message, R> CompletableFuture<R> post(
        String endpoint, byte[] body, Duration timeout, Parser<P> parser, Function<P, R> mapper, String operation) {
        return send(new ProtoRequest(baseUrl + endpoint, "POST", body, timeout), parser, mapper, operation);
    }

    private <P extends com.google.protobuf.Message, R> CompletableFuture<R> post(
        String endpoint, byte[] body, Duration timeout, Parser<P> parser, Function<P, R> mapper, String operation,
        CircuitBreaker breaker) {
        return send(new ProtoRequest(baseUrl + endpoint, "POST", body, timeout), parser, mapper, operation, breaker);
    }

    private CompletableFuture<Void> postVoid(String endpoint, byte[] body) {
        return sendVoid(new ProtoRequest(baseUrl + endpoint, "POST", body, null));
    }

    private CompletableFuture<Void> patchVoid(String endpoint, byte[] body) {
        return sendVoid(new ProtoRequest(baseUrl + endpoint, "PATCH", body, null));
    }

    private CompletableFuture<Void> patchVoid(String endpoint, byte[] body, String actingStaffId) {
        return sendVoid(new ProtoRequest(baseUrl + endpoint, "PATCH", body, null, actingStaffId));
    }

    private CompletableFuture<Void> sendVoid(ProtoRequest request) {
        return send(request, null, ignored -> null, null);
    }

    private <P extends com.google.protobuf.Message, R> CompletableFuture<R> send(
        ProtoRequest request, Parser<P> parser, Function<P, R> mapper, String operation) {
        return send(request, parser, mapper, operation, backgroundCircuitBreaker);
    }

    private <P extends com.google.protobuf.Message, R> CompletableFuture<R> send(
        ProtoRequest request, Parser<P> parser, Function<P, R> mapper, String operation, CircuitBreaker breaker) {
        final Instant startTime = Instant.now();
        final String requestId = generateRequestId();

        if (!breaker.allowRequest()) {
            return Java8Collections.failedFuture(new PanelUnavailableException(
                request.url, HttpURLConnection.HTTP_UNAVAILABLE,
                "V3 API is temporarily unavailable (circuit breaker open)"));
        }

        if (debugMode) {
            logger.info(String.format("[V3-REQ-%s] %s %s", requestId, request.method, request.url));
            if (request.body != null) logger.info(String.format("[V3-REQ-%s] Body present (%d bytes)", requestId, request.body.length));
        }

        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(request);

                int statusCode = connection.getResponseCode();
                byte[] responseBody = readBody(connection, statusCode);
                final Duration duration = Duration.between(startTime, Instant.now());

                if (debugMode) {
                    logger.info(String.format("[V3-RES-%s] Status: %d (%d bytes, took %dms)",
                        requestId, statusCode, responseBody.length, duration.toMillis()));
                }

                if (statusCode >= 200 && statusCode < 300) {
                    breaker.recordSuccess();
                    if (parser == null) return null;
                    try {
                        return mapper.apply(parser.parseFrom(responseBody));
                    } catch (InvalidProtocolBufferException e) {
                        logger.severe(String.format("[V3-REQ-%s] Failed to parse response: %s", requestId, e.getMessage()));
                        throw new RuntimeException("Failed to parse V3 response: " + e.getMessage(), e);
                    }
                }

                throw toError(requestId, request, statusCode, responseBody);
            } catch (RuntimeException e) {
                throw e;
            } catch (java.io.IOException e) {
                // Socket-level failure (connect-refused, DNS, read-timeout) -> panel unreachable (fail-closed on login).
                throw new PanelUnavailableException(request.url, STATUS_UNREACHABLE,
                    "V3 API unreachable: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException("V3 HTTP request failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, executor)
            .exceptionally(throwable -> {
                // Single funnel for circuit-breaker accounting: every failed call records exactly
                // once here (toError() classifies/logs but no longer records, to avoid double-counting).
                // 4xx client outcomes (ApiClientException) are routine and must NOT count.
                Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                    ? throwable.getCause() : throwable;
                if (!(cause instanceof ApiClientException)) breaker.recordFailure();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException("V3 HTTP request failed", throwable);
            });
    }

    private HttpURLConnection open(ProtoRequest request) throws Exception {
        URL url = new URL(request.url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(request.method);
        connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        connection.setReadTimeout((int) (request.timeout != null ? request.timeout : CONNECT_TIMEOUT).toMillis());
        connection.setInstanceFollowRedirects(true);

        connection.setRequestProperty(HEADER_API_KEY, apiKey);
        connection.setRequestProperty(HEADER_SERVER_DOMAIN, serverDomain);
        connection.setRequestProperty("User-Agent", "modl-minecraft/" + PluginInfo.VERSION);
        connection.setRequestProperty(HEADER_ACCEPT, CONTENT_TYPE_PROTOBUF);
        if (request.actingStaffId != null && !request.actingStaffId.trim().isEmpty()) {
            connection.setRequestProperty(HEADER_ACTING_STAFF_ID, request.actingStaffId);
        }

        if (request.body != null) {
            connection.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_PROTOBUF);
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(request.body);
            }
        }
        return connection;
    }

    private static byte[] readBody(HttpURLConnection connection, int statusCode) {
        try {
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return new byte[0];
            try (InputStream in = stream) {
                return readAllBytes(in);
            }
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] readAllBytes(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private RuntimeException toError(String requestId, ProtoRequest request, int statusCode, byte[] responseBody) {
        String message;
        try {
            ApiError error = ApiError.parseFrom(responseBody);
            message = error.getMessage().isEmpty()
                ? String.format("V3 request failed: HTTP %d", statusCode)
                : error.getMessage();
        } catch (InvalidProtocolBufferException e) {
            message = String.format("V3 request failed: HTTP %d", statusCode);
        }

        // Classify/log only; circuit-breaker accounting happens once in the send() exceptionally funnel.
        // Any 5xx is treated as "panel unreachable" (fail-closed on the login path); 4xx is a routine
        // client outcome that must NOT count toward the circuit breaker.
        if (statusCode >= 500 && statusCode < 600) {
            if (debugMode) logger.warning(String.format("[V3-REQ-%s] HTTP %d - %s %s: %s", requestId, statusCode, request.method, request.url, message));
            return new PanelUnavailableException(request.url, statusCode,
                "V3 API is temporarily unavailable (HTTP " + statusCode + ")");
        } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            if (debugMode) logger.fine(String.format("[V3-REQ-%s] Not found (404): %s - %s", requestId, request.url, message));
        } else if (statusCode == 401 || statusCode == 403) {
            logger.severe(String.format("[V3-REQ-%s] Authentication failed - check API key and server domain", requestId));
        } else if (statusCode == 405) {
            logger.severe(String.format("[V3-REQ-%s] HTTP %d - %s %s: %s", requestId, statusCode, request.method, request.url, message));
        } else {
            logger.warning(String.format("[V3-REQ-%s] %s", requestId, message));
        }

        if (statusCode >= 400 && statusCode < 500) return new ApiClientException(statusCode, message);
        return new RuntimeException(message);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String generateRequestId() {
        return "V3-" + (System.nanoTime() % 1000000);
    }

    private static final class ProtoRequest {
        final String url;
        final String method;
        final byte[] body;
        final Duration timeout;
        final String actingStaffId;

        ProtoRequest(String url, String method, byte[] body, Duration timeout) {
            this(url, method, body, timeout, null);
        }

        ProtoRequest(String url, String method, byte[] body, Duration timeout, String actingStaffId) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.timeout = timeout;
            this.actingStaffId = actingStaffId;
        }
    }
}
