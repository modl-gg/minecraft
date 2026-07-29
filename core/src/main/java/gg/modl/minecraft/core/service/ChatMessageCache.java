package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.util.StringUtil;
import lombok.Value;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

public class ChatMessageCache {
    private static final DateTimeFormatter REPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final int DEFAULT_MAX_MESSAGES = 100, REPORT_LOOKBACK_MESSAGES = 10, REPORT_FALLBACK_SECONDS = 120;
    private static final int REPORT_WINDOW_SECONDS = 120;
    private static final long DEFAULT_MAX_AGE_MS = 600_000, CLEANUP_INTERVAL_MS = 30_000;

    private final Map<String, LinkedBlockingDeque<ChatMessage>> serverMessages = new ConcurrentHashMap<>();
    private final Map<String, String> playerToServer = new ConcurrentHashMap<>();
    private final int maxMessagesPerServer;
    private final long maxMessageAge;
    private volatile long lastCleanupTime = 0;

    public ChatMessageCache() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_AGE_MS);
    }

    public ChatMessageCache(int maxMessagesPerServer, long maxMessageAge) {
        if (maxMessagesPerServer < 1) {
            throw new IllegalArgumentException(
                    "maxMessagesPerServer must be at least 1 but was " + maxMessagesPerServer);
        }
        this.maxMessagesPerServer = maxMessagesPerServer;
        this.maxMessageAge = maxMessageAge;
    }

    public void addMessage(String serverName, String playerUuid, String playerName, String message) {
        playerToServer.put(playerUuid, serverName);
        if (StringUtil.isBlank(message)) return;
        LinkedBlockingDeque<ChatMessage> queue =
                serverMessages.computeIfAbsent(serverName, k -> new LinkedBlockingDeque<>(maxMessagesPerServer));
        ChatMessage entry = new ChatMessage(playerUuid, playerName, message, serverName, Instant.now());
        while (!queue.offerLast(entry)) queue.pollFirst();

        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            serverMessages.values().forEach(this::cleanupOldMessages);
        }
    }

    public String getChatLogForReport(String reportedPlayerUuid) {
        List<ChatMessage> allMessages = collectMessagesForReportedPlayer(reportedPlayerUuid);
        if (allMessages.isEmpty()) return "";

        Instant startTimestamp = determineReportStartTimestamp(allMessages, reportedPlayerUuid);

        List<ChatMessage> relevantMessages = allMessages.stream()
                .filter(msg -> !msg.getTimestamp().isBefore(startTimestamp))
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .collect(Collectors.toList());

        if (relevantMessages.isEmpty()) return "";

        StringBuilder chatLog = new StringBuilder();
        for (ChatMessage msg : relevantMessages) {
            chatLog.append(REPORT_TIME_FORMAT.format(msg.getTimestamp()))
                   .append(" ").append(msg.getPlayerName())
                   .append(": ").append(msg.getMessage())
                   .append("\n");
        }
        return chatLog.toString().trim();
    }

    public void updatePlayerServer(String serverName, String playerUuid) {
        playerToServer.put(playerUuid, serverName);
    }

    public void removePlayer(String playerUuid) {
        playerToServer.remove(playerUuid);
    }

    private List<ChatMessage> collectMessagesForReportedPlayer(String reportedPlayerUuid) {
        List<ChatMessage> allMessages = new ArrayList<>();
        for (LinkedBlockingDeque<ChatMessage> queue : serverMessages.values()) {
            boolean containsReported = queue.stream()
                    .anyMatch(msg -> msg.getPlayerUuid().equals(reportedPlayerUuid));
            if (!containsReported) continue;

            cleanupOldMessages(queue);
            allMessages.addAll(queue);
        }
        return allMessages;
    }

    private Instant determineReportStartTimestamp(List<ChatMessage> allMessages, String reportedPlayerUuid) {
        List<ChatMessage> reportedMessages = allMessages.stream()
                .filter(msg -> msg.getPlayerUuid().equals(reportedPlayerUuid))
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .collect(Collectors.toList());

        if (reportedMessages.isEmpty()) {
            return Instant.now().minusSeconds(REPORT_FALLBACK_SECONDS);
        }

        Instant lastReported = reportedMessages.get(reportedMessages.size() - 1).getTimestamp();
        Instant timeFloor = lastReported.minusSeconds(REPORT_WINDOW_SECONDS);

        int startIndex = Math.max(0, reportedMessages.size() - REPORT_LOOKBACK_MESSAGES);
        Instant countFloor = reportedMessages.get(startIndex).getTimestamp();

        return laterOf(timeFloor, countFloor);
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private void cleanupOldMessages(LinkedBlockingDeque<ChatMessage> queue) {
        Instant cutoff = Instant.now().minusMillis(maxMessageAge);
        queue.removeIf(message -> message.getTimestamp().isBefore(cutoff));
    }

    @Value
    public static class ChatMessage {
        String playerUuid, playerName, message, serverName;
        Instant timestamp;
    }
}
