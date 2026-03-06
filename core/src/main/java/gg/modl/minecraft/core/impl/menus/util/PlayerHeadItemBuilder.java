package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.IPAddress;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class PlayerHeadItemBuilder {
    private PlayerHeadItemBuilder() {}

    public static CirrusItem create(Platform platform, Account targetAccount, String targetName, UUID targetUuid) {
        LocaleManager locale = platform.getLocaleManager();
        List<String> lore = new ArrayList<>();

        String firstLogin = "Unknown";
        if (!targetAccount.getUsernames().isEmpty()) {
            Date earliest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .min(Date::compareTo)
                    .orElse(null);
            if (earliest != null) firstLogin = MenuItems.formatDate(earliest);
        }

        boolean isOnline = platform.getCache() != null && platform.getCache().isOnline(targetUuid);
        boolean isBanned = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isBanType());
        boolean isMuted = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isMuteType());

        boolean realIpLogged = !targetAccount.getIpList().isEmpty();

        String lastSeenOrSessionTime = "N/A";
        if (isOnline && platform.getCache() != null) {
            long sessionMs = platform.getCache().getSessionDuration(targetUuid);
            lastSeenOrSessionTime = MenuItems.formatDuration(sessionMs);
        } else if (!targetAccount.getUsernames().isEmpty()) {
            Date latest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .max(Date::compareTo)
                    .orElse(null);
            if (latest != null) lastSeenOrSessionTime = MenuItems.formatDate(latest);
        }

        String server = "Unknown";
        if (isOnline) server = platform.getPlayerServer(targetUuid);
        else {
            targetAccount.getData();
            Object lastServer = targetAccount.getData().get("lastServer");
            if (lastServer instanceof String) server = (String) lastServer;
        }

        String region = "Unknown";
        String country = "Unknown";
        if (!targetAccount.getIpList().isEmpty()) {
            IPAddress latestIp = targetAccount.getIpList().get(targetAccount.getIpList().size() - 1);
            region = latestIp.getRegion();
            country = latestIp.getCountry();
        }

        String playtime = "N/A";
        targetAccount.getData();
        Object playtimeObj = targetAccount.getData().get("totalPlaytimeSeconds");
        long totalSeconds = 0;
        if (playtimeObj instanceof Number) totalSeconds = ((Number) playtimeObj).longValue();
        if (isOnline && platform.getCache() != null) {
            totalSeconds += platform.getCache().getSessionDuration(targetUuid) / 1000;
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
        for (String line : loreLinesRaw) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        String title = locale.getMessage("menus.player_head.title", Map.of("player_name", targetName));
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
                    if (wp != null && wp.valid() && wp.textureValue() != null) {
                        platform.getCache().cacheSkinTexture(targetUuid, wp.textureValue());
                        cachedTexture = wp.textureValue();
                    }
                } catch (Exception ignored) {}
            }
            if (cachedTexture != null) headItem = headItem.texture(cachedTexture);
        }

        return headItem;
    }
}
