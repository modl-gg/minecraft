package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import static gg.modl.minecraft.core.util.Java8Collections.orTimeout;

class NotificationService {
    private static final String TICKET_CREATED_TYPE = "TICKET_CREATED", TICKET_TYPE_REPORT = "REPORT";
    private static final int MAX_HOVER_TEXT_LENGTH = 200;
    private static final long NOTIFICATION_INITIAL_DELAY_MS = 2000, NOTIFICATION_INTER_DELAY_MS = 1500,
            HTTP_TIMEOUT_SECONDS = 5;

    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final PluginLogger logger;
    private final LocaleManager localeManager;
    private final String panelUrl;
    private final boolean debugMode;

    @Setter private volatile ScheduledExecutorService executor;

    NotificationService(Platform platform, HttpClientHolder httpClientHolder, Cache cache,
                        PluginLogger logger, LocaleManager localeManager, String panelUrl, boolean debugMode) {
        this.platform = platform;
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.logger = logger;
        this.localeManager = localeManager;
        this.panelUrl = panelUrl;
        this.debugMode = debugMode;
    }

    void processStaffNotification(SyncResponse.StaffNotification notification) {
        try {
            if (TICKET_CREATED_TYPE.equals(notification.getType()) && notification.getData() != null) {
                processTicketCreatedNotification(notification);
            } else {
                platform.staffBroadcast("&7&o[" + notification.getMessage() + "&7&o]");
            }
            if (debugMode) logger.info("Processed staff notification: " + notification.getMessage());
        } catch (Exception e) {
            logger.warning("Error processing staff notification: " + e.getMessage());
        }
    }

