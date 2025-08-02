package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for formatting punishment messages across all platforms
 */
public class PunishmentMessages {
    
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
        // Use the actual punishment ordinal from the punishment data
        int ordinal = ban.getOrdinal();
        
        // Build variables for message formatting
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", ban.getDescription() != null ? ban.getDescription() : "No reason specified");
        variables.put("description", ban.getDescription() != null ? ban.getDescription() : "No reason specified");
        variables.put("duration", ban.isPermanent() ? "permanent" : formatDuration(ban.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", localeManager.getMessage("config.appeal_url"));
        variables.put("id", ban.getId() != null ? ban.getId() : "Unknown");
        
        // Use punishment type specific message for consistency
        return localeManager.getPlayerNotificationMessage(ordinal, variables);
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
        // Use the actual punishment ordinal from the punishment data
        int ordinal = mute.getOrdinal();
        
        // Build variables for message formatting
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", mute.getDescription() != null ? mute.getDescription() : "No reason specified");
        variables.put("description", mute.getDescription() != null ? mute.getDescription() : "No reason specified");
        variables.put("duration", mute.isPermanent() ? "permanent" : formatDuration(mute.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", localeManager.getMessage("config.appeal_url"));
        variables.put("id", mute.getId() != null ? mute.getId() : "Unknown");
        
        // Use punishment type specific message for consistency
        return localeManager.getPlayerNotificationMessage(ordinal, variables);
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
        // Use the actual punishment ordinal from the punishment data
        int ordinal = kick.getOrdinal();
        
        // Build variables for message formatting
        Map<String, String> variables = new HashMap<>();
        variables.put("target", "You");
        variables.put("reason", kick.getDescription() != null ? kick.getDescription() : "No reason specified");
        variables.put("description", kick.getDescription() != null ? kick.getDescription() : "No reason specified");
        variables.put("duration", "temporary"); // Kicks are always temporary
        variables.put("appeal_url", localeManager.getMessage("config.appeal_url"));
        variables.put("id", kick.getId() != null ? kick.getId() : "Unknown");
        
        // Use punishment type specific message for consistency
        return localeManager.getPlayerNotificationMessage(ordinal, variables);
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
     * Format a punishment for broadcast messages
     */
    public static String formatPunishmentBroadcast(String username, SimplePunishment punishment, String action, LocaleManager localeManager) {
        // Determine punishment type ordinal based on action (for manual punishments)
        int ordinal;
        switch (action.toLowerCase()) {
            case "kicked":
                ordinal = 0; // Kick
                break;
            case "muted":
                ordinal = 1; // Manual Mute
                break;
            case "banned":
                ordinal = 2; // Manual Ban
                break;
            default:
                // For dynamic punishments, try to determine from punishment type
                ordinal = punishment.getType() != null ? Integer.parseInt(punishment.getType()) : 2;
                break;
        }
        
        // Build variables for message formatting
        Map<String, String> variables = new HashMap<>();
        variables.put("target", username);
        variables.put("reason", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("description", punishment.getDescription() != null ? punishment.getDescription() : "No reason specified");
        variables.put("duration", punishment.isPermanent() ? "permanent" : formatDuration(punishment.getExpiration() - System.currentTimeMillis()));
        variables.put("appeal_url", localeManager.getMessage("config.appeal_url"));
        variables.put("id", punishment.getId() != null ? punishment.getId() : "Unknown");
        
        // Use public notification message for consistency
        return localeManager.getPublicNotificationMessage(ordinal, variables);
    }
    
    /**
     * Format time in a user-friendly way
     */
    public static String formatTime(java.util.Date date) {
        if (date == null) return "Never";
        
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("MM/dd/yy hh:mm:ss aa");
        return formatter.format(date);
    }
    
    /**
     * Format duration in milliseconds to human readable string
     */
    public static String formatDuration(long millis) {
        long seconds = (millis / 1000L) + 1;

        if (seconds < 1) return "0 seconds";

        long minutes = seconds / 60;
        seconds = seconds % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        long day = hours / 24;
        hours = hours % 24;
        long years = day / 365;
        day = day % 365;

        StringBuilder time = new StringBuilder();

        if (years != 0) time.append(years).append("y ");
        if (day != 0) time.append(day).append("d ");
        if (hours != 0) time.append(hours).append("h ");
        if (minutes != 0) time.append(minutes).append("m ");
        if (seconds != 0) time.append(seconds).append("s ");

        return time.toString().trim();
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