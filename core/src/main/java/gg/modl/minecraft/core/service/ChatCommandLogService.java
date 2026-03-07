package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Buffers chat messages and commands for submission each sync cycle.
 */
public class ChatCommandLogService {
    private final List<ChatLogEntry> chatBuffer = new ArrayList<>();
    private final List<CommandLogEntry> commandBuffer = new ArrayList<>();

    public void addChatMessage(String uuid, String username, String message, String server) {
        synchronized (chatBuffer) {
            chatBuffer.add(new ChatLogEntry(uuid, username, message, server, System.currentTimeMillis()));
        }
    }

    public void addCommand(String uuid, String username, String command, String server) {
        synchronized (commandBuffer) {
            commandBuffer.add(new CommandLogEntry(uuid, username, command, server, System.currentTimeMillis()));
        }
    }

    public List<SyncRequest.ChatLogEntry> drainChatBuffer() {
        List<ChatLogEntry> entries;
        synchronized (chatBuffer) {
            if (chatBuffer.isEmpty()) return List.of();
            entries = new ArrayList<>(chatBuffer);
            chatBuffer.clear();
        }
        return entries.stream()
                .map(e -> new SyncRequest.ChatLogEntry(e.getUuid(), e.getUsername(), e.getMessage(), e.getServer(), e.getTimestamp()))
                .collect(Collectors.toList());
    }

    public List<SyncRequest.CommandLogEntry> drainCommandBuffer() {
        List<CommandLogEntry> entries;
        synchronized (commandBuffer) {
            if (commandBuffer.isEmpty()) return List.of();
            entries = new ArrayList<>(commandBuffer);
            commandBuffer.clear();
        }
        return entries.stream()
                .map(e -> new SyncRequest.CommandLogEntry(e.getUuid(), e.getUsername(), e.getCommand(), e.getServer(), e.getTimestamp()))
                .collect(Collectors.toList());
    }

    public CompletableFuture<List<ChatLogEntry>> getChatLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getChatLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return List.of();
            return response.getEntries().stream()
                    .map(e -> new ChatLogEntry(e.getUuid(), e.getUsername(), e.getMessage(), e.getServer(), e.getTimestamp()))
                    .collect(Collectors.toList());
        });
    }

    public CompletableFuture<List<CommandLogEntry>> getCommandLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getCommandLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return List.of();
            return response.getEntries().stream()
                    .map(e -> new CommandLogEntry(e.getUuid(), e.getUsername(), e.getCommand(), e.getServer(), e.getTimestamp()))
                    .collect(Collectors.toList());
        });
    }

    @Data @AllArgsConstructor
    public static class ChatLogEntry {
        private final String uuid, username, message, server;
        private final long timestamp;
    }

    @Data @AllArgsConstructor
    public static class CommandLogEntry {
        private final String uuid, username, command, server;
        private final long timestamp;
    }
}
