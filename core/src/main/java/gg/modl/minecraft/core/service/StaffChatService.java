package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffChatService {
    public enum ChatMode {
        NORMAL,
        STAFF
    }

    private final Map<UUID, ChatMode> chatModes = new ConcurrentHashMap<>();

    public boolean isInStaffChat(UUID playerUuid) {
        return chatModes.getOrDefault(playerUuid, ChatMode.NORMAL) == ChatMode.STAFF;
    }

    /** @return the new chat mode after toggling */
    public ChatMode toggleStaffChat(UUID playerUuid) {
        ChatMode current = chatModes.getOrDefault(playerUuid, ChatMode.NORMAL);
        ChatMode newMode = (current == ChatMode.STAFF) ? ChatMode.NORMAL : ChatMode.STAFF;
        chatModes.put(playerUuid, newMode);
        return newMode;
    }

    public void setMode(UUID playerUuid, ChatMode mode) {
        if (mode == ChatMode.NORMAL) chatModes.remove(playerUuid);
        else chatModes.put(playerUuid, mode);
    }

    public ChatMode getMode(UUID playerUuid) {
        return chatModes.getOrDefault(playerUuid, ChatMode.NORMAL);
    }

    public void removePlayer(UUID playerUuid) {
        chatModes.remove(playerUuid);
    }
}
