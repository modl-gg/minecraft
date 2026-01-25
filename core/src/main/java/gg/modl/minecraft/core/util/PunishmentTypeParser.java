package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.modl.minecraft.api.PunishmentTypeRegistry;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;

import java.util.List;

/**
 * Utility for parsing punishment type data and populating the PunishmentTypeRegistry.
 */
public class PunishmentTypeParser {

    private static final Gson gson = new Gson();

    /**
     * Parse a list of punishment types and populate the registry.
     */
    public static void populateRegistry(List<PunishmentTypesResponse.PunishmentTypeData> types) {
        // Always register administrative types first
        PunishmentTypeRegistry.registerAdministrativeTypes();

        // Parse each punishment type
        for (PunishmentTypesResponse.PunishmentTypeData type : types) {
            int ordinal = type.getOrdinal();

            // Skip administrative types (already registered)
            if (ordinal <= 5) {
                continue;
            }

            // Check for "permanent until" types - these are bans
            if (Boolean.TRUE.equals(type.getPermanentUntilSkinChange()) ||
                Boolean.TRUE.equals(type.getPermanentUntilUsernameChange())) {
                PunishmentTypeRegistry.register(ordinal, true, false);
                continue;
            }

            // Parse durations to determine ban/mute
            Object durations = type.getDurations();
            if (durations != null) {
                DurationInfo info = parseDurations(durations);
                PunishmentTypeRegistry.register(ordinal, info.hasBan, info.hasMute);
            } else {
                // No durations - default to neither ban nor mute (could be kick-like)
                PunishmentTypeRegistry.register(ordinal, false, false);
            }
        }
    }

    /**
     * Parse the durations object to determine if any duration is a ban or mute.
     */
    private static DurationInfo parseDurations(Object durations) {
        boolean hasBan = false;
        boolean hasMute = false;

        try {
            // Convert to JSON for easier parsing
            JsonElement element = gson.toJsonTree(durations);
            if (element.isJsonObject()) {
                JsonObject durationsObj = element.getAsJsonObject();

                // Check each severity level (low, regular, severe)
                for (String severity : new String[]{"low", "regular", "severe"}) {
                    if (durationsObj.has(severity) && durationsObj.get(severity).isJsonObject()) {
                        JsonObject severityObj = durationsObj.getAsJsonObject(severity);

                        // Check each offense level (first, medium, habitual)
                        for (String offenseLevel : new String[]{"first", "medium", "habitual"}) {
                            if (severityObj.has(offenseLevel) && severityObj.get(offenseLevel).isJsonObject()) {
                                JsonObject durationDetail = severityObj.getAsJsonObject(offenseLevel);

                                if (durationDetail.has("type")) {
                                    String typeStr = durationDetail.get("type").getAsString().toLowerCase();
                                    if (typeStr.contains("ban")) {
                                        hasBan = true;
                                    }
                                    if (typeStr.contains("mute")) {
                                        hasMute = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If parsing fails, log and default to neither
            System.err.println("[MODL] Failed to parse punishment durations: " + e.getMessage());
        }

        return new DurationInfo(hasBan, hasMute);
    }

    private static class DurationInfo {
        final boolean hasBan;
        final boolean hasMute;

        DurationInfo(boolean hasBan, boolean hasMute) {
            this.hasBan = hasBan;
            this.hasMute = hasMute;
        }
    }
}
