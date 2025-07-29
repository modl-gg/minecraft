package gg.modl.minecraft.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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

    public Map<String, Object> export() {
        Map<String, Object> map = new HashMap<>();

        if (blockedName != null) map.put("blockedName", blockedName);
        if (blockedSkin != null) map.put("blockedSkin", blockedSkin);
        if (linkedBanId != null) map.put("linkedBanId", linkedBanId);
        if (linkedBanExpiry.get() != null) map.put("linkedBanExpiry", linkedBanExpiry.get().getTime());
        if (chatLog != null) map.put("chatLog", chatLog);
        if (duration != -1L) map.put("duration", duration);
        if (altBlocking) map.put("altBlocking", true);
        if (wipeAfterExpiry) map.put("wipeAfterExpiry", true);

        return map;
    }

    public static PunishmentData fromMap(Map<String, Object> map) {
        String blockedName = (String) map.get("blockedName");
        String blockedSkin = (String) map.get("blockedSkin");
        String linkedBanId = (String) map.get("linkedBanId");

        AtomicReference<Date> linkedBanExpiry = new AtomicReference<>();
        if (map.containsKey("linkedBanExpiry")) {
            Object expiry = map.get("linkedBanExpiry");
            if (expiry instanceof Long) {
                linkedBanExpiry.set(new Date((Long) expiry));
            } else if (expiry instanceof Date) {
                linkedBanExpiry.set((Date) expiry);
            }
        }

        List<String> chatLog = null;
        if (map.containsKey("chatLog")) {
            Object chatLogObj = map.get("chatLog");
            if (chatLogObj instanceof List) {
                chatLog = new ArrayList<>((List<String>) chatLogObj);
            }
        }

        long duration = -1L;
        if (map.containsKey("duration")) {
            Object durationObj = map.get("duration");
            if (durationObj instanceof Long) {
                duration = (Long) durationObj;
            } else if (durationObj instanceof Integer) {
                duration = ((Integer) durationObj).longValue();
            }
        }

        boolean altBlocking = map.containsKey("altBlocking");
        boolean wipeAfterExpiry = map.containsKey("wipeAfterExpiry");

        return new PunishmentData(
                blockedName,
                blockedSkin,
                linkedBanId,
                linkedBanExpiry,
                chatLog,
                duration,
                altBlocking,
                wipeAfterExpiry
        );
    }
}