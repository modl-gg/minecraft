package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.ChatLogsResponse;
import gg.modl.minecraft.api.http.response.CommandLogsResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service that buffers chat messages and commands for submission
 * as part of the sync request. Buffers are drained each sync cycle.
 */
public class ChatCommandLogService {

    private final List<ChatLogEntry> chatBuffer = Collections.synchronizedList(new ArrayList<>());
    private final List<CommandLogEntry> commandBuffer = Collections.synchronizedList(new ArrayList<>());

    /**
     * Add a chat message to the buffer.
     */
    public void addChatMessage(String uuid, String username, String message, String server) {
        chatBuffer.add(new ChatLogEntry(uuid, username, message, System.currentTimeMillis(), server));
    }

    /**
     * Add a command to the buffer.
     */
    public void addCommand(String uuid, String username, String command, String server) {
        commandBuffer.add(new CommandLogEntry(uuid, username, command, System.currentTimeMillis(), server));
    }

    /**
     * Drain the chat buffer and return entries for inclusion in a sync request.
     */
    public List<SyncRequest.ChatLogEntry> drainChatBuffer() {
        List<ChatLogEntry> entries;
        synchronized (chatBuffer) {
            if (chatBuffer.isEmpty()) return List.of();
            entries = new ArrayList<>(chatBuffer);
            chatBuffer.clear();
        }
        return entries.stream()
                .map(e -> new SyncRequest.ChatLogEntry(e.getUuid(), e.getUsername(), e.getMessage(), e.getTimestamp(), e.getServer()))
                .collect(Collectors.toList());
    }

    /**
     * Drain the command buffer and return entries for inclusion in a sync request.
     */
    public List<SyncRequest.CommandLogEntry> drainCommandBuffer() {
        List<CommandLogEntry> entries;
        synchronized (commandBuffer) {
            if (commandBuffer.isEmpty()) return List.of();
            entries = new ArrayList<>(commandBuffer);
            commandBuffer.clear();
        }
        return entries.stream()
                .map(e -> new SyncRequest.CommandLogEntry(e.getUuid(), e.getUsername(), e.getCommand(), e.getTimestamp(), e.getServer()))
                .collect(Collectors.toList());
    }

    /**
     * Fetch chat logs for a player from the backend.
     */
    public CompletableFuture<List<ChatLogEntry>> getChatLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getChatLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return List.of();
            return response.getEntries().stream()
                    .map(e -> new ChatLogEntry(e.getUuid(), e.getUsername(), e.getMessage(), e.getTimestamp(), e.getServer()))
                    .collect(Collectors.toList());
        });
    }

    /**
     * Fetch command logs for a player from the backend.
     */
    public CompletableFuture<List<CommandLogEntry>> getCommandLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getCommandLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return List.of();
            return response.getEntries().stream()
                    .map(e -> new CommandLogEntry(e.getUuid(), e.getUsername(), e.getCommand(), e.getTimestamp(), e.getServer()))
                    .collect(Collectors.toList());
        });
    }

    @Data
    @AllArgsConstructor
    public static class ChatLogEntry {
        private final String uuid;
        private final String username;
        private final String message;
        private final long timestamp;
        private final String server;
    }

    @Data
    @AllArgsConstructor
    public static class CommandLogEntry {
        private final String uuid;
        private final String username;
        private final String command;
        private final long timestamp;
        private final String server;
    }
}
