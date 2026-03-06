package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentTypeCacheManager {

    private final Map<Integer, String> namesByOrdinal = new ConcurrentHashMap<>();
    private final Map<String, String> namesById = new ConcurrentHashMap<>();

    public void initialize(ModlHttpClient httpClient, PluginLogger logger) {
        httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess()) update(response.getData());
        }).exceptionally(throwable -> {
            if (logger != null) logger.debug("Error loading punishment types for cache: " + throwable.getMessage());
            return null;
        });
    }

    public void update(List<PunishmentTypesResponse.PunishmentTypeData> allTypes) {
        namesByOrdinal.clear();
        namesById.clear();
        allTypes.forEach(pt -> {
            namesByOrdinal.put(pt.getOrdinal(), pt.getName());
            namesById.put(String.valueOf(pt.getId()), pt.getName());
            namesById.put(String.valueOf(pt.getOrdinal()), pt.getName());
        });
    }

    public String getNameByOrdinal(int ordinal) {
        String name = namesByOrdinal.get(ordinal);
        if (name != null) return name;
        return switch (ordinal) {
            case 0 -> "Kick";
            case 1 -> "Mute";
            case 2 -> "Ban";
            case 3 -> "Security Ban";
            case 4 -> "Linked Ban";
            case 5 -> "Blacklist";
            default -> "Unknown";
        };
    }

    public String getNameById(String typeId) {
        String name = namesById.get(typeId);
        return name != null ? name : "Unknown";
    }

}
