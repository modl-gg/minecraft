package gg.modl.minecraft.core.config;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

@Getter
public class ChatManagementConfig {
    private int clearLines = 100;

    public static ChatManagementConfig load(Path dataFolder, Logger logger) {
        ChatManagementConfig config = new ChatManagementConfig();
        Map<String, Object> data = ConfigManager.loadSection(dataFolder, "chat_management.yml", "chat_management", logger);
        if (data == null) return config;

        if (data.containsKey("clear_lines")) config.clearLines = ((Number) data.get("clear_lines")).intValue();

        return config;
    }
}
