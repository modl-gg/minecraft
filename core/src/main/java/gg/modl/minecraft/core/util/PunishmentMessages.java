package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for formatting punishment messages across all platforms
 */
public class PunishmentMessages {

    private static String panelUrl;

    /**
     * Set the panel URL (derived from config's api.url).
     * Called during plugin initialization.
     */
    public static void setPanelUrl(String url) {
        panelUrl = url;
    }

    /**
     * Get the configured panel URL.
     */
    public static String getPanelUrl() {
        return panelUrl;
    }

    /**
     * Build the appeal URL from the configured panel URL.
     */
    public static String getAppealUrl() {
        if (panelUrl != null && !panelUrl.isEmpty()) {
            return panelUrl + "/appeal";
        }
        return "https://server.modl.gg/appeal";
    }

    /**
     * Context for punishment messages to determine appropriate tense and formatting
     */
    public enum MessageContext {
        DEFAULT,     // Standard message (have been)
        SYNC,        // From sync system (are)
        LOGIN,       // From login check (have been)
        CHAT         // From chat event (are)
    }
    
    /**
     * Format a ban message with all available punishment data (deprecated - use overload with LocaleManager)
     * Platform-specific components should be created from this text
     * @deprecated Use formatBanMessage(SimplePunishment, LocaleManager) instead
     */
    @Deprecated
    public static String formatBanMessage(SimplePunishment ban) {
        // Legacy fallback - create basic locale manager with default messages
        return formatBanMessage(ban, new LocaleManager());
    }
    
    /**
     * Format a ban message with all available punishment data
     * Platform-specific components should be created from this text
     */
    public static String formatBanMessage(SimplePunishment ban, LocaleManager localeManager) {
        return formatBanMessage(ban, localeManager, MessageContext.DEFAULT);
    }
    
    /**
     * Format a ban message with context for dynamic variables
     */
    public static String formatBanMessage(SimplePunishment ban, LocaleManager localeManager, MessageContext context) {
        // Use the actual punishment ordinal from the punishment data
        int ordinal = ban.getOrdinal();
        
        // Build basic variables for message formatting
        Map<String, String> variables = buildBasicPunishmentVariables(ban, localeManager);
        
        // Use punishment type specific message with full context for dynamic variables
        return localeManager.getPlayerNotificationMessage(ordinal, ban.getType(), variables, ban, context);
    }

    public static String getFormattedDuration(SimplePunishment punishment) {
        if (punishment.isPermanent()) {
            return "permanently";
        }

        if (punishment.isExpired()) {
            return "expired";
        }

        assert punishment.getExpiration() != null;
        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        // Import the utility method dynamically to avoid circular imports
        return formatDuration(timeLeft);
    }
    /**
     * Format a mute message with all available punishment data (deprecated - use overload with LocaleManager)
     * @deprecated Use formatMuteMessage(SimplePunishment, LocaleManager) instead
     */
    @Deprecated
    public static String formatMuteMessage(SimplePunishment mute) {
        // Legacy fallback - create basic locale manager with default messages
        return formatMuteMessage(mute, new LocaleManager());
    }
    
    /**
     * Format a mute message with all available punishment data
     */
    public static String formatMuteMessage(SimplePunishment mute, LocaleManager localeManager) {
        return formatMuteMessage(mute, localeManager, MessageContext.DEFAULT);
    }
    
    /**
     * Format a mute message with context for dynamic variables
     */
    public static String formatMuteMessage(SimplePunishment mute, LocaleManager localeManager, MessageContext context) {
        // Use the actual punishment ordinal from the punishment data
        int ordinal = mute.getOrdinal();
        
        // Build basic variables for message formatting
        Map<String, String> variables = buildBasicPunishmentVariables(mute, localeManager);
        
        // Use punishment type specific message with full context for dynamic variables
        return localeManager.getPlayerNotificationMessage(ordinal, mute.getType(), variables, mute, context);
    }
    
    /**
     * Format a kick message with all available punishment data (deprecated - use overload with LocaleManager)
     * @deprecated Use formatKickMessage(SimplePunishment, LocaleManager) instead
     */
    @Deprecated
    public static String formatKickMessage(SimplePunishment kick) {
        // Legacy fallback - create basic locale manager with default messages
        return formatKickMessage(kick, new LocaleManager());
    }
    
