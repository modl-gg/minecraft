package gg.modl.minecraft.core.realtime;

import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.proto.modl.v1.Ack;
import gg.modl.proto.modl.v1.AckKind;
import gg.modl.proto.modl.v1.ClientHello;
import gg.modl.proto.modl.v1.ClientKind;
import gg.modl.proto.modl.v1.ErrorCode;
import gg.modl.proto.modl.v1.Heartbeat;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.ReconnectAction;
import gg.modl.proto.modl.v1.Subscribe;
import gg.modl.proto.modl.v1.Topic;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;

public class MinecraftRealtimeClient {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 25;
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final long MAX_ADVISED_RECONNECT_DELAY_MS = 60000;
    private static final int RECENT_EVENT_ID_CAPACITY = 256;
    private static final Set<Topic> ALLOWED_STARTUP_TOPICS = allowedStartupTopics();

    private final String apiKey;
    private final String realtimeUrl;
    private final int protocolVersion;
    private final List<Topic> topics;
    private final String serverName;
    private final String serverInstanceId;
    private final SyncService syncService;
    private final PluginLogger logger;
    private final boolean debugMode;
    private final ScheduledExecutorService executor;
    private final AtomicLong heartbeatSequence = new AtomicLong(0);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final RecentRealtimeEventIds recentEventIds = new RecentRealtimeEventIds(RECENT_EVENT_ID_CAPACITY);
    private final Random random = new Random();

    private volatile WebSocketClient client;
    private volatile boolean running = false;
    private volatile boolean connectedOnce = false;
    private volatile boolean terminallyRejected = false;
    private volatile int reconnectAttempt = 0;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile Long reconnectDelayOverrideMs;

    public MinecraftRealtimeClient(String apiKey, StartupResponse startupResponse, String serverName,
                                   SyncService syncService, PluginLogger logger, boolean debugMode) {
        this.apiKey = apiKey;
        this.realtimeUrl = startupResponse.getRealtimeUrl();
        this.protocolVersion = startupResponse.getRealtimeProtocolVersion() != null
            ? startupResponse.getRealtimeProtocolVersion()
            : 1;
        this.serverInstanceId = normalize(startupResponse.getServerInstanceId());
        this.topics = parseTopics(startupResponse.getRealtimeTopics(), serverInstanceId);
        this.serverName = serverName;
        this.syncService = syncService;
        this.logger = logger;
        this.debugMode = debugMode;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "modl-realtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static boolean canStart(boolean localEnabled, StartupResponse startupResponse) {
        if (!localEnabled || startupResponse == null) return false;
        if (!Boolean.TRUE.equals(startupResponse.getRealtimeEnabled())) return false;
        String url = startupResponse.getRealtimeUrl();
        if (url == null || url.trim().isEmpty()) return false;
        return !parseTopics(startupResponse.getRealtimeTopics(), normalize(startupResponse.getServerInstanceId())).isEmpty();
    }

    public void start() {
        if (running) return;
        running = true;
        executor.execute(this::connectOnce);
    }

    public void stop() {
        running = false;
        WebSocketClient current = client;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
        cancelHeartbeat();
        executor.shutdownNow();
    }

    private void connectOnce() {
        if (!running) return;
        reconnectScheduled.set(false);

        try {
            URI uri = URI.create(realtimeUrl);
            Map<String, String> headers = new HashMap<>();
            headers.put(API_KEY_HEADER, apiKey);
            headers.put("User-Agent", "modl-minecraft");

            WebSocketClient nextClient = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    handleOpen(this);
                }

                @Override
                public void onMessage(String message) {
                    if (debugMode) logger.warning("[Realtime] Ignoring text frame from backend");
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    handleBinaryMessage(this, bytes);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handleClose(code, reason);
                }

                @Override
                public void onError(Exception exception) {
                    if (running) logger.warning("[Realtime] WebSocket error: " + exception.getMessage());
                    if (!terminallyRejected) {
                        scheduleReconnect();
                    }
                }
            };

