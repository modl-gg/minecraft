package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.core.Platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager {
    private static final long INPUT_EXPIRY_MS = 60_000;
    private static final String PROMPT_PREFIX = "\u00a76\u00a7l\u00bb \u00a7e";

    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public static class PendingInput {
        private final String prompt;
        private final Consumer<String> callback;
        private final Runnable cancelCallback;
        private final long timestamp;

        public PendingInput(String prompt, Consumer<String> callback, Runnable cancelCallback) {
            this.prompt = prompt;
            this.callback = callback;
            this.cancelCallback = cancelCallback;
            this.timestamp = System.currentTimeMillis();
        }

        public String getPrompt() {
            return prompt;
        }

        public Consumer<String> getCallback() {
            return callback;
        }

        public Runnable getCancelCallback() {
            return cancelCallback;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > INPUT_EXPIRY_MS;
        }
    }

    public static void requestInput(Platform platform, UUID playerUuid, String prompt, Consumer<String> callback) {
        requestInput(platform, playerUuid, prompt, callback, null);
    }

    public static void requestInput(Platform platform, UUID playerUuid, String prompt, Consumer<String> callback, Runnable cancelCallback) {
        cancelInput(playerUuid);
        pendingInputs.put(playerUuid, new PendingInput(prompt, callback, cancelCallback));
        platform.sendMessage(playerUuid, "");
        platform.sendMessage(playerUuid, PROMPT_PREFIX + prompt);
        platform.sendMessage(playerUuid, "§7Type your response in chat, or type §ccancel §7to cancel.");
        platform.sendMessage(playerUuid, "");
    }

    /**
     * Returns true if the message was consumed as pending input.
     */
    public static boolean handleChat(UUID playerUuid, String message) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending == null) return false;
        if (pending.isExpired()) return false;

        if (message.equalsIgnoreCase("cancel")) {
            if (pending.getCancelCallback() != null) pending.getCancelCallback().run();
            return true;
        }

        pending.getCallback().accept(message);
        return true;
    }

    public static void cancelInput(UUID playerUuid) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending != null && pending.getCancelCallback() != null) {
            pending.getCancelCallback().run();
        }
    }

    /** Unlike cancelInput, does NOT fire the cancel callback (player is offline). */
    public static void clearOnDisconnect(UUID playerUuid) {
        pendingInputs.remove(playerUuid);
    }

    public static boolean hasPendingInput(UUID playerUuid) {
        PendingInput pending = pendingInputs.get(playerUuid);
        if (pending != null && pending.isExpired()) {
            pendingInputs.remove(playerUuid);
            return false;
        }
        return pending != null;
    }

    public static void cleanupExpired() {
        pendingInputs.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
