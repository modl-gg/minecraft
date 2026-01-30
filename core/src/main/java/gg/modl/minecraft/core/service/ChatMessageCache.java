package gg.modl.minecraft.core.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ChatMessageCache {
    
    private final int maxMessagesPerServer;
    private final long maxMessageAge;
    
    // Server -> Queue of chat messages
    private final Map<String, ConcurrentLinkedQueue<ChatMessage>> serverMessages = new ConcurrentHashMap<>();
    
    // Player -> Server mapping (for cross-platform setups)
    private final Map<String, String> playerToServer = new ConcurrentHashMap<>();
    
    public ChatMessageCache() {
        this(100, 600_000); // Default: 100 messages per server, 10 minutes TTL
    }
    
    /**
     * Add a chat message to the cache
     * 
     * @param serverName The server name (used for cross-platform setups)
     * @param playerUuid The player's UUID
     * @param playerName The player's name
     * @param message The chat message
     */
    public void addMessage(String serverName, String playerUuid, String playerName, String message) {
        // Update player-to-server mapping
        playerToServer.put(playerUuid, serverName);
        
        // Get or create message queue for this server
        ConcurrentLinkedQueue<ChatMessage> messageQueue = serverMessages.computeIfAbsent(serverName, k -> new ConcurrentLinkedQueue<>());
        
        // Add the new message
        ChatMessage chatMessage = new ChatMessage(
            playerUuid,
            playerName,
            message,
            Instant.now(),
            serverName
        );
        
        messageQueue.offer(chatMessage);
        
        // Remove oldest messages if we exceed the limit
        while (messageQueue.size() > maxMessagesPerServer) {
            messageQueue.poll();
        }
        
        // Clean up old messages
        cleanupOldMessages(messageQueue);
    }
    
    /**
     * Get the last N chat messages from the server where the player is currently located
     * 
     * @param playerUuid The player's UUID
     * @param count Number of messages to retrieve (default: 30)
     * @return List of formatted chat messages
     */
    public List<String> getRecentMessages(String playerUuid, int count) {
        String serverName = playerToServer.get(playerUuid);
        if (serverName == null) {
            return Collections.emptyList();
        }
        
        return getRecentMessagesFromServer(serverName, count);
    }
    
    /**
     * Get the last N chat messages from a specific server
     * 
     * @param serverName The server name
     * @param count Number of messages to retrieve
     * @return List of formatted chat messages
     */
    public List<String> getRecentMessagesFromServer(String serverName, int count) {
        ConcurrentLinkedQueue<ChatMessage> messageQueue = serverMessages.get(serverName);
        if (messageQueue == null || messageQueue.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Clean up old messages first
        cleanupOldMessages(messageQueue);
        
        // Convert to list and get the last N messages
        List<ChatMessage> allMessages = new ArrayList<>(messageQueue);
        
        // Take the last 'count' messages
        int startIndex = Math.max(0, allMessages.size() - count);
        List<ChatMessage> recentMessages = allMessages.subList(startIndex, allMessages.size());
        
        // Format messages for the API
        return recentMessages.stream()
                .map(this::formatMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * Get recent chat messages for a specific player (messages they sent)
     * 
     * @param playerUuid The player's UUID
     * @param count Number of messages to retrieve
     * @return List of formatted chat messages from that player
     */
    public List<String> getRecentMessagesFromPlayer(String playerUuid, int count) {
        String serverName = playerToServer.get(playerUuid);
        if (serverName == null) {
            return Collections.emptyList();
        }
        
        ConcurrentLinkedQueue<ChatMessage> messageQueue = serverMessages.get(serverName);
        if (messageQueue == null || messageQueue.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Clean up old messages first
        cleanupOldMessages(messageQueue);
        
        // Filter messages from the specific player and take the last N
        List<ChatMessage> playerMessages = messageQueue.stream()
                .filter(msg -> msg.getPlayerUuid().equals(playerUuid))
                .collect(Collectors.toList());
        
        // Take the last 'count' messages
        int startIndex = Math.max(0, playerMessages.size() - count);
        List<ChatMessage> recentMessages = playerMessages.subList(startIndex, playerMessages.size());
        
        return recentMessages.stream()
                .map(this::formatMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all chat messages from all players on the server between the reported player's
     * 10th last message and now. This is used for chat reports.
     *
     * @param reportedPlayerUuid The UUID of the player being reported
     * @param reporterUuid The UUID of the player making the report
     * @return Formatted chat log string with "{date} {player}: {message}" format, newline separated
     */
    public String getChatLogForReport(String reportedPlayerUuid, String reporterUuid) {
        String serverName = playerToServer.get(reporterUuid);
        if (serverName == null) {
            serverName = playerToServer.get(reportedPlayerUuid);
        }
        if (serverName == null) {
            return "";
        }

        ConcurrentLinkedQueue<ChatMessage> messageQueue = serverMessages.get(serverName);
        if (messageQueue == null || messageQueue.isEmpty()) {
            return "";
        }

        // Clean up old messages first
        cleanupOldMessages(messageQueue);

        List<ChatMessage> allMessages = new ArrayList<>(messageQueue);

        // Find the reported player's messages
        List<ChatMessage> reportedPlayerMessages = allMessages.stream()
                .filter(msg -> msg.getPlayerUuid().equals(reportedPlayerUuid))
                .collect(Collectors.toList());

        // Get the 10th last message timestamp (or earliest if less than 10)
        Instant startTimestamp;
        if (reportedPlayerMessages.isEmpty()) {
            // No messages from reported player, use last 2 minutes of all messages
            startTimestamp = Instant.now().minusSeconds(120);
        } else {
            int startIndex = Math.max(0, reportedPlayerMessages.size() - 10);
            startTimestamp = reportedPlayerMessages.get(startIndex).getTimestamp();
        }

        // Get all messages from all players between startTimestamp and now
        List<ChatMessage> relevantMessages = allMessages.stream()
                .filter(msg -> !msg.getTimestamp().isBefore(startTimestamp))
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .collect(Collectors.toList());

        if (relevantMessages.isEmpty()) {
            return "";
        }

        // Format as "{date} {player}: {message}"
        StringBuilder chatLog = new StringBuilder();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());

        for (ChatMessage msg : relevantMessages) {
            String time = formatter.format(msg.getTimestamp());
            chatLog.append(time)
                   .append(" ")
                   .append(msg.getPlayerName())
                   .append(": ")
                   .append(msg.getMessage())
                   .append("\n");
        }

        return chatLog.toString().trim();
    }

    /**
     * Update a player's server mapping without adding a message.
     * This should be called when players join or switch servers.
     *
     * @param serverName The server name
     * @param playerUuid The player's UUID
     */
    public void updatePlayerServer(String serverName, String playerUuid) {
        playerToServer.put(playerUuid, serverName);
    }

    /**
     * Remove a player from the cache when they disconnect
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(String playerUuid) {
        playerToServer.remove(playerUuid);
    }
    
    /**
     * Clear all cached messages for a server
     * 
     * @param serverName The server name
     */
    public void clearServer(String serverName) {
        serverMessages.remove(serverName);
    }
    
    /**
     * Clear all cached messages
     */
    public void clearAll() {
        serverMessages.clear();
        playerToServer.clear();
    }
    
    /**
     * Get the current size of the cache for a server
     * 
     * @param serverName The server name
     * @return Number of cached messages
     */
    public int getServerCacheSize(String serverName) {
        ConcurrentLinkedQueue<ChatMessage> messageQueue = serverMessages.get(serverName);
        return messageQueue != null ? messageQueue.size() : 0;
    }
    
    /**
     * Format a chat message for the API as stringified JSON
     * 
     * @param message The chat message
     * @return Formatted message string as JSON
     */
    private String formatMessage(ChatMessage message) {
        // Format as stringified JSON: {"username":"PlayerName","message":"Message","timestamp":"2024-07-26T10:00:00Z"}
        String escapedMessage = escapeJsonString(message.getMessage());
        String escapedPlayerName = escapeJsonString(message.getPlayerName());
        String timestamp = message.getTimestamp().toString(); // ISO-8601 format
        
        return String.format("{\"username\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}", 
            escapedPlayerName,
            escapedMessage,
            timestamp
        );
    }
    
    /**
     * Escape special characters in JSON strings
     * 
     * @param input The input string
     * @return Escaped string for JSON
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        
        return input
            .replace("\\", "\\\\")  // Escape backslashes
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\b", "\\b")   // Escape backspace
            .replace("\f", "\\f")   // Escape form feed
            .replace("\n", "\\n")   // Escape newline
            .replace("\r", "\\r")   // Escape carriage return
            .replace("\t", "\\t");  // Escape tab
    }
    
    /**
     * Clean up old messages from the queue
     * 
     * @param messageQueue The message queue to clean
     */
    private void cleanupOldMessages(ConcurrentLinkedQueue<ChatMessage> messageQueue) {
        Instant cutoff = Instant.now().minusMillis(maxMessageAge);
        
        // Remove messages older than the cutoff
        messageQueue.removeIf(message -> message.getTimestamp().isBefore(cutoff));
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