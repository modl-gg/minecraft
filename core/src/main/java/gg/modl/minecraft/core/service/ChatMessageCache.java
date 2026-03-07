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
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class ChatMessageCache {
    private static final DateTimeFormatter REPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int DEFAULT_MAX_MESSAGES = 100, REPORT_LOOKBACK_MESSAGES = 10, REPORT_FALLBACK_SECONDS = 120;
    private static final long DEFAULT_MAX_AGE_MS = 600_000, CLEANUP_INTERVAL_MS = 30_000; // 10 minutes, 30 seconds

    private final Map<String, ConcurrentLinkedQueue<ChatMessage>> serverMessages = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> serverMessageCounts = new ConcurrentHashMap<>();
    private final Map<String, String> playerToServer = new ConcurrentHashMap<>();
    private final int maxMessagesPerServer;
    private final long maxMessageAge;
    private volatile long lastCleanupTime = 0;

    public ChatMessageCache() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_AGE_MS);
    }

    public void addMessage(String serverName, String playerUuid, String playerName, String message) {
        playerToServer.put(playerUuid, serverName);
        ConcurrentLinkedQueue<ChatMessage> queue = serverMessages.computeIfAbsent(serverName, k -> new ConcurrentLinkedQueue<>());
        AtomicInteger count = serverMessageCounts.computeIfAbsent(serverName, k -> new AtomicInteger(0));
        queue.offer(new ChatMessage(playerUuid, playerName, message, serverName, Instant.now()));
        count.incrementAndGet();

        while (count.get() > maxMessagesPerServer) {
            if (queue.poll() != null) {
                count.decrementAndGet();
            } else {
                break;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            serverMessages.forEach((name, q) -> {
                AtomicInteger c = serverMessageCounts.get(name);
                if (c != null) cleanupOldMessages(q, c);
            });
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
        AtomicInteger count = serverMessageCounts.get(serverName);
        if (count != null) cleanupOldMessages(queue, count);

        List<ChatMessage> allMessages = new ArrayList<>(queue);
        Instant startTimestamp = determineReportStartTimestamp(allMessages, reportedPlayerUuid);

        List<ChatMessage> relevantMessages = allMessages.stream()
                .filter(msg -> !msg.getTimestamp().isBefore(startTimestamp))
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .toList();

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
                .toList();

        if (reportedMessages.isEmpty()) {
            return Instant.now().minusSeconds(REPORT_FALLBACK_SECONDS);
        }
        int startIndex = Math.max(0, reportedMessages.size() - REPORT_LOOKBACK_MESSAGES);
        return reportedMessages.get(startIndex).getTimestamp();
    }

    private void cleanupOldMessages(ConcurrentLinkedQueue<ChatMessage> queue, AtomicInteger count) {
        Instant cutoff = Instant.now().minusMillis(maxMessageAge);
        queue.removeIf(message -> {
            if (message.getTimestamp().isBefore(cutoff)) {
                count.decrementAndGet();
                return true;
            }
            return false;
        });
    }

    @Data
    public static class ChatMessage {
        private final String playerUuid, playerName, message, serverName;
        private final Instant timestamp;
    }
}