            client = nextClient;
            nextClient.connect();
        } catch (Exception e) {
            logger.warning("[Realtime] Failed to start WebSocket: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void handleOpen(WebSocketClient openedClient) {
        if (debugMode) logger.info("[Realtime] WebSocket connected, sending ClientHello");
        send(openedClient, buildClientHello());
        cancelHeartbeat();
        heartbeatTask = executor.scheduleAtFixedRate(() -> sendHeartbeat(openedClient),
            HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void handleBinaryMessage(WebSocketClient source, ByteBuffer bytes) {
        try {
            RealtimeEnvelope envelope = RealtimeEnvelope.parseFrom(bytes);
            if (!envelope.getEventId().isEmpty()) {
                send(source, buildTransportAck(envelope.getEventId()));
            }

            switch (envelope.getPayloadCase()) {
                case SERVER_HELLO:
                    reconnectAttempt = 0;
                    send(source, buildSubscribe());
                    if (connectedOnce) {
                        syncService.forceSync("realtime reconnect");
                    }
                    connectedOnce = true;
                    if (debugMode) logger.info("[Realtime] ServerHello accepted " + envelope.getServerHello().getAcceptedTopicsCount() + " topics");
                    break;
                case HEARTBEAT:
                    if (debugMode) logger.info("[Realtime] Backend heartbeat received");
                    break;
                case RECONNECT_ADVICE:
                    handleReconnectAdvice(envelope);
                    break;
                case PERMISSION_INVALIDATED:
                case PUNISHMENT_TYPE_INVALIDATED:
                    if (recentEventIds.markIfNew(envelope.getEventId())) {
                        syncService.forceSync("realtime invalidation");
                    } else if (debugMode) {
                        logger.info("[Realtime] Suppressed duplicate invalidation " + envelope.getEventId());
                    }
                    break;
                case PRESENCE_INVALIDATED:
                case PRESENCE_SNAPSHOT:
                case PRESENCE_DELTA:
                    if (debugMode) logger.info("[Realtime] Ignored advisory payload: " + envelope.getPayloadCase());
                    break;
                case ERROR:
                    logger.warning("[Realtime] Backend error: " + envelope.getError().getMessage());
                    if (isTerminalBackendError(envelope.getError().getCode())) {
                        terminallyRejected = true;
                    }
                    break;
                default:
                    if (debugMode) logger.info("[Realtime] Ignored advisory payload: " + envelope.getPayloadCase());
                    break;
            }
        } catch (Exception e) {
            logger.warning("[Realtime] Failed to process WebSocket frame: " + e.getMessage());
        }
    }

    private void handleReconnectAdvice(RealtimeEnvelope envelope) {
        if (envelope.getReconnectAdvice().getAction() == ReconnectAction.RECONNECT_ACTION_STOP) {
            logger.warning("[Realtime] Backend requested realtime stop: " + envelope.getReconnectAdvice().getMessage());
            stop();
            return;
        }
        if (envelope.getReconnectAdvice().getAction() == ReconnectAction.RECONNECT_ACTION_RECONNECT
                && envelope.getReconnectAdvice().hasRetryAfterMs()) {
            reconnectDelayOverrideMs = (long) envelope.getReconnectAdvice().getRetryAfterMs();
        }
        if (envelope.getReconnectAdvice().getAction() == ReconnectAction.RECONNECT_ACTION_RESYNC) {
            syncService.forceSync("realtime reconnect advice");
        }
    }

    private void handleClose(int code, String reason) {
        cancelHeartbeat();
        if (debugMode && running) logger.info("[Realtime] WebSocket closed (" + code + "): " + reason);
        if (!shouldReconnectAfterClose(code) || terminallyRejected) {
            running = false;
            return;
        }
        scheduleReconnect();
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }
    }

    private void scheduleReconnect() {
        if (!running || !reconnectScheduled.compareAndSet(false, true)) return;
        long delayMs = reconnectDelayOverrideMs != null ? advisedReconnectDelayMs(reconnectDelayOverrideMs) : reconnectDelayMs();
        reconnectDelayOverrideMs = null;
        executor.schedule(this::connectOnce, delayMs, TimeUnit.MILLISECONDS);
    }

    private long reconnectDelayMs() {
        int attempt = Math.min(reconnectAttempt++, 5);
        long baseDelay = Math.min(MAX_RECONNECT_DELAY_MS, INITIAL_RECONNECT_DELAY_MS << attempt);
        long jitter = random.nextInt((int) Math.min(baseDelay, 1000L) + 1);
        return baseDelay + jitter;
    }

    private long advisedReconnectDelayMs(long retryAfterMs) {
        long cappedDelay = capAdvisedReconnectDelayMs(retryAfterMs);
        long jitterBound = Math.max(1L, Math.min(cappedDelay, 1000L));
        return cappedDelay + random.nextInt((int) jitterBound + 1);
    }

    static long capAdvisedReconnectDelayMs(long retryAfterMs) {
        return Math.max(0L, Math.min(retryAfterMs, MAX_ADVISED_RECONNECT_DELAY_MS));
    }

    static boolean shouldReconnectAfterClose(int code) {
        return code != 1008 && code != 1013;
    }

    static boolean isTerminalBackendError(ErrorCode code) {
        return code == ErrorCode.ERROR_CODE_UNAUTHORIZED
            || code == ErrorCode.ERROR_CODE_FORBIDDEN
            || code == ErrorCode.ERROR_CODE_UNSUPPORTED_PROTOCOL;
    }

    private RealtimeEnvelope buildClientHello() {
        ClientHello.Builder hello = ClientHello.newBuilder()
            .setClientKind(ClientKind.CLIENT_KIND_MINECRAFT_PLUGIN)
            .setProtocolVersion(protocolVersion)
            .addAllSupportedTopics(topics);
        if (serverName != null && !serverName.trim().isEmpty()) {
            hello.setServerName(serverName);
        }
        if (serverInstanceId != null) {
            hello.setServerInstanceId(serverInstanceId);
        }
        return baseEnvelope().setClientHello(hello).build();
    }

    private RealtimeEnvelope buildSubscribe() {
        return baseEnvelope()
            .setSubscribe(Subscribe.newBuilder().addAllTopics(topics))
            .build();
    }

    private RealtimeEnvelope buildTransportAck(String eventId) {
        return baseEnvelope()
            .setAck(Ack.newBuilder()
                .setKind(AckKind.ACK_KIND_TRANSPORT)
                .setEventId(eventId))
            .build();
    }

    private RealtimeEnvelope heartbeat(long sequence) {
        return baseEnvelope()
            .setHeartbeat(Heartbeat.newBuilder().setSequence(sequence))
            .build();
    }

    private void sendHeartbeat(WebSocketClient target) {
        send(target, heartbeat(heartbeatSequence.incrementAndGet()));
    }

    private RealtimeEnvelope.Builder baseEnvelope() {
        return RealtimeEnvelope.newBuilder().setProtocolVersion(protocolVersion);
    }

    private void send(WebSocketClient target, RealtimeEnvelope envelope) {
        if (!running || target == null || !target.isOpen()) return;
        try {
            target.send(envelope.toByteArray());
        } catch (Exception e) {
            logger.warning("[Realtime] Failed to send WebSocket frame: " + e.getMessage());
        }
    }

    private static List<Topic> parseTopics(List<String> topicNames, String serverInstanceId) {
        List<Topic> parsed = new ArrayList<>();
        if (topicNames == null) return parsed;

        for (String topicName : topicNames) {
            if (topicName == null || topicName.trim().isEmpty()) continue;
            try {
                Topic topic = Topic.valueOf(topicName.trim());
                if (ALLOWED_STARTUP_TOPICS.contains(topic)) parsed.add(topic);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parsed;
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static Set<Topic> allowedStartupTopics() {
        Set<Topic> topics = new HashSet<>();
        topics.add(Topic.TOPIC_MINECRAFT_PERMISSIONS);
        topics.add(Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES);
        return topics;
    }
}
