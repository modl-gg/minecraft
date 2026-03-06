package gg.modl.minecraft.core.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ChatMessageCache {
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final long DEFAULT_MAX_AGE_MS = 600_000; // 10 minutes
    private static final long CLEANUP_INTERVAL_MS = 30_000;
    private static final int REPORT_LOOKBACK_MESSAGES = 10;
    private static final int REPORT_FALLBACK_SECONDS = 120;
    private static final String JSON_FORMAT = "{\"username\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}";
    private static final DateTimeFormatter REPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final int maxMessagesPerServer;
    private final long maxMessageAge;
    private final Map<String, ConcurrentLinkedQueue<ChatMessage>> serverMessages = new ConcurrentHashMap<>();
    private final Map<String, String> playerToServer = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = 0;

    public ChatMessageCache() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_AGE_MS);
    }

    public void addMessage(String serverName, String playerUuid, String playerName, String message) {
        playerToServer.put(playerUuid, serverName);
        ConcurrentLinkedQueue<ChatMessage> queue = serverMessages.computeIfAbsent(serverName, k -> new ConcurrentLinkedQueue<>());
        queue.offer(new ChatMessage(playerUuid, playerName, message, Instant.now(), serverName));

        while (queue.size() > maxMessagesPerServer) {
            queue.poll();
        }

        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            serverMessages.values().forEach(this::cleanupOldMessages);
        }
    }

    /**
     * Returns chat context for a report: all messages on the server from the
     * reported player's 10th-last message onward (or last 2 minutes if no messages).
     */
    public String getChatLogForReport(String reportedPlayerUuid, String reporterUuid) {
        String serverName = resolveServerName(reporterUuid, reportedPlayerUuid);
        if (serverName == null) return "";

        ConcurrentLinkedQueue<ChatMessage> queue = serverMessages.get(serverName);
        if (queue == null || queue.isEmpty()) return "";
        cleanupOldMessages(queue);

        List<ChatMessage> allMessages = new ArrayList<>(queue);
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

    private String resolveServerName(String primaryUuid, String fallbackUuid) {
        String server = playerToServer.get(primaryUuid);
        if (server == null) server = playerToServer.get(fallbackUuid);
        return server;
    }

    private Instant determineReportStartTimestamp(List<ChatMessage> allMessages, String reportedPlayerUuid) {
        List<ChatMessage> reportedMessages = allMessages.stream()
                .filter(msg -> msg.getPlayerUuid().equals(reportedPlayerUuid))
                .collect(Collectors.toList());

        if (reportedMessages.isEmpty()) {
            return Instant.now().minusSeconds(REPORT_FALLBACK_SECONDS);
        }
        int startIndex = Math.max(0, reportedMessages.size() - REPORT_LOOKBACK_MESSAGES);
        return reportedMessages.get(startIndex).getTimestamp();
    }

    private void cleanupOldMessages(ConcurrentLinkedQueue<ChatMessage> queue) {
        Instant cutoff = Instant.now().minusMillis(maxMessageAge);
        queue.removeIf(message -> message.getTimestamp().isBefore(cutoff));
    }

    @Data
    public static class ChatMessage {
        private final String playerUuid;
        private final String playerName;
        private final String message;
        private final Instant timestamp;
        private final String serverName;
    }
}