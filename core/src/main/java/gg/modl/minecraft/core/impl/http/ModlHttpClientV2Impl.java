package gg.modl.minecraft.core.impl.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.util.CircuitBreaker;
import org.jetbrains.annotations.NotNull;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.File;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ModlHttpClientV2Impl extends AbstractModlHttpTransport implements ModlHttpClient {
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int MAX_LOG_BODY_LENGTH = 1000;
    private static final String[] FALLBACK_DATE_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss",
            "MMM d, yyyy, h:mm:ss a",
    };

    private @NotNull final Gson gson;

    public ModlHttpClientV2Impl(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain, boolean debugMode) {
        super(baseUrl, apiKey, serverDomain, debugMode, "V2", "modl-http-");
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Date.class, flexibleDateDeserializer())
                .create();
    }

    private static JsonDeserializer<Date> flexibleDateDeserializer() {
        return new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(JsonElement json, Type typeOfT,
                                    JsonDeserializationContext context) throws JsonParseException {
                if (json == null || json.isJsonNull()) {
                    return null;
                }
                if (!json.isJsonPrimitive()) {
                    throw new JsonParseException("Unsupported date payload: " + json);
                }
                if (json.getAsJsonPrimitive().isNumber()) {
                    return new Date(json.getAsLong());
                }
                String value = json.getAsString();
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                    return null;
                }
                try {
                    return Date.from(Instant.parse(trimmed));
                } catch (DateTimeParseException ignored) {
                }
                try {
                    return Date.from(OffsetDateTime.parse(trimmed).toInstant());
                } catch (DateTimeParseException ignored) {
                }
                try {
                    return new Date(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                }
                for (String pattern : FALLBACK_DATE_PATTERNS) {
                    try {
                        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                        return format.parse(trimmed);
                    } catch (ParseException ignored) {
                    }
                }
                throw new JsonParseException(trimmed);
            }
        };
    }

    private static class RequestConfig {
        final String url;
        final String method;
        final Map<String, String> headers = new LinkedHashMap<>();
        final String body;
        final Duration timeout;

        RequestConfig(String url, String method, String body, Duration timeout) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.timeout = timeout;
        }
    }

    private class RequestBuilder {
        private final String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String method = "GET";
        private String body = null;
        private Duration timeout = null;

        RequestBuilder(String url) {
            this.url = url;
            headers.put(HEADER_API_KEY, apiKey);
            headers.put(HEADER_SERVER_DOMAIN, serverDomain);
            headers.put(HEADER_USER_AGENT, "modl-minecraft/" + PluginInfo.VERSION);
        }

        RequestBuilder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        RequestBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        RequestBuilder GET() {
            this.method = "GET";
            this.body = null;
            return this;
        }

        RequestBuilder POST(String body) {
            this.method = "POST";
            this.body = body;
            return this;
        }

        RequestBuilder method(String method, String body) {
            this.method = method;
            this.body = body;
            return this;
        }

        RequestConfig build() {
            RequestConfig config = new RequestConfig(url, method, body, timeout);
            config.headers.putAll(headers);
            return config;
        }
    }

    private RequestBuilder requestBuilder(String endpoint) {
        return new RequestBuilder(baseUrl + endpoint);
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
                .POST(requestBody)
                .build(), PlayerLoginResponse.class, "LOGIN", loginCircuitBreaker);
    }

    @NotNull @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/disconnect")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return sendAsync(requestBuilder("/minecraft/tickets")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), CreateTicketResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) logger.info(String.format("[V2] Create unfinished ticket request body: %s", requestBody));

        return sendAsync(requestBuilder("/minecraft/tickets/unfinished")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(requestBody)
                .build(), CreateTicketResponse.class, "CREATE_UNFINISHED_TICKET");
    }

    @NotNull @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {

        return sendAsync(requestBuilder("/minecraft/punishments/create")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/dynamic")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
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
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull CreatePlayerNoteRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), PlayerNoteCreateResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        String requestBody = gson.toJson(request);
        if (debugMode) logger.info(String.format("[V2] Sync request body: %s", requestBody));

        String v2SyncUrl = deriveV2SyncBaseUrl() + "/minecraft/players/sync";
        return sendAsync(new RequestBuilder(v2SyncUrl)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .timeout(SYNC_TIMEOUT)
                .POST(requestBody)
                .build(), SyncResponse.class, "SYNC");
    }

    private String deriveV2SyncBaseUrl() {
        int index = baseUrl.lastIndexOf("/v1");
        return index >= 0 ? baseUrl.substring(0, index) + "/v2" : baseUrl;
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/acknowledge")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return sendAsync(requestBuilder("/minecraft/notifications/acknowledge")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
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
                .POST(gson.toJson(request))
                .build(), PlayerLookupResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(@NotNull PlayerLookupRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/lookup-profile?punishmentLimit=14&noteLimit=14")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), PlayerProfileResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/pardon")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), PardonResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/pardon")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
                .build(), PardonResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return sendAsync(requestBuilder("/minecraft/migration/progress")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(request))
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
                .POST(gson.toJson(body))
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
    public CompletableFuture<ReportsResponse> getPlayerReports(@NotNull UUID playerUuid, String status) {
        String endpoint = "/minecraft/reports/player/" + playerUuid;
        if (status != null && !status.isEmpty()) endpoint += "?status=" + status;
        return sendAsync(requestBuilder(endpoint)
                .GET()
                .build(), ReportsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        Map<String, String> body = new HashMap<>();
        if (dismissedBy != null) body.put("dismissedBy", dismissedBy);
        if (reason != null) body.put("reason", reason);

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/dismiss")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId) {
        Map<String, String> body = new HashMap<>();
        if (resolvedBy != null) body.put("resolvedBy", resolvedBy);
        if (resolution != null) body.put("resolution", resolution);
        if (punishmentId != null) body.put("punishmentId", punishmentId);

        return sendAsync(requestBuilder("/minecraft/reports/" + reportId + "/resolve")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        Map<String, String> body = new HashMap<>();
        body.put("playerUuid", request.getPlayerUuid());
        body.put("playerName", request.getPlayerName());

        return sendAsync(requestBuilder("/minecraft/tickets/" + request.getTicketId() + "/claim")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName, String actingStaffId) {
        Map<String, String> body = new HashMap<>();
        body.put("role", roleName);

        RequestBuilder builder = requestBuilder("/minecraft/staff/" + staffId + "/role")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        if (actingStaffId != null && !actingStaffId.trim().isEmpty()) builder.header(HEADER_ACTING_STAFF_ID, actingStaffId);

        return sendAsync(builder
                .method("PATCH", gson.toJson(body))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return sendAsync(requestBuilder("/minecraft/roles")
                .GET()
                .build(), RolesListResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Boolean> uploadMigrationFile(@NotNull File file) {
        return CompletableFuture.supplyAsync(() -> {
            String uploadUrl = baseUrl + "/minecraft/migration/upload";
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addBinaryBody("migrationFile", file, ContentType.APPLICATION_JSON, file.getName());

                HttpPost post = new HttpPost(uploadUrl);
                post.setHeader(HEADER_API_KEY, apiKey);
                post.setHeader(HEADER_SERVER_DOMAIN, serverDomain);
                post.setEntity(builder.build());

                return client.execute(post, response -> {
                    int status = response.getCode();
                    String body = EntityUtils.toString(response.getEntity());
                    if (status >= 200 && status < 300) return true;
                    if (status == 413) logger.severe("Migration file too large: " + body);
                    else logger.severe("Migration upload failed with status " + status + ": " + body);
                    return false;
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error uploading migration file: " + e.getMessage(), e);
                return false;
            }
        }, executor);
    }

    @NotNull @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions, String actingStaffId) {
        Map<String, Object> body = new HashMap<>();
        body.put("permissions", permissions);

        RequestBuilder builder = requestBuilder("/minecraft/roles/" + roleId + "/permissions")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        if (actingStaffId != null && !actingStaffId.trim().isEmpty()) builder.header(HEADER_ACTING_STAFF_ID, actingStaffId);

        return sendAsync(builder
                .method("PATCH", gson.toJson(body))
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
                .POST(gson.toJson(body))
                .build(), EvidenceUploadTokenResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName) {
        Map<String, String> body = new HashMap<>();
        body.put("minecraftUuid", minecraftUuid);
        body.put("serverName", serverName);
        String serverInstanceId = StartupClient.getServerInstanceId();
        if (serverInstanceId != null) body.put("serverInstanceId", serverInstanceId);
        return sendAsync(requestBuilder("/minecraft/players/update-server")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
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
                .POST(gson.toJson(body))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<TicketsResponse> getTicketsByIds(@NotNull List<String> ticketIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ticketIds);

        return sendAsync(requestBuilder("/minecraft/tickets/by-ids")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
                .build(), TicketsResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(@NotNull String minecraftUuid, @NotNull String ip) {
        Map<String, String> body = new HashMap<>();
        body.put("minecraftUuid", minecraftUuid);
        body.put("ip", ip);
        return sendAsync(requestBuilder("/minecraft/staff/2fa/generate")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(body))
                .build(), Staff2faTokenResponse.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest chatLogBatch) {
        return sendAsync(requestBuilder("/minecraft/players/chat-log")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(chatLogBatch))
                .build(), Void.class);
    }

    @NotNull @Override
    public CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest commandLogBatch) {
        return sendAsync(requestBuilder("/minecraft/players/command-log")
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(gson.toJson(commandLogBatch))
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

    private <T> CompletableFuture<T> sendAsync(RequestConfig request, Class<T> responseType) {
        return sendAsync(request, responseType, null);
    }

    private <T> CompletableFuture<T> sendAsync(RequestConfig request, Class<T> responseType, String operation) {
        return sendAsync(request, responseType, operation, backgroundCircuitBreaker);
    }

    private <T> CompletableFuture<T> sendAsync(RequestConfig request, Class<T> responseType, String operation,
                                               CircuitBreaker breaker) {
        byte[] body = request.body == null ? null : request.body.getBytes(StandardCharsets.UTF_8);
        HttpRequest httpRequest = new HttpRequest(request.url, request.method, body, request.timeout, request.headers);
        return execute(httpRequest, operation, breaker,
                (requestId, responseBody) -> decodeJson(requestId, responseBody, responseType));
    }

    private <T> T decodeJson(String requestId, byte[] responseBody, Class<T> responseType) {
        if (responseType == Void.class) return null;
        String text = new String(responseBody, StandardCharsets.UTF_8);
        try {
            T result = gson.fromJson(text, responseType);
            if (debugMode) logger.info(String.format("[V2-REQ-%s] Successfully parsed response to %s", requestId, responseType.getSimpleName()));
            return result;
        } catch (Exception e) {
            logger.severe(String.format("[V2-REQ-%s] Failed to parse response: %s", requestId, e.getMessage()));
            throw new RuntimeException("Failed to parse V2 response: " + e.getMessage(), e);
        }
    }

    @Override
    protected void logRequest(String requestId, HttpRequest request) {
        logger.info(String.format("[V2-REQ-%s] %s %s", requestId, request.method, request.url));
        logger.info(String.format("[V2-REQ-%s] Headers: %s", requestId, redactHeaders(request.headers)));
        if (request.body != null) logger.info(String.format("[V2-REQ-%s] Body present", requestId));
    }

    @Override
    protected void logResponse(String requestId, int statusCode, byte[] body, long durationMs, String operation) {
        logger.info(String.format("[V2-RES-%s] Status: %d (took %dms)", requestId, statusCode, durationMs));
        if (body.length == 0) return;
        String responseBody = new String(body, StandardCharsets.UTF_8);
        if ("LOGIN".equals(operation) || "SYNC".equals(operation) || responseBody.length() <= MAX_LOG_BODY_LENGTH) {
            logger.info(String.format("[V2-RES-%s] Body: %s", requestId, responseBody));
        } else {
            logger.info(String.format("[V2-RES-%s] Body: %s... (truncated, %d chars total)",
                    requestId, responseBody.substring(0, MAX_LOG_BODY_LENGTH), responseBody.length()));
        }
    }

    @Override
    protected RuntimeException toError(String requestId, HttpRequest request, int statusCode, byte[] body) {
        String responseBody = new String(body, StandardCharsets.UTF_8);
        String errorMessage = extractErrorMessage(responseBody, statusCode);

        if (statusCode >= 500 && statusCode < 600) {
            logger.severe(String.format("[V2-REQ-%s] Server error (HTTP %d) - %s %s: %s", requestId, statusCode, request.method, request.url, responseBody));
            return new PanelUnavailableException(request.url, statusCode,
                    "V2 API is temporarily unavailable (HTTP " + statusCode + ")");
        } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            if (debugMode) logger.fine(String.format("[V2-REQ-%s] Not found (404): %s - %s", requestId, request.url, errorMessage));
        } else if (statusCode == 401 || statusCode == 403) {
            logger.severe(String.format("[V2-REQ-%s] Authentication failed - check API key and server domain", requestId));
        } else if (statusCode == 405) {
            logger.severe(String.format("[V2-REQ-%s] Method Not Allowed (405) - %s %s", requestId, request.method, request.url));
            logger.severe(String.format("[V2-REQ-%s] This usually means the endpoint exists but doesn't accept %s requests", requestId, request.method));
        } else {
            logger.warning(String.format("[V2-REQ-%s] %s", requestId, errorMessage));
        }

        if (statusCode >= 400 && statusCode < 500) return new ApiClientException(statusCode, errorMessage);
        return new RuntimeException(errorMessage);
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JsonObject errorResponse = gson.fromJson(responseBody, JsonObject.class);
            if (errorResponse != null) {
                String error = errorResponse.has("error") ? errorResponse.get("error").getAsString() : "";
                if (error.isEmpty() && errorResponse.has("message")) error = errorResponse.get("message").getAsString();
                String details = errorResponse.has("errors") ? errorResponse.get("errors").getAsString() : "";
                String message = !details.isEmpty() ? error + " Details: " + details : error;
                return message.isEmpty()
                        ? String.format("V2 request failed with status code %d: %s", statusCode, responseBody)
                        : message;
            }
        } catch (Exception ignored) {
        }
        return String.format("V2 request failed with status code %d: %s", statusCode, responseBody);
    }

    private Map<String, String> redactHeaders(Map<String, String> headers) {
        Map<String, String> copy = new LinkedHashMap<>(headers);
        if (copy.containsKey(HEADER_API_KEY)) copy.put(HEADER_API_KEY, mask(copy.get(HEADER_API_KEY)));
        return copy;
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) return "***";
        int visible = Math.min(4, value.length());
        return "***" + value.substring(value.length() - visible);
    }
}
