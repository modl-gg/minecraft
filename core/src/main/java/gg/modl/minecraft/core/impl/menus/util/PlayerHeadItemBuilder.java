package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.IPAddress;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;

import java.util.*;
import java.util.concurrent.TimeUnit;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public final class PlayerHeadItemBuilder {
    private static final String UNKNOWN = "Unknown";

    private PlayerHeadItemBuilder() {}

    public static CirrusItem create(Platform platform, Account targetAccount, String targetName, UUID targetUuid) {
        LocaleManager locale = platform.getLocaleManager();
        List<String> lore = new ArrayList<>();

        String firstLogin = UNKNOWN;
        if (!targetAccount.getUsernames().isEmpty()) {
            Date earliest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
            if (earliest != null) firstLogin = MenuItems.formatDate(earliest);
        }

        CachedProfile targetProfile = platform.getCache() != null ? platform.getCache().getPlayerProfile(targetUuid) : null;
        boolean isOnline = targetProfile != null;
        boolean isBanned = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isBanType());
        boolean isMuted = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isMuteType());

        boolean realIpLogged = !targetAccount.getIpList().isEmpty();

        String lastSeenOrSessionTime = "N/A";
        if (isOnline) {
            long sessionMs = targetProfile.getSessionDuration();
            lastSeenOrSessionTime = MenuItems.formatDuration(sessionMs);
        } else if (!targetAccount.getUsernames().isEmpty()) {
            Date latest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(null);
            if (latest != null) lastSeenOrSessionTime = MenuItems.formatDate(latest);
        }

        String server = UNKNOWN;
        if (isOnline) server = platform.getPlayerServer(targetUuid);
        else {
            Object lastServer = targetAccount.getData().get("lastServer");
            if (lastServer instanceof String) server = (String) lastServer;
        }

        String region = UNKNOWN;
        String country = UNKNOWN;
        if (!targetAccount.getIpList().isEmpty()) {
            IPAddress latestIp = targetAccount.getIpList().get(targetAccount.getIpList().size() - 1);
            region = displayValue(latestIp.getRegion(), UNKNOWN);
            country = displayValue(latestIp.getCountry(), UNKNOWN);
        }

        String playtime = "N/A";
        Object playtimeObj = targetAccount.getData().get("totalPlaytimeSeconds");
        long totalSeconds = 0;
        if (playtimeObj instanceof Number) totalSeconds = ((Number) playtimeObj).longValue();
        if (isOnline) {
            totalSeconds += targetProfile.getSessionDuration() / 1000;
        }
        if (totalSeconds > 0) {
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            playtime = hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("player_name", targetName);
        vars.put("uuid", targetUuid.toString());
        vars.put("first_login", firstLogin);
        vars.put("is_online", isOnline ? "&aYes" : "&cNo");
        vars.put("last_seen_or_session_time", lastSeenOrSessionTime);
        vars.put("playtime", playtime);
        vars.put("real_ip_logged", realIpLogged ? "&aYes" : "&cNo");
        vars.put("is_banned", isBanned ? "&cYes" : "&aNo");
        vars.put("is_muted", isMuted ? "&cYes" : "&aNo");
        vars.put("server", server);
        vars.put("region", region);
        vars.put("country", country);

        List<String> loreLinesRaw = locale.getMessageList("menus.player_head.lore");
        lore.addAll(renderLoreLines(loreLinesRaw, vars));

        String title = locale.getMessage("menus.player_head.title", mapOf("player_name", targetName));
        title = MenuItems.translateColorCodes(title);

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );

        if (platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(targetUuid);
            if (cachedTexture == null) {
                try {
                    WebPlayer wp = WebPlayer.get(targetUuid)
                            .get(3, TimeUnit.SECONDS);
                    if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                        platform.getCache().cacheSkinTexture(targetUuid, wp.getTextureValue());
                        cachedTexture = wp.getTextureValue();
                    }
                } catch (Exception ignored) {}
            }
            if (cachedTexture != null) headItem = headItem.texture(cachedTexture);
        }

        return headItem;
    }

    private static String displayValue(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    static List<String> renderLoreLines(List<String> loreLinesRaw, Map<String, String> vars) {
        List<String> lore = new ArrayList<>();
        for (String line : loreLinesRaw) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", displayValue(entry.getValue(), UNKNOWN));
            }
            lore.add(processed);
        }
        return lore;
    }
}
