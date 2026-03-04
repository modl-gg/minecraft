package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages staff chat mode state for online players.
 * Players in STAFF mode have their chat messages routed to the staff-only channel.
 */
public class StaffChatService {

    public enum ChatMode {
        NORMAL,
        STAFF
    }

    private final Map<UUID, ChatMode> chatModes = new ConcurrentHashMap<>();

    /**
     * Check if a player is currently in staff chat mode.
     *
     * @param playerUuid The player's UUID
     * @return true if the player is in STAFF chat mode
     */
    public boolean isInStaffChat(UUID playerUuid) {
        return chatModes.getOrDefault(playerUuid, ChatMode.NORMAL) == ChatMode.STAFF;
    }

    /**
     * Toggle a player's chat mode between NORMAL and STAFF.
     *
     * @param playerUuid The player's UUID
     * @return The new chat mode after toggling
     */
    public ChatMode toggleStaffChat(UUID playerUuid) {
        ChatMode current = chatModes.getOrDefault(playerUuid, ChatMode.NORMAL);
        ChatMode newMode = (current == ChatMode.STAFF) ? ChatMode.NORMAL : ChatMode.STAFF;
        chatModes.put(playerUuid, newMode);
        return newMode;
    }

    /**
     * Set a player's chat mode to a specific value.
     *
     * @param playerUuid The player's UUID
     * @param mode       The chat mode to set
     */
    public void setMode(UUID playerUuid, ChatMode mode) {
        if (mode == ChatMode.NORMAL) {
            chatModes.remove(playerUuid);
        } else {
            chatModes.put(playerUuid, mode);
        }
    }

    /**
     * Get the current chat mode for a player.
     *
     * @param playerUuid The player's UUID
     * @return The player's current chat mode (defaults to NORMAL)
     */
    public ChatMode getMode(UUID playerUuid) {
        return chatModes.getOrDefault(playerUuid, ChatMode.NORMAL);
    }

    /**
     * Remove a player from the chat mode map (e.g., on disconnect).
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(UUID playerUuid) {
        chatModes.remove(playerUuid);
    }
}
