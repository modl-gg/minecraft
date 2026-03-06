package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.modl.minecraft.api.PunishmentTypeRegistry;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;

import java.util.List;

public class PunishmentTypeParser {

    private static final Gson gson = new Gson();
    private static final int MAX_ADMINISTRATIVE_ORDINAL = 5;
    private static final String[] SEVERITY_LEVELS = {"low", "regular", "severe"};
    private static final String[] OFFENSE_LEVELS = {"first", "medium", "habitual"};

    public static void populateRegistry(List<PunishmentTypesResponse.PunishmentTypeData> types) {
        PunishmentTypeRegistry.registerAdministrativeTypes();

        for (PunishmentTypesResponse.PunishmentTypeData type : types) {
            int ordinal = type.getOrdinal();
            if (ordinal <= MAX_ADMINISTRATIVE_ORDINAL) continue;

            if (Boolean.TRUE.equals(type.getPermanentUntilSkinChange()) ||
                Boolean.TRUE.equals(type.getPermanentUntilUsernameChange())) {
                PunishmentTypeRegistry.register(ordinal, true, false);
                continue;
            }

            Object durations = type.getDurations();
            if (durations != null) {
                DurationInfo info = parseDurations(durations);
                PunishmentTypeRegistry.register(ordinal, info.hasBan, info.hasMute);
            } else {
                PunishmentTypeRegistry.register(ordinal, false, false);
            }
        }
    }

    private static DurationInfo parseDurations(Object durations) {
        boolean hasBan = false;
        boolean hasMute = false;

        try {
            JsonElement element = gson.toJsonTree(durations);
            if (!element.isJsonObject()) return new DurationInfo(false, false);

            JsonObject durationsObj = element.getAsJsonObject();
            for (String severity : SEVERITY_LEVELS) {
                if (!durationsObj.has(severity) || !durationsObj.get(severity).isJsonObject()) continue;
                JsonObject severityObj = durationsObj.getAsJsonObject(severity);

                for (String offenseLevel : OFFENSE_LEVELS) {
                    if (!severityObj.has(offenseLevel) || !severityObj.get(offenseLevel).isJsonObject()) continue;
                    JsonObject durationDetail = severityObj.getAsJsonObject(offenseLevel);

                    if (durationDetail.has("type")) {
                        String typeStr = durationDetail.get("type").getAsString().toLowerCase();
                        if (typeStr.contains("ban")) hasBan = true;
                        if (typeStr.contains("mute")) hasMute = true;
                    }
                }
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger("modl").warning("Failed to parse punishment durations: " + e.getMessage());
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
