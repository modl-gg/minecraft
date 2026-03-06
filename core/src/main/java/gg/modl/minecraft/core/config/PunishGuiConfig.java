package gg.modl.minecraft.core.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Data
public class PunishGuiConfig {
    private static final Yaml yaml = new Yaml();
    public static final int MAX_PUNISHMENT_SLOTS = 14;
    private final Map<Integer, PunishSlotConfig> slots = new HashMap<>();
    private final Map<Integer, String> itemsByOrdinal = new HashMap<>();

    @Data
    @NoArgsConstructor
    public static class PunishSlotConfig {
        private int slotNumber;
        private boolean enabled = false;
        private String item = "minecraft:paper";
        private String title = "Unknown";
        private int ordinal = 0;
        private List<String> description = new ArrayList<>();

        public PunishSlotConfig(int slotNumber, boolean enabled, String item, String title, int ordinal, List<String> description) {
            this.slotNumber = slotNumber;
            this.enabled = enabled;
            this.item = item;
            this.title = title;
            this.ordinal = ordinal;
            this.description = description != null ? description : new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    public static PunishGuiConfig load(Path dataDirectory, Logger logger) {
        PunishGuiConfig config = new PunishGuiConfig();
        try {
            loadItemsByOrdinal(config, dataDirectory, logger);
            Map<?, ?> punishGui = null;
            Path dedicatedFile = dataDirectory.resolve("punish_gui.yml");
            if (Files.exists(dedicatedFile)) {
                try (InputStream is = Files.newInputStream(dedicatedFile)) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null && data.containsKey("punish_gui")) punishGui = (Map<?, ?>) data.get("punish_gui");
                    else if (data != null) punishGui = data;
                }
                if (punishGui != null) logger.info("[PunishGuiConfig] Loaded from punish_gui.yml");
            }

            if (punishGui == null) {
                Path configFile = dataDirectory.resolve("config.yml");
                if (!Files.exists(configFile)) {
                    logger.info("[PunishGuiConfig] Config file not found, using defaults");
                    return createDefault();
                }
                try (InputStream is = Files.newInputStream(configFile)) {
                    Map<String, Object> rootConfig = yaml.load(is);
                    if (rootConfig != null && rootConfig.containsKey("punish_gui")) punishGui = (Map<?, ?>) rootConfig.get("punish_gui");
                }
            }

            if (punishGui == null) {
                logger.info("[PunishGuiConfig] No punish_gui section found, using defaults");
                return createDefault();
            }

            for (Map.Entry<?, ?> entry : punishGui.entrySet()) {
                try {
                    int slotNumber;
                    Object key = entry.getKey();
                    if (key instanceof Integer) slotNumber = (Integer) key;
                    else if (key instanceof String) slotNumber = Integer.parseInt((String) key);
                    else continue;

                    if (slotNumber < 1 || slotNumber > MAX_PUNISHMENT_SLOTS) continue;

                    Map<String, Object> slotData = (Map<String, Object>) entry.getValue();
                    PunishSlotConfig slotConfig = parseSlotConfig(slotNumber, slotData);
                    config.slots.put(slotNumber, slotConfig);
                } catch (NumberFormatException ignored) {}
            }

            logger.info("[PunishGuiConfig] Loaded " + config.slots.size() + " punishment slots from config");
        } catch (Exception e) {
            logger.warning("[PunishGuiConfig] Failed to load config: " + e.getMessage());
            return createDefault();
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private static void loadItemsByOrdinal(PunishGuiConfig config, Path dataDirectory, Logger logger) {
        try {
            Map<?, ?> itemsMap = null;

            // Primary: config.yml punishment_type_items_by_ordinal section
            Path configFile = dataDirectory.resolve("config.yml");
            if (Files.exists(configFile)) {
                try (InputStream is = Files.newInputStream(configFile)) {
                    Map<String, Object> rootConfig = yaml.load(is);
                    if (rootConfig != null && rootConfig.containsKey("punishment_type_items_by_ordinal")) {
                        itemsMap = (Map<?, ?>) rootConfig.get("punishment_type_items_by_ordinal");
                    }
                }
            }

            // Fallback: legacy punish_gui.yml
            if (itemsMap == null) {
                Path dedicatedFile = dataDirectory.resolve("punish_gui.yml");
                if (Files.exists(dedicatedFile)) {
                    try (InputStream is = Files.newInputStream(dedicatedFile)) {
                        Map<String, Object> data = yaml.load(is);
                        if (data != null && data.containsKey("punishment_type_items_by_ordinal")) {
                            itemsMap = (Map<?, ?>) data.get("punishment_type_items_by_ordinal");
                        }
                    }
                }
            }

            if (itemsMap != null) {
                for (Map.Entry<?, ?> entry : itemsMap.entrySet()) {
                    try {
                        int ordinal = entry.getKey() instanceof Integer ? (Integer) entry.getKey() : Integer.parseInt(entry.getKey().toString());
                        String itemId = entry.getValue().toString();
                        String[] parts = itemId.split(":");
                        if (parts.length > 2) itemId = parts[0] + ":" + parts[1];
                        config.itemsByOrdinal.put(ordinal, itemId);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            logger.warning("[PunishGuiConfig] Failed to load items by ordinal: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static PunishSlotConfig parseSlotConfig(int slotNumber, Map<String, Object> data) {
        PunishSlotConfig config = new PunishSlotConfig();
        config.setSlotNumber(slotNumber);

        if (data.containsKey("enabled")) config.setEnabled((Boolean) data.get("enabled"));
        if (data.containsKey("item")) config.setItem((String) data.get("item"));
        if (data.containsKey("title")) config.setTitle((String) data.get("title"));
        if (data.containsKey("ordinal")) {
            Object val = data.get("ordinal");
            if (val instanceof Number) config.setOrdinal(((Number) val).intValue());
            else if (val instanceof String) {
                try { config.setOrdinal(Integer.parseInt((String) val)); } catch (NumberFormatException ignored) {}
            }
        }
        if (data.containsKey("description")) {
            Object desc = data.get("description");
            if (desc instanceof List) config.setDescription(new ArrayList<>((List<String>) desc));
        }

        return config;
    }

    public static PunishGuiConfig createDefault() {
        PunishGuiConfig config = new PunishGuiConfig();
        config.itemsByOrdinal.put(0, "minecraft:leather_boots");
        config.itemsByOrdinal.put(1, "minecraft:paper");
        config.itemsByOrdinal.put(2, "minecraft:barrier");
        config.itemsByOrdinal.put(3, "minecraft:barrier");
        config.itemsByOrdinal.put(4, "minecraft:player_head");
        config.itemsByOrdinal.put(5, "minecraft:bedrock");
        config.itemsByOrdinal.put(6, "minecraft:feather");
        config.itemsByOrdinal.put(7, "minecraft:pufferfish");
        config.itemsByOrdinal.put(8, "minecraft:creeper_head");
        config.itemsByOrdinal.put(9, "minecraft:ink_sac");
        config.itemsByOrdinal.put(10, "minecraft:name_tag");
        config.itemsByOrdinal.put(11, "minecraft:armor_stand");
        config.itemsByOrdinal.put(12, "minecraft:lava_bucket");
        config.itemsByOrdinal.put(13, "minecraft:spider_eye");
        config.itemsByOrdinal.put(14, "minecraft:diamond_sword");
        config.itemsByOrdinal.put(15, "minecraft:gold_ingot");
        config.itemsByOrdinal.put(16, "minecraft:experience_bottle");
        config.itemsByOrdinal.put(17, "minecraft:barrier");

        config.slots.put(1, new PunishSlotConfig(1, true, "minecraft:feather", "Chat Abuse", 6,
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment.")));
        config.slots.put(2, new PunishSlotConfig(2, true, "minecraft:pufferfish", "Anti Social", 7,
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment.")));
        config.slots.put(3, new PunishSlotConfig(3, true, "minecraft:creeper_head", "Targeting", 8,
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment.")));
        config.slots.put(4, new PunishSlotConfig(4, false, "minecraft:paper", "", 0, List.of()));
        config.slots.put(5, new PunishSlotConfig(5, true, "minecraft:lava_bucket", "Team Abuse", 12,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));
        config.slots.put(6, new PunishSlotConfig(6, true, "minecraft:spider_eye", "Game Abuse", 13,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));
        config.slots.put(7, new PunishSlotConfig(7, true, "minecraft:diamond_sword", "Cheating", 14,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));
        config.slots.put(8, new PunishSlotConfig(8, true, "minecraft:ink_sac", "Bad Content", 9,
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment.")));
        config.slots.put(9, new PunishSlotConfig(9, true, "minecraft:name_tag", "Bad Username", 10,
            List.of("&7{staff-description}", "", "Click to restrict player until username is changed.")));
        config.slots.put(10, new PunishSlotConfig(10, true, "minecraft:armor_stand", "Bad Skin", 11,
            List.of("&7{staff-description}", "", "Click to restrict player until skin is changed.")));
        config.slots.put(11, new PunishSlotConfig(11, false, "minecraft:paper", "", 0, List.of()));
        config.slots.put(12, new PunishSlotConfig(12, true, "minecraft:gold_ingot", "Game Trading", 15,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));
        config.slots.put(13, new PunishSlotConfig(13, true, "minecraft:experience_bottle", "Account Abuse", 16,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));
        config.slots.put(14, new PunishSlotConfig(14, true, "minecraft:barrier", "Systems Abuse", 16,
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment.")));

        return config;
    }

    public List<PunishSlotConfig> getEnabledSlots() {
        return slots.values().stream()
                .filter(PunishSlotConfig::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getSlotNumber(), b.getSlotNumber()))
                .toList();
    }

    public PunishSlotConfig getSlot(int slotNumber) {
        return slots.get(slotNumber);
    }

    public String getItemForOrdinal(int ordinal) {
        return itemsByOrdinal.get(ordinal);
    }
}
