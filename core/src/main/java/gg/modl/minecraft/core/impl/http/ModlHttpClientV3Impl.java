package gg.modl.minecraft.core.impl.http;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import gg.modl.minecraft.api.http.ApiClientException;
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
import gg.modl.proto.modl.v1.ApiError;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ModlHttpClientV3Impl extends AbstractModlHttpTransport implements ModlHttpClient {
    private static final String HEADER_ACCEPT = "Accept", CONTENT_TYPE_PROTOBUF = "application/x-protobuf";

    private final @NotNull ModlHttpClientV2Impl legacyClient;

    public ModlHttpClientV3Impl(@NotNull String baseUrl, @NotNull String apiKey,
                                @NotNull String serverDomain, boolean debugMode) {
        this(baseUrl, apiKey, serverDomain, debugMode, deriveLegacyBaseUrl(baseUrl));
    }

    ModlHttpClientV3Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain,
                         boolean debugMode, @NotNull String legacyBaseUrl) {
        super(baseUrl, apiKey, serverDomain, debugMode, "V3", "modl-http-v3-");
        this.legacyClient = new ModlHttpClientV2Impl(legacyBaseUrl, apiKey, serverDomain, debugMode);
    }

    private static String deriveLegacyBaseUrl(String v3BaseUrl) {
        int index = v3BaseUrl.lastIndexOf("/v3");
        return index >= 0 ? v3BaseUrl.substring(0, index) + "/v1" : v3BaseUrl;
    }

    @Override
    public void shutdown() {
        legacyClient.shutdown();
        super.shutdown();
    }

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
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull CreatePlayerNoteRequest request) {
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

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return postVoid("/minecraft/notifications/acknowledge", PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest request) {
        return postVoid("/minecraft/players/chat-log", PlayerProtoMapper.toProto(request).toByteArray());
    }

    @NotNull @Override
    public CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest request) {
        return postVoid("/minecraft/players/command-log", PlayerProtoMapper.toProto(request).toByteArray());
    }

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

    @NotNull @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        return post("/minecraft/players/sync", SyncProtoMapper.toProto(request).toByteArray(), SYNC_TIMEOUT,
            gg.modl.proto.modl.v1.SyncResponse.parser(), SyncProtoMapper::toSyncResponse, "SYNC");
    }

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

    private <P extends Message, R> CompletableFuture<R> get(String endpoint, Parser<P> parser, Function<P, R> mapper) {
        return send(request(endpoint, "GET", null, null, null), parser, mapper, null, backgroundCircuitBreaker);
    }

    private <P extends Message, R> CompletableFuture<R> post(String endpoint, byte[] body, Duration timeout,
                                                             Parser<P> parser, Function<P, R> mapper, String operation) {
        return send(request(endpoint, "POST", body, timeout, null), parser, mapper, operation, backgroundCircuitBreaker);
    }

    private <P extends Message, R> CompletableFuture<R> post(String endpoint, byte[] body, Duration timeout,
                                                             Parser<P> parser, Function<P, R> mapper, String operation,
                                                             CircuitBreaker breaker) {
        return send(request(endpoint, "POST", body, timeout, null), parser, mapper, operation, breaker);
    }

    private CompletableFuture<Void> postVoid(String endpoint, byte[] body) {
        return sendVoid(request(endpoint, "POST", body, null, null));
    }

    private CompletableFuture<Void> patchVoid(String endpoint, byte[] body, String actingStaffId) {
        return sendVoid(request(endpoint, "PATCH", body, null, actingStaffId));
    }

    private HttpRequest request(String endpoint, String method, byte[] body, Duration timeout, String actingStaffId) {
        return new HttpRequest(baseUrl + endpoint, method, body, timeout, protoHeaders(body, actingStaffId));
    }

    private Map<String, String> protoHeaders(byte[] body, String actingStaffId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_API_KEY, apiKey);
        headers.put(HEADER_SERVER_DOMAIN, serverDomain);
        headers.put(HEADER_USER_AGENT, "modl-minecraft/" + PluginInfo.VERSION);
        headers.put(HEADER_ACCEPT, CONTENT_TYPE_PROTOBUF);
        if (actingStaffId != null && !actingStaffId.trim().isEmpty()) headers.put(HEADER_ACTING_STAFF_ID, actingStaffId);
        if (body != null) headers.put(HEADER_CONTENT_TYPE, CONTENT_TYPE_PROTOBUF);
        return headers;
    }

    private <P extends Message, R> CompletableFuture<R> send(HttpRequest request, Parser<P> parser,
                                                             Function<P, R> mapper, String operation, CircuitBreaker breaker) {
        return execute(request, operation, breaker, (requestId, body) -> decodeProto(requestId, body, parser, mapper));
    }

    private CompletableFuture<Void> sendVoid(HttpRequest request) {
        return this.<Void>execute(request, null, backgroundCircuitBreaker, (requestId, body) -> null);
    }

    private <P extends Message, R> R decodeProto(String requestId, byte[] body, Parser<P> parser, Function<P, R> mapper) {
        if (parser == null) return null;
        try {
            return mapper.apply(parser.parseFrom(body));
        } catch (InvalidProtocolBufferException e) {
            logger.severe(String.format("[V3-REQ-%s] Failed to parse response: %s", requestId, e.getMessage()));
            throw new RuntimeException("Failed to parse V3 response: " + e.getMessage(), e);
        }
    }

    @Override
    protected void logRequest(String requestId, HttpRequest request) {
        logger.info(String.format("[V3-REQ-%s] %s %s", requestId, request.method, request.url));
        if (request.body != null) logger.info(String.format("[V3-REQ-%s] Body present (%d bytes)", requestId, request.body.length));
    }

    @Override
    protected void logResponse(String requestId, int statusCode, byte[] body, long durationMs, String operation) {
        logger.info(String.format("[V3-RES-%s] Status: %d (%d bytes, took %dms)", requestId, statusCode, body.length, durationMs));
    }

    @Override
    protected RuntimeException toError(String requestId, HttpRequest request, int statusCode, byte[] responseBody) {
        String message;
        try {
            ApiError error = ApiError.parseFrom(responseBody);
            message = error.getMessage().isEmpty()
                ? String.format("V3 request failed: HTTP %d", statusCode)
                : error.getMessage();
        } catch (InvalidProtocolBufferException e) {
            message = String.format("V3 request failed: HTTP %d", statusCode);
        }

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
}