    private void processTicketCreatedNotification(SyncResponse.StaffNotification notification) {
        Map<String, Object> data = notification.getData();
        String ticketId = extractString(data, "ticketId");
        String rawTicketUrl = extractString(data, "ticketUrl");
        String ticketUrl = ticketId.isEmpty()
                ? (rawTicketUrl.startsWith("http") ? rawTicketUrl : panelUrl + rawTicketUrl)
                : panelUrl + "/panel/tickets/" + ticketId;
        String subject = extractString(data, "subject");
        String firstReply = extractString(data, "firstReplyContent");
        String ticketType = extractString(data, "ticketType");
        String category = extractString(data, "category");
        String reportedPlayer = extractString(data, "reportedPlayer");

        String hoverText = buildTicketHoverText(subject, firstReply);
        boolean isGameplayReport = TICKET_TYPE_REPORT.equalsIgnoreCase(ticketType)
                && !category.toLowerCase().contains("chat")
                && !reportedPlayer.isEmpty();

        String clickAction;
        String clickValue;
        if (isGameplayReport) {
            clickAction = "run_command";
            clickValue = "/target " + escapeJson(reportedPlayer);
            hoverText += (hoverText.isEmpty() ? "" : "\n\n") + "Click to target " + reportedPlayer;
        } else {
            clickAction = "open_url";
            clickValue = ticketUrl;
        }

        String json = String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"[%s]\",\"color\":\"gray\",\"italic\":true," +
            "\"clickEvent\":{\"action\":\"%s\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"%s\"}}]}",
            escapeJson(notification.getMessage()), clickAction, clickValue, escapeJson(hoverText)
        );
        platform.staffJsonBroadcast(json);
    }

    void processPlayerNotification(SyncResponse.PlayerNotification notification) {
        try {
            if (debugMode)
                logger.info("Processing notification " + notification.getId() + " (type: " + notification.getType() + "): " + notification.getMessage());

            String targetPlayerUuid = notification.getTargetPlayerUuid();
            if (targetPlayerUuid == null || targetPlayerUuid.isEmpty()) {
                handleNotificationForAllOnlinePlayers(notification);
                return;
            }

            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(targetPlayerUuid);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid UUID format in notification: " + targetPlayerUuid);
                return;
            }

            if (deliverNotificationToPlayerAndCheck(playerUuid, notification)) {
                acknowledgeNotification(playerUuid, notification.getId());
            }
        } catch (Exception e) {
            logger.severe("Error processing player notification: " + e.getMessage());
        }
    }

    private boolean deliverNotificationToPlayerAndCheck(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        AbstractPlayer player = platform.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            if (debugMode) logger.info("Player " + playerUuid + " is offline, notification " + notification.getId() + " deferred");
            return false;
        }

        Map<String, Object> data = notification.getData();
        if (data != null && data.containsKey("ticketUrl")) {
            sendClickableTicketNotification(playerUuid, notification, data);
        } else {
            String message = localeManager.getMessage(
                "notification.ticket_reply", mapOf("message", notification.getMessage()));

            platform.runOnMainThread(() -> platform.sendMessage(playerUuid, message));
        }

        if (debugMode) logger.info("Delivered notification " + notification.getId() + " to " + player.getName());
        return true;
    }

    private void sendClickableTicketNotification(UUID playerUuid, SyncResponse.PlayerNotification notification, Map<String, Object> data) {
        String ticketId = extractString(data, "ticketId");
        String rawTicketUrl = extractString(data, "ticketUrl");
        String ticketUrl = ticketId.isEmpty()
                ? (rawTicketUrl.startsWith("http") ? rawTicketUrl : panelUrl + rawTicketUrl)
                : panelUrl + "/panel/tickets/" + ticketId;
        String json = buildClickableTicketJson(notification.getMessage(), ticketUrl, ticketId);
        if (debugMode) logger.info("Sending clickable notification JSON: " + json);
        platform.runOnMainThread(() -> platform.sendJsonMessage(playerUuid, json));
    }

    private void handleNotificationForAllOnlinePlayers(SyncResponse.PlayerNotification notification) {
        for (AbstractPlayer player : platform.getOnlinePlayers()) {
            try {
                UUID playerUuid = player.getUuid();
                CachedProfile profile = cache.getPlayerProfile(playerUuid);
                if (profile != null) profile.addNotification(notification);
                deliverNotificationToPlayerAndCheck(playerUuid, notification);
            } catch (Exception e) {
                logger.warning("Error handling notification for player " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    void deliverPendingNotifications(UUID playerUuid) {
        try {
            CachedProfile profile = cache.getPlayerProfile(playerUuid);
            if (profile == null) return;

            List<CachedProfile.PendingNotification> pending = profile.getPendingNotifications();
            pending.removeIf(CachedProfile.PendingNotification::isExpired);
            if (pending.isEmpty()) return;

            List<CachedProfile.PendingNotification> toProcess = new ArrayList<>(pending);
            if (debugMode) logger.info("Delivering " + toProcess.size() + " pending notifications to " + playerUuid);

            List<String> deliveredIds = new ArrayList<>();
            List<String> expiredIds = new ArrayList<>();
            deliverNotificationsWithDelay(playerUuid, toProcess, deliveredIds, expiredIds);

            for (String id : expiredIds) profile.removeNotification(id);
            for (String id : deliveredIds) profile.removeNotification(id);
            if (!deliveredIds.isEmpty()) acknowledgeNotifications(playerUuid, deliveredIds);
        } catch (Exception e) {
            logger.severe("Error delivering pending notifications: " + e.getMessage());
        }
    }

    private void deliverPendingNotificationToPlayer(UUID playerUuid, CachedProfile.PendingNotification pending) {
        AbstractPlayer player = platform.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;

        Map<String, Object> data = pending.getData();
        if (data != null && data.containsKey("ticketUrl")) {
            String ticketId = extractString(data, "ticketId");
            String rawTicketUrl = extractString(data, "ticketUrl");
            String ticketUrl = ticketId.isEmpty()
                    ? (rawTicketUrl.startsWith("http") ? rawTicketUrl : panelUrl + rawTicketUrl)
                    : panelUrl + "/panel/tickets/" + ticketId;
            String json = buildClickableTicketJson(pending.getMessage(), ticketUrl, ticketId);
            platform.runOnMainThread(() -> platform.sendJsonMessage(playerUuid, json));
        } else {
            String message = pending.getMessage();
            platform.runOnMainThread(() -> platform.sendMessage(playerUuid, message));
        }
    }

    private void deliverNotificationsWithDelay(UUID playerUuid, List<CachedProfile.PendingNotification> notifications,
                                             List<String> deliveredIds, List<String> expiredIds) {
        if (notifications.isEmpty() || executor == null) return;
        executor.schedule(() ->
            platform.runOnMainThread(() ->
                deliverNotificationAtIndex(playerUuid, notifications, 0, deliveredIds, expiredIds)),
            NOTIFICATION_INITIAL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void deliverNotificationAtIndex(UUID playerUuid, List<CachedProfile.PendingNotification> notifications,
                                          int index, List<String> deliveredIds, List<String> expiredIds) {
        if (index >= notifications.size()) {
            finalizePendingNotificationDelivery(playerUuid, deliveredIds, expiredIds);
            return;
        }

        CachedProfile.PendingNotification pending = notifications.get(index);
        try {
            if (pending.isExpired()) {
                expiredIds.add(pending.getId());
            } else {
                AbstractPlayer player = platform.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) {
                    if (debugMode) logger.info("Player " + playerUuid + " disconnected during notification delivery");
                    return;
                }
                deliverPendingNotificationToPlayer(playerUuid, pending);
                deliveredIds.add(pending.getId());
                if (debugMode) logger.info("Delivered pending notification " + pending.getId() + " to " + playerUuid);
            }
        } catch (Exception e) {
            logger.warning("Error delivering pending notification " + pending.getId() + ": " + e.getMessage());
        }

        executor.schedule(() ->
            platform.runOnMainThread(() ->
                deliverNotificationAtIndex(playerUuid, notifications, index + 1, deliveredIds, expiredIds)),
            NOTIFICATION_INTER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void finalizePendingNotificationDelivery(UUID playerUuid, List<String> deliveredIds, List<String> expiredIds) {
        try {
            CachedProfile profile = cache.getPlayerProfile(playerUuid);
            if (profile != null) {
                for (String id : expiredIds) profile.removeNotification(id);
                for (String id : deliveredIds) profile.removeNotification(id);
            }
            if (!deliveredIds.isEmpty()) acknowledgeNotifications(playerUuid, deliveredIds);
            if (debugMode) logger.info("Notification delivery complete for " + playerUuid + ". Delivered: " + deliveredIds.size() + ", Expired: " + expiredIds.size());
        } catch (Exception e) {
            logger.severe("Error finalizing pending notification delivery: " + e.getMessage());
        }
    }

    private void acknowledgeNotification(UUID playerUuid, String notificationId) {
        acknowledgeNotifications(playerUuid, listOf(notificationId));
    }

    private void acknowledgeNotifications(UUID playerUuid, List<String> notificationIds) {
        try {
            NotificationAcknowledgeRequest request = new NotificationAcknowledgeRequest(
                    playerUuid.toString(), Instant.now().toString(), notificationIds);

            orTimeout(httpClientHolder.getClient().acknowledgeNotifications(request)
                    .thenAccept(response -> {
                        if (debugMode) logger.info("Acknowledged " + notificationIds.size() + " notifications for " + playerUuid);
                    })
                    .exceptionally(throwable -> {
                        Throwable cause = throwable.getCause();
                        if (cause instanceof PanelUnavailableException) {
                            logger.warning("Failed to acknowledge notifications for " + playerUuid + ": Panel temporarily unavailable");
                        } else {
                            logger.warning("Failed to acknowledge notifications for " + playerUuid + ": " + throwable.getMessage());
                        }
                        return null;
                    }), HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.severe("Error acknowledging notifications: " + e.getMessage());
        }
    }

    private String buildClickableTicketJson(String message, String ticketUrl, String ticketId) {
        return String.format(
            "{\"text\":\"\",\"extra\":[" +
            "{\"text\":\"[Ticket] \",\"color\":\"gold\"}," +
            "{\"text\":\"%s \",\"color\":\"white\"}," +
            "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
            "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
            "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
            message.replace("\"", "\\\""), ticketUrl, ticketId
        );
    }

    private String buildTicketHoverText(String subject, String firstReply) {
        StringBuilder hover = new StringBuilder();
        if (!subject.isEmpty()) hover.append(subject);
        if (!firstReply.isEmpty()) {
            if (hover.length() > 0) hover.append("\n\n");
            hover.append(firstReply.length() > MAX_HOVER_TEXT_LENGTH
                    ? firstReply.substring(0, MAX_HOVER_TEXT_LENGTH) + "..."
                    : firstReply);
        }
        return hover.toString();
    }

    private static String extractString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val instanceof String ? (String) val : "";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "")
                   .replace("\t", "\\t");
    }
}
