package gg.modl.minecraft.core.config;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

@Getter
public class StaffChatConfig {
    private boolean enabled = true;
    private String prefix = "!";
    private String format = "&b[Staff] &f{player}: &7{message}";

    public static StaffChatConfig load(Path dataFolder, Logger logger) {
        StaffChatConfig config = new StaffChatConfig();
        Map<String, Object> data = ConfigManager.loadSection(dataFolder, "staff_chat.yml", "staff_chat", logger);
        if (data == null) return config;

        if (data.containsKey("enabled")) config.enabled = Boolean.TRUE.equals(data.get("enabled"));
        if (data.containsKey("prefix")) config.prefix = String.valueOf(data.get("prefix"));
        if (data.containsKey("format")) config.format = String.valueOf(data.get("format"));

        return config;
    }
}
