package gg.modl.minecraft.core.config;

import lombok.Getter;

import gg.modl.minecraft.core.util.PluginLogger;

import java.nio.file.Path;
import java.util.Map;

@Getter
public class ChatManagementConfig {
    private int clearLines = 100;

    public static ChatManagementConfig load(Path dataFolder, PluginLogger logger) {
        ChatManagementConfig config = new ChatManagementConfig();
        Map<String, Object> data = ConfigManager.loadSection(dataFolder, "chat_management.yml", "chat_management", logger);
        if (data == null) return config;

        if (data.containsKey("clear_lines")) {
            Object val = data.get("clear_lines");
            if (val instanceof Number) config.clearLines = ((Number) val).intValue();
            else if (val instanceof String) {
                try { config.clearLines = Integer.parseInt((String) val); } catch (NumberFormatException ignored) {}
            }
        }

        return config;
    }
}
