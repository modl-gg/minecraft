package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class PunishmentMessages {
    private static final String FALLBACK_MUTE_MESSAGE = "§cYou are muted!";

    @Setter @Getter private static String panelUrl;

    public static String getAppealUrl() {
        return panelUrl + "/appeal";
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
        Map<String, String> variables = buildBasicPunishmentVariables(ban);
        return localeManager.getPlayerNotificationMessage(ordinal, ban.getType(), variables, ban, context);
    }

    public static String getMuteMessage(SimplePunishment mute, LocaleManager localeManager) {
        if (mute != null) return formatMuteMessage(mute, localeManager, MessageContext.CHAT);
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
        Map<String, String> variables = buildBasicPunishmentVariables(mute);
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
        Map<String, String> variables = buildBasicPunishmentVariables(kick);
        return localeManager.getPlayerNotificationMessage(ordinal, kick.getType(), variables, kick, context);
    }

    @Deprecated
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment) {
        return formatPunishmentBroadcast(username, punishment, new LocaleManager());
    }

    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, LocaleManager localeManager) {
        int ordinal = punishment.getOrdinal();
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", getAppealUrl());
        variables.put("id", punishment.getId());

        return localeManager.getPublicNotificationMessage(ordinal, variables);
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
    
    private static Map<String, String> buildBasicPunishmentVariables(SimplePunishment punishment) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("id", punishment.getId());
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
        return "\n§7This " + typeWord + " will expire in §f" + durationStr + "§7.";
    }

}