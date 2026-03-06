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

        Object enabled = data.get("enabled");
        if (enabled != null) config.enabled = Boolean.TRUE.equals(enabled);
        Object prefix = data.get("prefix");
        if (prefix != null) config.prefix = String.valueOf(prefix);
        Object format = data.get("format");
        if (format != null) config.format = String.valueOf(format);

        return config;
    }

    public String formatMessage(String inGameName, String panelName, String message) {
        return format
                .replace("{player}", inGameName)
                .replace("{panel-name}", panelName != null ? panelName : inGameName)
                .replace("{message}", message)
                .replace("&", "§");
    }
}
