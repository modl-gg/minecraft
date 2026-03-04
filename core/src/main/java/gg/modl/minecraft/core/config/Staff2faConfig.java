package gg.modl.minecraft.core.config;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

@Getter
public class Staff2faConfig {
    private boolean enabled = false;
    private int ipTtlDays = 7;

    public static Staff2faConfig load(Path dataFolder, Logger logger) {
        Staff2faConfig config = new Staff2faConfig();
        Map<String, Object> data = ConfigManager.loadSection(dataFolder, "staff_2fa.yml", "staff_2fa", logger);
        if (data == null) return config;

        if (data.containsKey("enabled")) config.enabled = Boolean.TRUE.equals(data.get("enabled"));
        if (data.containsKey("ip_ttl_days")) config.ipTtlDays = ((Number) data.get("ip_ttl_days")).intValue();
        // Backward compatibility: migrate old minutes config
        else if (data.containsKey("ip_ttl_minutes")) config.ipTtlDays = Math.max(1, ((Number) data.get("ip_ttl_minutes")).intValue() / 1440);

        return config;
    }
}
