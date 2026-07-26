package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.http.ChatLogEntry;
import gg.modl.minecraft.api.http.CommandLogEntry;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;

public class ChatCommandLogService {
    private static final int MAX_CONTENT_LENGTH = 512;

    private final List<ChatLogEntry> chatBuffer = new ArrayList<>();
    private final List<CommandLogEntry> commandBuffer = new ArrayList<>();

    public void addChatMessage(String uuid, String username, String message, String server) {
        if (StringUtil.isBlank(message)) return;
        synchronized (chatBuffer) {
            chatBuffer.add(new ChatLogEntry(uuid, username, clampToContractLength(message), server, System.currentTimeMillis()));
        }
    }

    public void addCommand(String uuid, String username, String command, String server) {
        if (StringUtil.isBlank(command)) return;
        synchronized (commandBuffer) {
            commandBuffer.add(new CommandLogEntry(uuid, username, clampToContractLength(command), server, System.currentTimeMillis()));
        }
    }

    private static String clampToContractLength(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) return content;
        int end = Character.isHighSurrogate(content.charAt(MAX_CONTENT_LENGTH - 1))
                ? MAX_CONTENT_LENGTH - 1 : MAX_CONTENT_LENGTH;
        return content.substring(0, end);
    }

    public List<ChatLogEntry> drainChatBuffer() {
        return drain(chatBuffer);
    }

    public List<CommandLogEntry> drainCommandBuffer() {
        return drain(commandBuffer);
    }

    private static <T> List<T> drain(List<T> buffer) {
        synchronized (buffer) {
            if (buffer.isEmpty()) return listOf();
            List<T> drained = new ArrayList<>(buffer);
            buffer.clear();
            return drained;
        }
    }

    public CompletableFuture<List<ChatLogEntry>> getChatLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getChatLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return listOf();
            return new ArrayList<>(response.getEntries());
        });
    }

    public CompletableFuture<List<CommandLogEntry>> getCommandLogs(HttpClientHolder httpClientHolder, String uuid, int limit) {
        return httpClientHolder.getClient().getCommandLogs(uuid, limit).thenApply(response -> {
            if (response.getEntries() == null) return listOf();
            return new ArrayList<>(response.getEntries());
        });
    }
}
