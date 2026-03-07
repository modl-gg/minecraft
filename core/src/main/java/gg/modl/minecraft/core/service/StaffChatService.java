package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.PlayerProfile;
import gg.modl.minecraft.core.cache.PlayerProfileRegistry;

import java.util.UUID;

public class StaffChatService {
    public enum ChatMode {
        NORMAL,
        STAFF
    }

    private final PlayerProfileRegistry registry;

    public StaffChatService(PlayerProfileRegistry registry) {
        this.registry = registry;
    }

    public boolean isInStaffChat(UUID playerUuid) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        return profile != null && profile.getChatMode() == ChatMode.STAFF;
    }

    /** @return the new chat mode after toggling */
    public ChatMode toggleStaffChat(UUID playerUuid) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        if (profile == null) return ChatMode.NORMAL;
        ChatMode current = profile.getChatMode();
        ChatMode newMode = (current == ChatMode.STAFF) ? ChatMode.NORMAL : ChatMode.STAFF;
        profile.setChatMode(newMode);
        return newMode;
    }

    public void setMode(UUID playerUuid, ChatMode mode) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        if (profile != null) profile.setChatMode(mode);
    }

    public ChatMode getMode(UUID playerUuid) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        return profile != null ? profile.getChatMode() : ChatMode.NORMAL;
    }
}
