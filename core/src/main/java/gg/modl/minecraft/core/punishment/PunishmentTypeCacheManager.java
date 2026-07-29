package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.api.PunishmentTypeClassifier;
import gg.modl.minecraft.api.PunishmentTypeClassifiers;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PunishmentTypeCacheManager {
    private volatile Map<Integer, String> namesByOrdinal = Collections.emptyMap();
    private volatile Map<String, Integer> ordinalsByName = Collections.emptyMap();

    public void initialize(ModlHttpClient httpClient, PluginLogger logger) {
        httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) update(response.getData());
        }).exceptionally(throwable -> {
            if (logger != null) logger.debug("Error loading punishment types for cache: " + throwable.getMessage());
            return null;
        });
    }

    public void update(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        if (allTypes == null) return;
        Map<Integer, String> newNamesByOrdinal = new HashMap<>();
        Map<String, Integer> newOrdinalsByName = new HashMap<>();
        allTypes.forEach(pt -> {
            newNamesByOrdinal.put(pt.getOrdinal(), pt.getName());
            if (pt.getName() != null) newOrdinalsByName.put(pt.getName().toLowerCase(), pt.getOrdinal());
        });
        namesByOrdinal = newNamesByOrdinal;
        ordinalsByName = newOrdinalsByName;
    }

    public boolean isBanType(String typeName) {
        if (typeName == null) return false;
        Integer ordinal = ordinalsByName.get(typeName.toLowerCase());
        if (ordinal != null) return PunishmentTypeClassifiers.active().isBan(ordinal);
        String lower = typeName.toLowerCase();
        return lower.contains("ban") || lower.equals("blacklist");
    }

    public boolean isMuteType(String typeName) {
        if (typeName == null) return false;
        Integer ordinal = ordinalsByName.get(typeName.toLowerCase());
        if (ordinal != null) return PunishmentTypeClassifiers.active().isMute(ordinal);
        String lower = typeName.toLowerCase();
        return lower.contains("mute") || lower.equals("silence");
    }

    public String getNameByOrdinal(int ordinal) {
        String name = namesByOrdinal.get(ordinal);
        if (name != null) return name;
        switch (ordinal) {
            case PunishmentTypeClassifier.ORDINAL_KICK: return "Kick";
            case PunishmentTypeClassifier.ORDINAL_MUTE: return "Mute";
            case PunishmentTypeClassifier.ORDINAL_BAN: return "Ban";
            case PunishmentTypeClassifier.ORDINAL_SECURITY_BAN: return "Security Ban";
            case PunishmentTypeClassifier.ORDINAL_LINKED_BAN: return "Linked Ban";
            case PunishmentTypeClassifier.ORDINAL_BLACKLIST: return "Blacklist";
            default: return "Unknown";
        }
    }

}
