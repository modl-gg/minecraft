package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;

public final class PunishmentMessages {
    private static final String FALLBACK_MUTE_MESSAGE = "\u00a7cYou are muted!";

    private PunishmentMessages() {}

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
        return formatPunishmentNotification(ban, localeManager, context);
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
        return formatPunishmentNotification(mute, localeManager, context);
    }

    @Deprecated
    public static String formatKickMessage(SimplePunishment kick) {
        return formatKickMessage(kick, new LocaleManager());
    }

    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager) {
        return formatKickMessage(kick, localeManager, MessageContext.DEFAULT);
    }

    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager, MessageContext context) {
        return formatPunishmentNotification(kick, localeManager, context);
    }

    private static String formatPunishmentNotification(SimplePunishment punishment, LocaleManager localeManager, MessageContext context) {
        Map<String, String> variables = buildBasicPunishmentVariables(punishment, localeManager);
        return localeManager.getPlayerNotificationMessage(punishment.getOrdinal(), punishment.getType(), variables, punishment, context);
    }

    @Deprecated
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment) {
        return formatPunishmentBroadcast(username, punishment, new LocaleManager());
    }

    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, LocaleManager localeManager) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", getAppealUrl());
        variables.put("id", punishment.getId());
        variables.put("temp", punishment.isPermanent()
                ? localeManager.getMessage("punishment_words.permanently")
                : localeManager.getMessage("punishment_words.temporarily"));

        return localeManager.getPublicNotificationMessage(punishment.getOrdinal(), variables);
    }

    public static String formatDuration(long millis) {
        return TimeUtil.formatTimeMillis(millis);
    }

    private static Map<String, String> buildBasicPunishmentVariables(SimplePunishment punishment, LocaleManager localeManager) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("id", punishment.getId());
        variables.put("appeal_url", getAppealUrl());
        variables.put("temp", punishment.isPermanent()
                ? localeManager.getMessage("punishment_words.permanently")
                : localeManager.getMessage("punishment_words.temporarily"));
        variables.put("for_duration", computeForDuration(punishment));

        Date issuedDate = punishment.getIssuedAsDate();
        variables.put("issued", issuedDate != null ? DateFormatter.format(issuedDate) : Constants.UNKNOWN);

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
        return "\n\u00a77This " + typeWord + " will expire in \u00a7f" + durationStr + "\u00a77.";
    }
}
