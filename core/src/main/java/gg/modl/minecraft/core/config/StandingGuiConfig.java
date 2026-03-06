package gg.modl.minecraft.core.config;

import lombok.Data;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import gg.modl.minecraft.core.util.PluginLogger;

@Data
public class StandingGuiConfig {
    private static final Yaml yaml = new Yaml();
    private String socialItem = "minecraft:creeper_head";
    private String gameplayItem = "minecraft:tnt";

    @SuppressWarnings("unchecked")
    public static StandingGuiConfig load(Path dataDirectory, PluginLogger logger) {
        StandingGuiConfig config = new StandingGuiConfig();
        try {
            Map<String, Object> gui = null;

            // Primary: config.yml standing_gui section
            Path configFile = dataDirectory.resolve("config.yml");
            if (Files.exists(configFile)) {
                try (InputStream is = Files.newInputStream(configFile)) {
                    Map<String, Object> root = yaml.load(is);
                    if (root != null && root.containsKey("standing_gui"))
                        gui = (Map<String, Object>) root.get("standing_gui");
                }
            }

            // Fallback: legacy standing_gui.yml
            if (gui == null) {
                Path dedicatedFile = dataDirectory.resolve("standing_gui.yml");
                if (Files.exists(dedicatedFile)) {
                    try (InputStream is = Files.newInputStream(dedicatedFile)) {
                        Map<String, Object> data = yaml.load(is);
                        if (data != null && data.containsKey("standing_gui"))
                            gui = (Map<String, Object>) data.get("standing_gui");
                        else if (data != null) gui = data;
                    }
                }
            }

            if (gui == null) return config;

            if (gui.containsKey("social_status")) {
                Map<String, Object> social = (Map<String, Object>) gui.get("social_status");
                if (social != null && social.containsKey("item")) config.socialItem = String.valueOf(social.get("item"));
            }

            if (gui.containsKey("gameplay_status")) {
                Map<String, Object> gameplay = (Map<String, Object>) gui.get("gameplay_status");
                if (gameplay != null && gameplay.containsKey("item")) config.gameplayItem = String.valueOf(gameplay.get("item"));
            }
        } catch (Exception e) {
            logger.warning("Failed to load standing GUI config: " + e.getMessage());
        }
        return config;
    }
}
