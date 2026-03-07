package gg.modl.minecraft.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public record PunishmentData(
        @Nullable String blockedName,
        @Nullable String blockedSkin,
        @Nullable String linkedBanId,
        @Nullable Date linkedBanExpiry,
        @Nullable List<String> chatLog,
        long duration,
        boolean altBlocking,
        boolean wipeAfterExpiry) {
    private static final String KEY_BLOCKED_NAME = "blockedName", KEY_BLOCKED_SKIN = "blockedSkin",
            KEY_LINKED_BAN_ID = "linkedBanId", KEY_LINKED_BAN_EXPIRY = "linkedBanExpiry",
            KEY_CHAT_LOG = "chatLog", KEY_DURATION = "duration",
            KEY_ALT_BLOCKING = "altBlocking", KEY_WIPE_AFTER_EXPIRY = "wipeAfterExpiry";
    private static final long NO_DURATION = -1L;

    public static PunishmentData fromMap(Map<String, Object> map) {
        String blockedName = (String) map.get(KEY_BLOCKED_NAME);
        String blockedSkin = (String) map.get(KEY_BLOCKED_SKIN);
        String linkedBanId = (String) map.get(KEY_LINKED_BAN_ID);

        Date linkedBanExpiry = null;
        Object expiry = map.get(KEY_LINKED_BAN_EXPIRY);
        if (expiry instanceof Long l) linkedBanExpiry = new Date(l);
        else if (expiry instanceof Date d) linkedBanExpiry = d;

        List<String> chatLog = parseChatLog(map.get(KEY_CHAT_LOG));

        long duration = parseDuration(map.get(KEY_DURATION));

        return new PunishmentData(
                blockedName,
                blockedSkin,
                linkedBanId,
                linkedBanExpiry,
                chatLog,
                duration,
                Boolean.TRUE.equals(map.get(KEY_ALT_BLOCKING)),
                Boolean.TRUE.equals(map.get(KEY_WIPE_AFTER_EXPIRY))
        );
    }

    private static List<String> parseChatLog(Object chatLogObj) {
        if (chatLogObj instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<String> typedList = (List<String>) list;
            return new ArrayList<>(typedList);
        }
        return null;
    }

    private static long parseDuration(Object durationObj) {
        if (durationObj instanceof Long l) return l;
        if (durationObj instanceof Integer i) return i.longValue();
        return NO_DURATION;
    }
}