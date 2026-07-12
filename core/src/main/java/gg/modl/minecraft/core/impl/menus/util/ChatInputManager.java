package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.core.Platform;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager {
    private static final long INPUT_EXPIRY_MS = 60_000;
    private static final String PROMPT_PREFIX = "§6§l» §e";
    private static final String TIMEOUT_MESSAGE = "§cInput prompt timed out. Please try again.";

    private final Platform platform;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public ChatInputManager(Platform platform) {
        this.platform = platform;
    }

    @Getter @RequiredArgsConstructor
    public static class PendingInput {
        private final String prompt;
        private final Consumer<String> callback;
        private final Runnable cancelCallback;
        private final long timestamp = System.currentTimeMillis();

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > INPUT_EXPIRY_MS;
        }
    }

    public void requestInput(UUID playerUuid, String prompt, Consumer<String> callback, Runnable cancelCallback) {
        cancelInput(playerUuid);
        pendingInputs.put(playerUuid, new PendingInput(prompt, callback, cancelCallback));
        platform.sendMessage(playerUuid, "");
        platform.sendMessage(playerUuid, PROMPT_PREFIX + prompt);
        platform.sendMessage(playerUuid, "§7Type your response in chat, or type §ccancel §7to cancel.");
        platform.sendMessage(playerUuid, "");
    }

    public boolean handleChat(UUID playerUuid, String message) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending == null) return false;
        if (pending.isExpired()) {
            cancelPending(playerUuid, pending);
            return true;
        }

        if (message.equalsIgnoreCase("cancel")) {
            if (pending.getCancelCallback() != null) pending.getCancelCallback().run();
            return true;
        }

        pending.getCallback().accept(message);
        return true;
    }

    private void cancelPending(UUID playerUuid, PendingInput pending) {
        platform.sendMessage(playerUuid, TIMEOUT_MESSAGE);
        if (pending.getCancelCallback() != null) {
            pending.getCancelCallback().run();
        }
    }

    public void cancelInput(UUID playerUuid) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending != null && pending.getCancelCallback() != null) {
            pending.getCancelCallback().run();
        }
    }

    public void clearOnDisconnect(UUID playerUuid) {
        pendingInputs.remove(playerUuid);
    }

    public void cleanupExpired() {
        List<Map.Entry<UUID, PendingInput>> expired = new ArrayList<>();
        pendingInputs.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                expired.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
                return true;
            }
            return false;
        });
        for (Map.Entry<UUID, PendingInput> e : expired) {
            platform.runOnMainThread(() -> cancelPending(e.getKey(), e.getValue()));
        }
    }
}
