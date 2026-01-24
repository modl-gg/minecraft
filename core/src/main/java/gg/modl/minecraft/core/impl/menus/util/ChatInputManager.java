package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.core.Platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages chat input prompts for menu interactions.
 * When a player needs to enter text (e.g., note content, duration),
 * this class handles capturing their next chat message.
 */
public class ChatInputManager {

    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    /**
     * Represents a pending chat input from a player.
     */
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
            // Expire after 60 seconds
            return System.currentTimeMillis() - timestamp > 60000;
        }
    }

    /**
     * Request chat input from a player.
     *
     * @param platform The platform instance for sending messages
     * @param playerUuid The player's UUID
     * @param prompt The prompt message to show
     * @param callback Called with the player's input
     */
    public static void requestInput(Platform platform, UUID playerUuid, String prompt, Consumer<String> callback) {
        requestInput(platform, playerUuid, prompt, callback, null);
    }

    /**
     * Request chat input from a player with cancel callback.
     *
     * @param platform The platform instance for sending messages
     * @param playerUuid The player's UUID
     * @param prompt The prompt message to show
     * @param callback Called with the player's input
     * @param cancelCallback Called if input is cancelled
     */
    public static void requestInput(Platform platform, UUID playerUuid, String prompt, Consumer<String> callback, Runnable cancelCallback) {
        // Cancel any existing pending input
        cancelInput(playerUuid);

        // Store the new pending input
        pendingInputs.put(playerUuid, new PendingInput(prompt, callback, cancelCallback));

        // Send prompt to player
        platform.sendMessage(playerUuid, "");
        platform.sendMessage(playerUuid, "§6§l» §e" + prompt);
        platform.sendMessage(playerUuid, "§7Type your response in chat, or type §ccancel §7to cancel.");
        platform.sendMessage(playerUuid, "");
    }

    /**
     * Handle a chat message from a player.
     * Returns true if the message was consumed as input.
     *
     * @param playerUuid The player's UUID
     * @param message The chat message
     * @return true if the message was consumed, false otherwise
     */
    public static boolean handleChat(UUID playerUuid, String message) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending == null) {
            return false;
        }

        // Check if expired
        if (pending.isExpired()) {
            return false;
        }

        // Check for cancel
        if (message.equalsIgnoreCase("cancel")) {
            if (pending.getCancelCallback() != null) {
                pending.getCancelCallback().run();
            }
            return true;
        }

        // Call the callback with the input
        pending.getCallback().accept(message);
        return true;
    }

    /**
     * Cancel any pending input for a player.
     *
     * @param playerUuid The player's UUID
     */
    public static void cancelInput(UUID playerUuid) {
        PendingInput pending = pendingInputs.remove(playerUuid);
        if (pending != null && pending.getCancelCallback() != null) {
            pending.getCancelCallback().run();
        }
    }

    /**
     * Check if a player has pending input.
     *
     * @param playerUuid The player's UUID
     * @return true if the player has pending input
     */
    public static boolean hasPendingInput(UUID playerUuid) {
        PendingInput pending = pendingInputs.get(playerUuid);
        if (pending != null && pending.isExpired()) {
            pendingInputs.remove(playerUuid);
            return false;
        }
        return pending != null;
    }

    /**
     * Clean up expired inputs.
     * Should be called periodically.
     */
    public static void cleanupExpired() {
        pendingInputs.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
