package gg.modl.minecraft.core.realtime;

import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.proto.modl.v1.Ack;
import gg.modl.proto.modl.v1.AckKind;
import gg.modl.proto.modl.v1.ActiveStaffPushEvent;
import gg.modl.proto.modl.v1.ClientHello;
import gg.modl.proto.modl.v1.ClientKind;
import gg.modl.proto.modl.v1.ErrorCode;
import gg.modl.proto.modl.v1.Heartbeat;
import gg.modl.proto.modl.v1.MigrationTaskPushEvent;
import gg.modl.proto.modl.v1.PlayerNotificationPushEvent;
import gg.modl.proto.modl.v1.PunishmentPushEvent;
import gg.modl.proto.modl.v1.RealtimeEnvelope;
import gg.modl.proto.modl.v1.ReconnectAction;
import gg.modl.proto.modl.v1.Staff2faPushEvent;
import gg.modl.proto.modl.v1.StaffNotificationPushEvent;
import gg.modl.proto.modl.v1.StatWipePushEvent;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.ByteBuffer;

public class MinecraftRealtimeClient {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 25;
    private static final long INBOUND_LIVENESS_TIMEOUT_MILLIS = HEARTBEAT_INTERVAL_SECONDS * 3 * 1000;
    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final long MAX_ADVISED_RECONNECT_DELAY_MS = 60000;
    private static final int RECENT_EVENT_ID_CAPACITY = 256;
    private static final Set<Topic> ALLOWED_STARTUP_TOPICS = allowedStartupTopics();

    private enum State { DISCONNECTED, CONNECTING, CONNECTED, TERMINAL }

    private final String apiKey;
    private final String realtimeUrl;
    private final int protocolVersion;
    private final List<Topic> topics;
    private final Platform platform;
    private final String serverInstanceId;
    private final SyncService syncService;
    private final PluginLogger logger;
    private final boolean debugMode;
    private final ScheduledExecutorService executor;
    private final AtomicLong heartbeatSequence = new AtomicLong(0);
    private final RecentRealtimeEventIds recentEventIds = new RecentRealtimeEventIds(RECENT_EVENT_ID_CAPACITY);
    private final Random random = new Random();

    private final Object stateLock = new Object();
    private volatile State state = State.DISCONNECTED;
    private boolean started;
    private WebSocketClient client;
    private ScheduledFuture<?> heartbeatTask;
    private boolean connectedOnce;
    private int reconnectAttempt;
    private boolean reconnectScheduled;
    private Long reconnectDelayOverrideMs;
    private volatile long lastInboundFrameMillis;

    public MinecraftRealtimeClient(String apiKey, StartupResponse startupResponse, Platform platform,
                                   SyncService syncService, PluginLogger logger, boolean debugMode) {
        this.apiKey = apiKey;
        this.realtimeUrl = startupResponse.getRealtimeUrl();
        this.protocolVersion = startupResponse.getRealtimeProtocolVersion() != null
            ? startupResponse.getRealtimeProtocolVersion()
            : 1;
        this.serverInstanceId = normalize(startupResponse.getServerInstanceId());
        this.topics = parseTopics(startupResponse.getRealtimeTopics(), serverInstanceId);
        this.platform = platform;
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
        synchronized (stateLock) {
            if (started || state != State.DISCONNECTED) return;
            started = true;
            state = State.CONNECTING;
        }
        executor.execute(this::connectOnce);
    }

    public void stop() {
        terminate();
        executor.shutdownNow();
    }

    private boolean isTerminated() {
        return state == State.TERMINAL;
    }

    private boolean isCurrent(WebSocketClient source) {
        synchronized (stateLock) {
            return source == client;
        }
    }

