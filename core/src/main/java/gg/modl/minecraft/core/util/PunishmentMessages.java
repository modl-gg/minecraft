package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PunishmentMessages {
    private static final String FALLBACK_MUTE_MESSAGE = "\u00A7cYou are muted!";

    private static String panelUrl;

    public static void setPanelUrl(String url) {
        panelUrl = url;
    }

    public static String getPanelUrl() {
        return panelUrl;
    }

    public static String getAppealUrl() {
        if (panelUrl != null && !panelUrl.isEmpty()) return panelUrl + "/appeal";
        return Constants.DEFAULT_APPEAL_URL;
    }

    public enum MessageContext {
        DEFAULT,
        SYNC,
        LOGIN,
        CHAT
    }
    
    @Deprecated
    public static String formatBanMessage(SimplePunishment ban) {
        return formatBanMessage(ban, new LocaleManager());
    }

    public static String formatBanMessage(SimplePunishment ban, LocaleManager localeManager) {
        return formatBanMessage(ban, localeManager, MessageContext.DEFAULT);
    }
    
    public static String formatBanMessage(SimplePunishment ban, LocaleManager localeManager, MessageContext context) {
        int ordinal = ban.getOrdinal();
        Map<String, String> variables = buildBasicPunishmentVariables(ban, localeManager);
        return localeManager.getPlayerNotificationMessage(ordinal, ban.getType(), variables, ban, context);
    }

    public static String getMuteMessage(UUID playerUuid, Cache cache, LocaleManager localeManager) {
        Cache.CachedPlayerData data = cache.getCachedPlayerData(playerUuid);
        if (data == null) return FALLBACK_MUTE_MESSAGE;
        if (data.getSimpleMute() != null) return formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
        if (data.getMute() != null) return formatLegacyMuteMessage(data.getMute());
        return FALLBACK_MUTE_MESSAGE;
    }

    @Deprecated
    public static String formatMuteMessage(SimplePunishment mute) {
        return formatMuteMessage(mute, new LocaleManager());
    }

    public static String formatMuteMessage(SimplePunishment mute, LocaleManager localeManager) {
        return formatMuteMessage(mute, localeManager, MessageContext.DEFAULT);
    }
    
    public static String formatMuteMessage(SimplePunishment mute, LocaleManager localeManager, MessageContext context) {
        int ordinal = mute.getOrdinal();
        Map<String, String> variables = buildBasicPunishmentVariables(mute, localeManager);
        return localeManager.getPlayerNotificationMessage(ordinal, mute.getType(), variables, mute, context);
    }
    
    @Deprecated
    public static String formatKickMessage(SimplePunishment kick) {
        return formatKickMessage(kick, new LocaleManager());
    }

    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager) {
        return formatKickMessage(kick, localeManager, MessageContext.DEFAULT);
    }
    
    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager, MessageContext context) {
        int ordinal = kick.getOrdinal();
        Map<String, String> variables = buildBasicPunishmentVariables(kick, localeManager);
        return localeManager.getPlayerNotificationMessage(ordinal, kick.getType(), variables, kick, context);
    }

    @Deprecated
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, String action) {
        return formatPunishmentBroadcast(username, punishment, action, new LocaleManager());
    }

    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, String action, LocaleManager localeManager) {
        int ordinal = punishment.getOrdinal();
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription() != null ? punishment.getDescription() : Constants.UNKNOWN);
        variables.put("description", punishment.getDescription() != null ? punishment.getDescription() : Constants.UNKNOWN);
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", getAppealUrl());
        variables.put("id", punishment.getId() != null ? punishment.getId() : Constants.UNKNOWN);

        return localeManager.getPublicNotificationMessage(ordinal, punishment.getCategory(), variables);
    }

    private static String dateFormatPattern = "MM/dd/yyyy HH:mm";
    private static java.util.TimeZone timeZone = null;

    public static void setDateFormat(String pattern) {
        try {
            new java.text.SimpleDateFormat(pattern);
            dateFormatPattern = pattern;
        } catch (IllegalArgumentException ignored) {}
    }

    public static void setTimezone(String timezoneId) {
        if (timezoneId != null && !timezoneId.isEmpty()) timeZone = java.util.TimeZone.getTimeZone(timezoneId);
        else timeZone = null;
    }

    public static String formatTime(java.util.Date date) {
        if (date == null) return "Never";

        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(dateFormatPattern);
        if (timeZone != null) formatter.setTimeZone(timeZone);
        return formatter.format(date);
    }
    
    public static String formatDuration(long millis) {
        return TimeUtil.formatTimeMillis(millis);
    }
    
    private static Map<String, String> buildBasicPunishmentVariables(SimplePunishment punishment, LocaleManager localeManager) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", punishment.getDescription() != null ? punishment.getDescription() : Constants.UNKNOWN);
        variables.put("description", punishment.getDescription() != null ? punishment.getDescription() : Constants.UNKNOWN);
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("id", punishment.getId() != null ? punishment.getId() : Constants.UNKNOWN);
        variables.put("appeal_url", getAppealUrl());
        variables.put("temp", punishment.isPermanent() ? "permanently" : "temporarily");
        variables.put("for_duration", computeForDuration(punishment));

        java.util.Date issuedDate = punishment.getIssuedAsDate();
        variables.put("issued", issuedDate != null ? formatTime(issuedDate) : Constants.UNKNOWN);

        String playerDesc = punishment.getPlayerDescription();
        variables.put("player_description", playerDesc != null ? playerDesc : "");

        String issuer = punishment.getIssuerName();
        variables.put("issuer", issuer != null ? issuer : Constants.DEFAULT_STAFF_NAME);

        variables.put("will_expire", computeWillExpire(punishment));

        return variables;
    }
    
    private static String computeForDuration(SimplePunishment punishment) {
        if (punishment.isPermanent() || punishment.getExpiration() == null) return "";
        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        return timeLeft > 0 ? " for " + formatDuration(timeLeft) : "";
    }

    private static String computeWillExpire(SimplePunishment punishment) {
        if (punishment.isPermanent() || punishment.getExpiration() == null) return "";
        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        if (timeLeft <= 0) return "";
        String durationStr = formatDuration(timeLeft);
        String typeWord = punishment.isBan() ? "ban" : (punishment.isMute() ? "mute" : "punishment");
        return "\n\u00A77This " + typeWord + " will expire in \u00A7f" + durationStr + "\u00A77.";
    }

    public static String formatLegacyMuteMessage(Punishment mute) {
        String reason = mute.getReason() != null ? mute.getReason() : Constants.UNKNOWN;
        StringBuilder message = new StringBuilder();
        message.append("\u00A7cYou are muted!\n");
        message.append("\u00A77Reason: \u00A7f").append(reason);
        if (mute.getExpires() != null) {
            long timeLeft = mute.getExpires().getTime() - System.currentTimeMillis();
            if (timeLeft > 0) {
                message.append("\n\u00A77Time remaining: \u00A7f").append(formatDuration(timeLeft));
            }
        } else {
            message.append("\n\u00A74This mute is permanent.");
        }
        return message.toString();
    }

}