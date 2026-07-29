package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Map;

public final class PlayerNotificationMapper {
    private final PluginLogger logger;

    public PlayerNotificationMapper(PluginLogger logger) {
        this.logger = logger;
    }

    public SyncResponse.PlayerNotification mapToPlayerNotification(Map<String, Object> data) {
        try {
            Long timestamp = data.get("timestamp") instanceof Number ? ((Number) data.get("timestamp")).longValue() : null;

            Map<String, Object> dataMap = null;
            Object nestedData = data.get("data");
            if (nestedData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) nestedData;
                dataMap = casted;
            }

            return new SyncResponse.PlayerNotification(
                    (String) data.get("id"),
                    (String) data.get("message"),
                    (String) data.get("type"),
                    (String) data.get("targetPlayerUuid"),
                    dataMap,
                    timestamp);
        } catch (Exception e) {
            logger.warning("Failed to convert notification data: " + e.getMessage());
            return null;
        }
    }
}