    private void connectOnce() {
        WebSocketClient nextClient;
        synchronized (stateLock) {
            if (state == State.TERMINAL) return;
            state = State.CONNECTING;
            reconnectScheduled = false;
        }

        try {
            URI uri = URI.create(realtimeUrl);
            Map<String, String> headers = new HashMap<>();
            headers.put(API_KEY_HEADER, apiKey);
            headers.put("User-Agent", "modl-minecraft");

            nextClient = new WebSocketClient(uri, headers) {
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
                    handleClose(this, code, reason);
                }

                @Override
                public void onError(Exception exception) {
                    handleError(this, exception);
                }
            };

            synchronized (stateLock) {
                if (state == State.TERMINAL) {
                    safeClose(nextClient);
                    return;
                }
                this.client = nextClient;
            }
            nextClient.connect();
        } catch (Exception e) {
            logger.warning("[Realtime] Failed to start WebSocket: " + e.getMessage());
            teardown();
            scheduleReconnect();
        }
    }

    private void handleOpen(WebSocketClient openedClient) {
        if (!isCurrent(openedClient)) return;
        if (debugMode) logger.info("[Realtime] WebSocket connected, sending ClientHello");
        send(openedClient, buildClientHello());
        synchronized (stateLock) {
            if (state == State.TERMINAL || openedClient != client) return;
            lastInboundFrameMillis = System.currentTimeMillis();
            cancelHeartbeatLocked();
            heartbeatTask = executor.scheduleAtFixedRate(() -> heartbeatTick(openedClient),
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void handleBinaryMessage(WebSocketClient source, ByteBuffer bytes) {
        if (!isCurrent(source)) return;
        lastInboundFrameMillis = System.currentTimeMillis();
        try {
            RealtimeEnvelope envelope = RealtimeEnvelope.parseFrom(bytes);
            if (!envelope.getEventId().isEmpty()) {
                send(source, buildTransportAck(envelope.getEventId()));
            }

            switch (envelope.getPayloadCase()) {
                case SERVER_HELLO:
                    onServerHello(source, envelope);
                    break;
                case HEARTBEAT:
                    if (debugMode) logger.info("[Realtime] Backend heartbeat received");
                    break;
                case RECONNECT_ADVICE:
                    handleReconnectAdvice(envelope);
                    break;
                case PERMISSION_INVALIDATED:
                    applyDomainEvent(envelope, () -> syncService.refreshStaffPermissionsNow());
                    break;
                case PUNISHMENT_TYPE_INVALIDATED:
                    applyDomainEvent(envelope, () -> syncService.refreshPunishmentTypesNow());
                    break;
                case PUNISHMENT_PUSH:
                    applyDomainEvent(envelope, () -> applyPunishmentPush(envelope.getPunishmentPush()));
                    break;
                case PLAYER_NOTIFICATION_PUSH:
                    applyDomainEvent(envelope, () -> applyPlayerNotificationPush(envelope.getPlayerNotificationPush()));
                    break;
                case STAFF_NOTIFICATION_PUSH:
                    applyDomainEvent(envelope, () -> applyStaffNotificationPush(envelope.getStaffNotificationPush()));
                    break;
                case STAT_WIPE_PUSH:
                    applyDomainEvent(envelope, () -> applyStatWipePush(envelope.getStatWipePush()));
                    break;
                case STAFF_2FA_PUSH:
                    applyDomainEvent(envelope, () -> applyStaff2faPush(envelope.getStaff2FaPush()));
                    break;
                case MIGRATION_TASK_PUSH:
                    applyDomainEvent(envelope, () -> applyMigrationTaskPush(envelope.getMigrationTaskPush()));
                    break;
                case ACTIVE_STAFF_PUSH:
                    applyDomainEvent(envelope, () -> applyActiveStaffPush(envelope.getActiveStaffPush()));
                    break;
                case PRESENCE_INVALIDATED:
                case PRESENCE_SNAPSHOT:
                case PRESENCE_DELTA:
                    if (debugMode) logger.info("[Realtime] Ignored advisory payload: " + envelope.getPayloadCase());
                    break;
                case ERROR:
                    logger.warning("[Realtime] Backend error: " + envelope.getError().getMessage());
                    if (isTerminalBackendError(envelope.getError().getCode())) {
                        stop();
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

    private void onServerHello(WebSocketClient source, RealtimeEnvelope envelope) {
        boolean firstConnect;
        synchronized (stateLock) {
            if (state == State.TERMINAL || source != client) return;
            state = State.CONNECTED;
            reconnectAttempt = 0;
            firstConnect = !connectedOnce;
            connectedOnce = true;
        }
        send(source, buildSubscribe());
        syncService.setRealtimeConnected(true);
        syncService.runBaselineFetch(firstConnect ? "realtime connect" : "realtime reconnect");
        if (debugMode) logger.info("[Realtime] ServerHello accepted " + envelope.getServerHello().getAcceptedTopicsCount() + " topics");
    }

    private void applyDomainEvent(RealtimeEnvelope envelope, Runnable apply) {
        String eventId = envelope.getEventId();
        boolean accepted = syncService.submitRealtimeApply(() -> {
            if (recentEventIds.contains(eventId)) {
                if (debugMode) logger.info("[Realtime] Suppressed duplicate event " + eventId);
                return;
            }
            try {
                apply.run();
                recentEventIds.markIfNew(eventId);
            } catch (Exception e) {
                logger.warning("[Realtime] Failed to apply " + envelope.getPayloadCase()
                    + " (reconciled on next baseline sync): " + e.getMessage());
            }
        });
        if (!accepted && !isTerminated() && debugMode) {
            logger.info("[Realtime] Dropped " + envelope.getPayloadCase() + "; sync service unavailable");
        }
    }

    private void applyPunishmentPush(PunishmentPushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromPunishmentPush(event);
        for (SyncResponse.ModifiedPunishment modified : data.getRecentlyModifiedPunishments()) {
            syncService.applyModifiedPunishment(modified);
        }
        for (SyncResponse.PendingPunishment pending : data.getPendingPunishments()) {
            syncService.applyPendingPunishment(pending);
        }
    }

    private void applyPlayerNotificationPush(PlayerNotificationPushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromPlayerNotificationPush(event);
        for (SyncResponse.PlayerNotification notification : data.getPlayerNotifications()) {
            String target = notification.getTargetPlayerUuid();
            if (target == null || target.isEmpty()) {
                if (debugMode) logger.info("[Realtime] Dropping player notification " + notification.getId() + " with empty target");
                continue;
            }
            syncService.applyPlayerNotification(notification);
        }
    }

    private void applyStaffNotificationPush(StaffNotificationPushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromStaffNotificationPush(event);
        for (SyncResponse.StaffNotification notification : data.getStaffNotifications()) {
            syncService.applyStaffNotification(notification);
        }
    }

    private void applyStatWipePush(StatWipePushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromStatWipePush(event);
        for (SyncResponse.PendingStatWipe statWipe : data.getPendingStatWipes()) {
            syncService.applyStatWipe(statWipe);
        }
    }

    private void applyStaff2faPush(Staff2faPushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromStaff2faPush(event);
        for (SyncResponse.Staff2faVerification verification : data.getStaff2faVerifications()) {
            syncService.applyStaff2faVerification(verification);
        }
    }

    private void applyMigrationTaskPush(MigrationTaskPushEvent event) {
        SyncResponse.MigrationTask task = RealtimeEventMappers.fromMigrationTaskPush(event);
        if (task != null) syncService.applyMigrationTask(task);
    }

    private void applyActiveStaffPush(ActiveStaffPushEvent event) {
        SyncResponse.SyncData data = RealtimeEventMappers.fromActiveStaffPush(event);
        for (SyncResponse.ActiveStaffMember staffMember : data.getActiveStaffMembers()) {
            syncService.applyActiveStaffMember(staffMember);
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
            synchronized (stateLock) {
                reconnectDelayOverrideMs = (long) envelope.getReconnectAdvice().getRetryAfterMs();
            }
        }
        if (envelope.getReconnectAdvice().getAction() == ReconnectAction.RECONNECT_ACTION_RESYNC) {
            syncService.runBaselineFetch("realtime resync advice");
        }
    }

    private void handleClose(WebSocketClient source, int code, String reason) {
        boolean reconnect = shouldReconnectAfterClose(code);
        boolean wasCurrent;
        synchronized (stateLock) {
            if (source != client || state == State.TERMINAL) {
                wasCurrent = false;
            } else {
                wasCurrent = true;
                cancelHeartbeatLocked();
                closeClientLocked();
                state = reconnect ? State.DISCONNECTED : State.TERMINAL;
            }
        }
        if (!wasCurrent) return;
        syncService.setRealtimeConnected(false);
        if (debugMode) logger.info("[Realtime] WebSocket closed (" + code + "): " + reason);
        if (reconnect) scheduleReconnect();
    }

    private void handleError(WebSocketClient source, Exception exception) {
        boolean wasCurrent;
        synchronized (stateLock) {
            if (source != client || state == State.TERMINAL) {
                wasCurrent = false;
            } else {
                wasCurrent = true;
                cancelHeartbeatLocked();
                closeClientLocked();
                state = State.DISCONNECTED;
            }
        }
        if (!wasCurrent) return;
        logger.warning("[Realtime] WebSocket error: " + exception.getMessage());
        syncService.setRealtimeConnected(false);
        scheduleReconnect();
    }

    private void heartbeatTick(WebSocketClient target) {
        if (!isCurrent(target)) return;
        if (System.currentTimeMillis() - lastInboundFrameMillis > INBOUND_LIVENESS_TIMEOUT_MILLIS) {
            logger.warning("[Realtime] No frames received within "
                + (INBOUND_LIVENESS_TIMEOUT_MILLIS / 1000) + "s; forcing reconnect");
            teardown();
            scheduleReconnect();
            return;
        }
        sendHeartbeat(target);
    }

    private void teardown() {
        synchronized (stateLock) {
            if (state == State.TERMINAL) return;
            cancelHeartbeatLocked();
            closeClientLocked();
            state = State.DISCONNECTED;
        }
        syncService.setRealtimeConnected(false);
    }

    private void terminate() {
        synchronized (stateLock) {
            state = State.TERMINAL;
            cancelHeartbeatLocked();
            closeClientLocked();
        }
        syncService.setRealtimeConnected(false);
    }

    private void cancelHeartbeatLocked() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void closeClientLocked() {
        if (client != null) {
            safeClose(client);
            client = null;
        }
    }

    private static void safeClose(WebSocketClient target) {
        try {
            target.close();
        } catch (Exception ignored) {
        }
    }

    private void scheduleReconnect() {
        long delayMs;
        synchronized (stateLock) {
            if (state == State.TERMINAL || reconnectScheduled) return;
            reconnectScheduled = true;
            delayMs = nextReconnectDelayLocked();
        }
        try {
            executor.schedule(this::connectOnce, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            synchronized (stateLock) {
                reconnectScheduled = false;
            }
            if (debugMode) logger.warning("[Realtime] Reconnect rejected: " + e.getMessage());
        }
    }

    private long nextReconnectDelayLocked() {
        if (reconnectDelayOverrideMs != null) {
            long override = reconnectDelayOverrideMs;
            reconnectDelayOverrideMs = null;
            return advisedReconnectDelayMs(override);
        }
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
        String serverName = platform.getServerName();
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
        if (isTerminated() || target == null || !target.isOpen()) return;
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
        topics.add(Topic.TOPIC_MINECRAFT_STAFF_NOTIFICATIONS);
        topics.add(Topic.TOPIC_MINECRAFT_PUNISHMENTS);
        topics.add(Topic.TOPIC_MINECRAFT_PLAYER_NOTIFICATIONS);
        topics.add(Topic.TOPIC_MINECRAFT_STAFF_2FA);
        topics.add(Topic.TOPIC_MINECRAFT_MIGRATION_TASKS);
        topics.add(Topic.TOPIC_MINECRAFT_STAT_WIPES);
        return topics;
    }
}
