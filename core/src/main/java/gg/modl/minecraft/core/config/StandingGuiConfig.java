package gg.modl.minecraft.core.config;

import lombok.Data;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Configuration for the standing GUI menu.
 * Only holds structural config (item types). All text content is in locale files.
 */
@Data
public class StandingGuiConfig {
    // Social status item type
    private String socialItem = "minecraft:creeper_head";

    // Gameplay status item type
    private String gameplayItem = "minecraft:tnt";

    /**
     * Load the standing GUI config from standing_gui.yml first, falling back to config.yml.
     */
    @SuppressWarnings("unchecked")
    public static StandingGuiConfig load(Path dataDirectory, Logger logger) {
        StandingGuiConfig config = new StandingGuiConfig();
        try {
            Map<String, Object> gui = null;

            // Try dedicated standing_gui.yml first
            Path dedicatedFile = dataDirectory.resolve("standing_gui.yml");
            if (Files.exists(dedicatedFile)) {
                Yaml yaml = new Yaml();
                try (InputStream is = Files.newInputStream(dedicatedFile)) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null && data.containsKey("standing_gui")) {
                        gui = (Map<String, Object>) data.get("standing_gui");
                    } else if (data != null) {
                        gui = data;
                    }
                }
            }

            // Fall back to config.yml
            if (gui == null) {
                Path configFile = dataDirectory.resolve("config.yml");
                if (!Files.exists(configFile)) return config;
                Yaml yaml = new Yaml();
                try (InputStream inputStream = Files.newInputStream(configFile)) {
                    Map<String, Object> root = yaml.load(inputStream);
                    if (root == null || !root.containsKey("standing_gui")) return config;
                    gui = (Map<String, Object>) root.get("standing_gui");
                }
            }

            if (gui == null) return config;

            // Social status item type
            if (gui.containsKey("social_status")) {
                Map<String, Object> social = (Map<String, Object>) gui.get("social_status");
                if (social != null && social.containsKey("item")) {
                    config.socialItem = String.valueOf(social.get("item"));
                }
            }

            // Gameplay status item type
            if (gui.containsKey("gameplay_status")) {
                Map<String, Object> gameplay = (Map<String, Object>) gui.get("gameplay_status");
                if (gameplay != null && gameplay.containsKey("item")) {
                    config.gameplayItem = String.valueOf(gameplay.get("item"));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load standing GUI config: " + e.getMessage());
        }
        return config;
    }
}
