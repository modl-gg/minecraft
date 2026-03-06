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
import gg.modl.minecraft.core.util.PluginLogger;

@Data
public class PunishGuiConfig {
    private static final Yaml yaml = new Yaml();
    public static final int MAX_PUNISHMENT_SLOTS = 14;
    private final Map<Integer, PunishSlotConfig> slots = new HashMap<>();

    @Data @NoArgsConstructor
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
    public static PunishGuiConfig load(Path dataDirectory, PluginLogger logger) {
        PunishGuiConfig config = new PunishGuiConfig();
        try {
            Map<?, ?> punishGui = null;
            Path dedicatedFile = dataDirectory.resolve("punish_gui.yml");
            if (Files.exists(dedicatedFile)) {
                try (InputStream is = Files.newInputStream(dedicatedFile)) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null && data.containsKey("punish_gui")) punishGui = (Map<?, ?>) data.get("punish_gui");
                    else if (data != null) punishGui = data;
                }
                if (punishGui != null) logger.info("Loaded from punish_gui.yml");
            }

            if (punishGui == null) {
                Path configFile = dataDirectory.resolve("config.yml");
                if (!Files.exists(configFile)) {
                    logger.info("Punish GUI config file not found, using defaults");
                    return createDefault();
                }
                try (InputStream is = Files.newInputStream(configFile)) {
                    Map<String, Object> rootConfig = yaml.load(is);
                    if (rootConfig != null && rootConfig.containsKey("punish_gui")) punishGui = (Map<?, ?>) rootConfig.get("punish_gui");
                }
            }

            if (punishGui == null) {
                logger.info("No punish_gui section found, using defaults");
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

            logger.info("Loaded " + config.slots.size() + " punishment slots from config");
        } catch (Exception e) {
            logger.warning("Failed to load config: " + e.getMessage());
            return createDefault();
        }

        return config;
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
}
