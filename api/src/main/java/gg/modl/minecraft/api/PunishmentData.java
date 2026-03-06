package gg.modl.minecraft.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public record PunishmentData(
        @Nullable String blockedName,
        @Nullable String blockedSkin,
        @Nullable String linkedBanId,
        @NotNull AtomicReference<Date> linkedBanExpiry,
        @Nullable List<String> chatLog,
        long duration,
        boolean altBlocking,
        boolean wipeAfterExpiry
) {
    private static final long NO_DURATION = -1L;

    private static final String KEY_BLOCKED_NAME = "blockedName";
    private static final String KEY_BLOCKED_SKIN = "blockedSkin";
    private static final String KEY_LINKED_BAN_ID = "linkedBanId";
    private static final String KEY_LINKED_BAN_EXPIRY = "linkedBanExpiry";
    private static final String KEY_CHAT_LOG = "chatLog";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_ALT_BLOCKING = "altBlocking";
    private static final String KEY_WIPE_AFTER_EXPIRY = "wipeAfterExpiry";

    public static PunishmentData fromMap(Map<String, Object> map) {
        String blockedName = (String) map.get(KEY_BLOCKED_NAME);
        String blockedSkin = (String) map.get(KEY_BLOCKED_SKIN);
        String linkedBanId = (String) map.get(KEY_LINKED_BAN_ID);

        AtomicReference<Date> linkedBanExpiry = new AtomicReference<>();
        Object expiry = map.get(KEY_LINKED_BAN_EXPIRY);
        if (expiry instanceof Long l) linkedBanExpiry.set(new Date(l));
        else if (expiry instanceof Date d) linkedBanExpiry.set(d);

        List<String> chatLog = parseChatLog(map.get(KEY_CHAT_LOG));

        long duration = parseDuration(map.get(KEY_DURATION));

        return new PunishmentData(
                blockedName,
                blockedSkin,
                linkedBanId,
                linkedBanExpiry,
                chatLog,
                duration,
                map.containsKey(KEY_ALT_BLOCKING),
                map.containsKey(KEY_WIPE_AFTER_EXPIRY)
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