    /**
     * Format a kick message with all available punishment data
     */
    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager) {
        return formatKickMessage(kick, localeManager, MessageContext.DEFAULT);
    }
    
    /**
     * Format a kick message with context for dynamic variables
     */
    public static String formatKickMessage(SimplePunishment kick, LocaleManager localeManager, MessageContext context) {
        // Use the actual punishment ordinal from the punishment data
        int ordinal = kick.getOrdinal();
        
        // Build basic variables for message formatting
        Map<String, String> variables = buildBasicPunishmentVariables(kick, localeManager);
        
        // Use punishment type specific message with full context for dynamic variables
        return localeManager.getPlayerNotificationMessage(ordinal, kick.getType(), variables, kick, context);
    }

    /**
     * Format a punishment for broadcast messages (deprecated - use overload with LocaleManager)
     * @deprecated Use formatPunishmentBroadcast(String, SimplePunishment, String, LocaleManager) instead
     */
    @Deprecated
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, String action) {
        // Legacy fallback - create basic locale manager with default messages
        return formatPunishmentBroadcast(username, punishment, action, new LocaleManager());
    }
    
    /**
     * Format a punishment for broadcast messages.
     * Uses the punishment's actual ordinal to look up the correct public_notification in locale.
     *
     * @param username The username of the punished player
     * @param punishment The punishment data containing ordinal, description, etc.
     * @param action Unused - kept for API compatibility. The ordinal from punishment is used instead.
     * @param localeManager The locale manager for message lookups
     * @return The formatted broadcast message
     */
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, String action, LocaleManager localeManager) {
        // Use the actual punishment ordinal from the punishment data
        // This ensures custom punishment types (ordinal 3, 4, 5, etc.) use their own public_notification
        int ordinal = punishment.getOrdinal();

        // Build variables for message formatting
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("description", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", getAppealUrl());
        variables.put("id", punishment.getId() != null ? punishment.getId() : "Unknown");

        // Use public notification message for the actual punishment type ordinal
        return localeManager.getPublicNotificationMessage(ordinal, punishment.getCategory(), variables);
    }
    
    private static String dateFormatPattern = "MM/dd/yyyy HH:mm";
    private static java.util.TimeZone timeZone = null;

    /**
     * Set the date format pattern from config. Called during plugin initialization.
     */
    public static void setDateFormat(String pattern) {
        try {
            new java.text.SimpleDateFormat(pattern); // validate
            dateFormatPattern = pattern;
        } catch (IllegalArgumentException ignored) {
            // Keep default if pattern is invalid
        }
    }

    /**
     * Set the timezone from config. Called during plugin initialization.
     */
    public static void setTimezone(String timezoneId) {
        if (timezoneId != null && !timezoneId.isEmpty()) {
            timeZone = java.util.TimeZone.getTimeZone(timezoneId);
        } else {
            timeZone = null;
        }
    }

    /**
     * Format time in a user-friendly way
     */
    public static String formatTime(java.util.Date date) {
        if (date == null) return "Never";

        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(dateFormatPattern);
        if (timeZone != null) {
            formatter.setTimeZone(timeZone);
        }
        return formatter.format(date);
    }
    
    /**
     * Format duration in milliseconds to human readable string
     */
    public static String formatDuration(long millis) {
        return TimeUtil.formatTimeMillis(millis);
    }
    
    /**
     * Build basic punishment variables map (without dynamic context variables)
     */
    private static Map<String, String> buildBasicPunishmentVariables(SimplePunishment punishment, LocaleManager localeManager) {
        Map<String, String> variables = new HashMap<>();

        // Basic variables
        variables.put("target", "You");
        variables.put("reason", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("description", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("id", punishment.getId() != null ? punishment.getId() : "Unknown");

        variables.put("appeal_url", getAppealUrl());

        // Temp - "temporarily" or "permanently"
        variables.put("temp", punishment.isPermanent() ? "permanently" : "temporarily");

        // Issued date using configured format
        java.util.Date issuedDate = punishment.getIssuedAsDate();
        if (issuedDate != null) {
            variables.put("issued", formatTime(issuedDate));
        } else {
            variables.put("issued", "Unknown");
        }

        // Player description from punishment type
        String playerDesc = punishment.getPlayerDescription();
        variables.put("player_description", playerDesc != null ? playerDesc : "");

        // Issuer name
        String issuer = punishment.getIssuerName();
        variables.put("issuer", issuer != null ? issuer : "Staff");

        // Will expire message
        if (punishment.isPermanent()) {
            variables.put("will_expire", "");
        } else {
            Long expiration = punishment.getExpiration();
            if (expiration != null) {
                long timeLeft = expiration - System.currentTimeMillis();
                if (timeLeft > 0) {
                    String durationStr = formatDuration(timeLeft);
                    String typeWord = punishment.isBan() ? "ban" : (punishment.isMute() ? "mute" : "punishment");
                    variables.put("will_expire", "\n§7This " + typeWord + " will expire in §f" + durationStr + "§7.");
                } else {
                    variables.put("will_expire", "");
                }
            } else {
                variables.put("will_expire", "");
            }
        }

        return variables;
    }
    
    /**
     * Format a legacy mute message for old Punishment format (fallback).
     * Uses raw section-sign color codes that work on all platforms.
     */
    public static String formatLegacyMuteMessage(Punishment mute) {
        String reason = mute.getReason() != null ? mute.getReason() : "No reason provided";
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

    /**
     * Create a clean text version without color codes (for console/logs)
     */
    public static String stripColors(String message) {
        return message.replaceAll("§[0-9a-fk-or]", "");
    }
    
    /**
     * Convert legacy color codes (&) to modern format (§)
     */
    public static String translateColors(String message) {
        return message.replace('&', '§');
    }
}