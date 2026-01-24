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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * V2 API HTTP client implementation.
 * Connects to centralized API at api.modl.gg or api.cobl.gg.
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
        String requestBody = gson.toJson(request);
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
        return sendAsync(requestBuilder("/minecraft/players/disconnect")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
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
        return sendAsync(requestBuilder("/minecraft/punishments/create")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/notes/create")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/dynamic")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
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
        return sendAsync(requestBuilder("/minecraft/players-name?username=" + request.getMinecraftUsername())
                .GET()
                .build(), PlayerNameResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/" + request.getTargetUuid() + "/notes")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerNoteCreateResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        String requestBody = gson.toJson(request);
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
        return sendAsync(requestBuilder("/minecraft/punishments/acknowledge")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return sendAsync(requestBuilder("/minecraft/notifications/acknowledge")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
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
        return sendAsync(requestBuilder("/minecraft/players/lookup")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), PlayerLookupResponse.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        return sendAsync(requestBuilder("/minecraft/punishments/" + request.getPunishmentId() + "/pardon")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return sendAsync(requestBuilder("/minecraft/players/pardon")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build(), Void.class);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return sendAsync(requestBuilder("/minecraft/migration/progress")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
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
                            if (errorResponse != null && errorResponse.has("message")) {
                                errorMessage = errorResponse.get("message").getAsString();
                            } else {
                                errorMessage = String.format("V2 request failed with status code %d: %s",
                                        response.statusCode(), response.body());
                            }
                        } catch (Exception e) {
                            errorMessage = String.format("V2 request failed with status code %d: %s",
                                    response.statusCode(), response.body());
                        }

                        circuitBreaker.recordFailure();

                        if (response.statusCode() == 502) {
                            throw new PanelUnavailableException(502, request.uri().getPath(),
                                    "V2 API is temporarily unavailable (502 Bad Gateway)");
                        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                            logger.severe(String.format("[V2-REQ-%s] Authentication failed - check API key and server domain", requestId));
                        } else if (response.statusCode() == 404) {
                            logger.severe(String.format("[V2-REQ-%s] Endpoint not found: %s", requestId, request.uri().getPath()));
                        }

                        logger.warning(String.format("[V2-REQ-%s] %s", requestId, errorMessage));
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
