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
 * Maps to the standing_gui section in config.yml.
 */
@Data
public class StandingGuiConfig {
    private String title = "&8Your Standing";

    // Social status display
    private String socialItem = "minecraft:player_head";
    private String socialTitle = "&eSocial Standing: {status}";
    private Map<String, List<String>> socialDescriptions = new LinkedHashMap<>();

    // Gameplay status display
    private String gameplayItem = "minecraft:diamond_sword";
    private String gameplayTitle = "&eGameplay Standing: {status}";
    private Map<String, List<String>> gameplayDescriptions = new LinkedHashMap<>();

    // Status display name mapping (backend status -> colored display name)
    private Map<String, String> statusDisplay = new LinkedHashMap<>();

    // Punishment item display
    private String punishmentTitle = "&e#{id} {date}";
    private List<String> punishmentLore = new ArrayList<>();

    public StandingGuiConfig() {
        // Default status display names with colors
        statusDisplay.put("Low", "&aLow");
        statusDisplay.put("Medium", "&6Medium");
        statusDisplay.put("Habitual", "&cHabitual");

        // Default social descriptions
        socialDescriptions.put("Low", Arrays.asList(
                "&eYou have a few social infractions.",
                "&7Please be mindful of your behavior."));
        socialDescriptions.put("Medium", Arrays.asList(
                "&cYou have accumulated several social infractions.",
                "&7Further violations may result in longer punishments."));
        socialDescriptions.put("Habitual", Arrays.asList(
                "&4You are a habitual social offender.",
                "&7Your punishments will be severe."));

        // Default gameplay descriptions
        gameplayDescriptions.put("Low", Arrays.asList(
                "&eYou have a few gameplay infractions.",
                "&7Please be mindful of your gameplay."));
        gameplayDescriptions.put("Medium", Arrays.asList(
                "&cYou have accumulated several gameplay infractions.",
                "&7Further violations may result in longer punishments."));
        gameplayDescriptions.put("Habitual", Arrays.asList(
                "&4You are a habitual gameplay offender.",
                "&7Your punishments will be severe."));

        // Default punishment lore
        punishmentLore.addAll(Arrays.asList(
                "&7{duration} {type} ({status})",
                "",
                "&f{reason}"));
    }

    /**
     * Load the standing GUI config from config.yml.
     */
    @SuppressWarnings("unchecked")
    public static StandingGuiConfig load(Path dataDirectory, Logger logger) {
        StandingGuiConfig config = new StandingGuiConfig();
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                return config;
            }
            Yaml yaml = new Yaml();
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Map<String, Object> root = yaml.load(inputStream);
                if (root == null || !root.containsKey("standing_gui")) {
                    return config;
                }
                Map<String, Object> gui = (Map<String, Object>) root.get("standing_gui");
                if (gui == null) return config;

                if (gui.containsKey("title")) {
                    config.title = String.valueOf(gui.get("title"));
                }

                // Status display name mapping
                if (gui.containsKey("status_display")) {
                    Map<String, Object> display = (Map<String, Object>) gui.get("status_display");
                    if (display != null) {
                        config.statusDisplay = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : display.entrySet()) {
                            config.statusDisplay.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                    }
                }

                // Social status
                if (gui.containsKey("social_status")) {
                    Map<String, Object> social = (Map<String, Object>) gui.get("social_status");
                    if (social != null) {
                        if (social.containsKey("item")) config.socialItem = String.valueOf(social.get("item"));
                        if (social.containsKey("title")) config.socialTitle = String.valueOf(social.get("title"));
                        if (social.containsKey("description")) {
                            Map<String, Object> descs = (Map<String, Object>) social.get("description");
                            if (descs != null) {
                                config.socialDescriptions = new LinkedHashMap<>();
                                for (Map.Entry<String, Object> entry : descs.entrySet()) {
                                    if (entry.getValue() instanceof List) {
                                        List<String> lines = new ArrayList<>();
                                        for (Object o : (List<?>) entry.getValue()) {
                                            lines.add(String.valueOf(o));
                                        }
                                        config.socialDescriptions.put(entry.getKey(), lines);
                                    }
                                }
                            }
                        }
                    }
                }

                // Gameplay status
                if (gui.containsKey("gameplay_status")) {
                    Map<String, Object> gameplay = (Map<String, Object>) gui.get("gameplay_status");
                    if (gameplay != null) {
                        if (gameplay.containsKey("item")) config.gameplayItem = String.valueOf(gameplay.get("item"));
                        if (gameplay.containsKey("title")) config.gameplayTitle = String.valueOf(gameplay.get("title"));
                        if (gameplay.containsKey("description")) {
                            Map<String, Object> descs = (Map<String, Object>) gameplay.get("description");
                            if (descs != null) {
                                config.gameplayDescriptions = new LinkedHashMap<>();
                                for (Map.Entry<String, Object> entry : descs.entrySet()) {
                                    if (entry.getValue() instanceof List) {
                                        List<String> lines = new ArrayList<>();
                                        for (Object o : (List<?>) entry.getValue()) {
                                            lines.add(String.valueOf(o));
                                        }
                                        config.gameplayDescriptions.put(entry.getKey(), lines);
                                    }
                                }
                            }
                        }
                    }
                }

                // Punishment item
                if (gui.containsKey("punishment_item")) {
                    Map<String, Object> pItem = (Map<String, Object>) gui.get("punishment_item");
                    if (pItem != null) {
                        if (pItem.containsKey("title")) config.punishmentTitle = String.valueOf(pItem.get("title"));
                        if (pItem.containsKey("lore") && pItem.get("lore") instanceof List) {
                            config.punishmentLore = new ArrayList<>();
                            for (Object o : (List<?>) pItem.get("lore")) {
                                config.punishmentLore.add(String.valueOf(o));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load standing GUI config: " + e.getMessage());
        }
        return config;
    }

    /**
     * Get the colored display name for a status, or fall back to the raw status.
     */
    public String getStatusDisplayName(String status) {
        if (status == null) return "Unknown";
        String display = statusDisplay.get(status);
        if (display != null) return display;
        // Try case-insensitive
        for (Map.Entry<String, String> entry : statusDisplay.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(status)) {
                return entry.getValue();
            }
        }
        return status;
    }
}
