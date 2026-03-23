package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor
public class PunishmentData {
    @Nullable private final String blockedName;
    @Nullable private final String blockedSkin;
    @Nullable private final String linkedBanId;
    @Nullable private final Date linkedBanExpiry;
    @Nullable private final List<String> chatLog;
    private final long duration;
    private final boolean altBlocking;
    private final boolean wipeAfterExpiry;

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
        if (expiry instanceof Long) linkedBanExpiry = new Date((Long) expiry);
        else if (expiry instanceof Date) linkedBanExpiry = (Date) expiry;

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
        if (chatLogObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> typedList = (List<String>) chatLogObj;
            return new ArrayList<>(typedList);
        }
        return null;
    }

    private static long parseDuration(Object durationObj) {
        if (durationObj instanceof Long) return (Long) durationObj;
        if (durationObj instanceof Integer) return ((Integer) durationObj).longValue();
        return NO_DURATION;
    }
}
