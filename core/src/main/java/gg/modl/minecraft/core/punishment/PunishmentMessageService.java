package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.locale.PunishmentMessageContext;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.TimeUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class PunishmentMessageService {
    private static final String FALLBACK_MUTE_MESSAGE = "§cYou are muted!";

    private final LocaleManager localeManager;
    private final DateFormatter dateFormatter;
    private final String panelUrl;

    public PunishmentMessageService(LocaleManager localeManager, DateFormatter dateFormatter, String panelUrl) {
        this.localeManager = localeManager;
        this.dateFormatter = dateFormatter;
        this.panelUrl = panelUrl;
    }

    public String getPanelUrl() {
        return panelUrl;
    }

    public String getAppealUrl() {
        return panelUrl + "/appeal";
    }

    public String formatBanMessage(SimplePunishment ban) {
        return formatBanMessage(ban, PunishmentMessageContext.DEFAULT);
    }

    public String formatBanMessage(SimplePunishment ban, PunishmentMessageContext context) {
        return formatPunishmentNotification(ban, context);
    }

    public String getMuteMessage(SimplePunishment mute) {
        if (mute != null) return formatMuteMessage(mute, PunishmentMessageContext.CHAT);
        return FALLBACK_MUTE_MESSAGE;
    }

    public String formatMuteMessage(SimplePunishment mute, PunishmentMessageContext context) {
        return formatPunishmentNotification(mute, context);
    }

    public String formatKickMessage(SimplePunishment kick, PunishmentMessageContext context) {
        return formatPunishmentNotification(kick, context);
    }

    private String formatPunishmentNotification(SimplePunishment punishment, PunishmentMessageContext context) {
        Map<String, String> variables = buildBasicPunishmentVariables(punishment);
        return localeManager.getPlayerNotificationMessage(punishment.getOrdinal(), punishment.getType(), variables, punishment, context);
    }

    public String formatPunishmentBroadcast(String username, SimplePunishment punishment) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", formatRemainingDuration(punishment));
        variables.put("appeal_url", getAppealUrl());
        variables.put("id", punishment.getId());
        variables.put("temp", punishment.isPermanent()
                ? localeManager.getMessage("punishment_words.permanently")
                : localeManager.getMessage("punishment_words.temporarily"));

        return localeManager.getPublicNotificationMessage(punishment.getOrdinal(), variables);
    }

    private static String formatRemainingDuration(SimplePunishment punishment) {
        if (punishment.isPermanent() || punishment.getExpiration() == null) return "permanent";
        return TimeUtil.formatTimeMillis(punishment.getExpiration() - System.currentTimeMillis());
    }

    private Map<String, String> buildBasicPunishmentVariables(SimplePunishment punishment) {
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", punishment.getDescription());
        variables.put("description", punishment.getDescription());
        variables.put("duration", formatRemainingDuration(punishment));
        variables.put("id", punishment.getId());
        variables.put("appeal_url", getAppealUrl());
        variables.put("temp", punishment.isPermanent()
                ? localeManager.getMessage("punishment_words.permanently")
                : localeManager.getMessage("punishment_words.temporarily"));
        variables.put("for_duration", computeForDuration(punishment));

        Date issuedDate = punishment.getIssuedAsDate();
        variables.put("issued", issuedDate != null ? dateFormatter.format(issuedDate) : Constants.UNKNOWN);

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
        return timeLeft > 0 ? " for " + TimeUtil.formatTimeMillis(timeLeft) : "";
    }

    private static String computeWillExpire(SimplePunishment punishment) {
        if (punishment.isPermanent() || punishment.getExpiration() == null) return "";
        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        if (timeLeft <= 0) return "";
        String durationStr = TimeUtil.formatTimeMillis(timeLeft);
        String typeWord = punishment.isBan() ? "ban" : (punishment.isMute() ? "mute" : "punishment");
        return "\n§7This " + typeWord + " will expire in §f" + durationStr + "§7.";
    }
}
