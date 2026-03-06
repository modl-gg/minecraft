package gg.modl.minecraft.core.config;

import lombok.Getter;

import gg.modl.minecraft.core.util.PluginLogger;

import java.nio.file.Path;
import java.util.Map;

@Getter
public class Staff2faConfig {
    private boolean enabled = false;

    public static Staff2faConfig load(Path dataFolder, PluginLogger logger) {
        Staff2faConfig config = new Staff2faConfig();
        Map<String, Object> data = ConfigManager.loadSection(dataFolder, "staff_2fa.yml", "staff_2fa", logger);
        if (data == null) return config;

        if (data.containsKey("enabled")) config.enabled = Boolean.TRUE.equals(data.get("enabled"));

        return config;
    }
}
