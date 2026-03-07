package gg.modl.minecraft.core.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import gg.modl.minecraft.core.util.PluginLogger;

@Data
public class PunishGuiConfig {
    public static final int MAX_PUNISHMENT_SLOTS = 14;
    private final Map<Integer, PunishSlotConfig> slots = new HashMap<>();

    @Data @NoArgsConstructor
    public static class PunishSlotConfig {
        private String item = "minecraft:paper", title = "Unknown";
        private List<String> description = new ArrayList<>();
        private int slotNumber, ordinal = 0;
        private boolean enabled = false;

        public PunishSlotConfig(String item, String title, List<String> description, int slotNumber, boolean enabled, int ordinal) {
            this.item = item;
            this.title = title;
            this.description = description != null ? description : new ArrayList<>();
            this.slotNumber = slotNumber;
            this.enabled = enabled;
            this.ordinal = ordinal;
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
                    Yaml yaml = new Yaml();
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
                    Yaml yaml = new Yaml();
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

        config.slots.put(1, new PunishSlotConfig("minecraft:feather", "Chat Abuse",
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment."), 1, true, 6));
        config.slots.put(2, new PunishSlotConfig("minecraft:pufferfish", "Anti Social",
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment."), 2, true, 7));
        config.slots.put(3, new PunishSlotConfig("minecraft:creeper_head", "Targeting",
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment."), 3, true, 8));
        config.slots.put(4, new PunishSlotConfig("minecraft:paper", "", List.of(), 4, false, 0));
        config.slots.put(5, new PunishSlotConfig("minecraft:lava_bucket", "Team Abuse",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 5, true, 12));
        config.slots.put(6, new PunishSlotConfig("minecraft:spider_eye", "Game Abuse",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 6, true, 13));
        config.slots.put(7, new PunishSlotConfig("minecraft:diamond_sword", "Cheating",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 7, true, 14));
        config.slots.put(8, new PunishSlotConfig("minecraft:ink_sac", "Bad Content",
            List.of("&7{staff-description}", "", "&fSocial Offender Level: {social-status}", "", "Click to issue new punishment."), 8, true, 9));
        config.slots.put(9, new PunishSlotConfig("minecraft:name_tag", "Bad Username",
            List.of("&7{staff-description}", "", "Click to restrict player until username is changed."), 9, true, 10));
        config.slots.put(10, new PunishSlotConfig("minecraft:armor_stand", "Bad Skin",
            List.of("&7{staff-description}", "", "Click to restrict player until skin is changed."), 10, true, 11));
        config.slots.put(11, new PunishSlotConfig("minecraft:paper", "", List.of(), 11, false, 0));
        config.slots.put(12, new PunishSlotConfig("minecraft:gold_ingot", "Game Trading",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 12, true, 15));
        config.slots.put(13, new PunishSlotConfig("minecraft:experience_bottle", "Account Abuse",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 13, true, 16));
        config.slots.put(14, new PunishSlotConfig("minecraft:barrier", "Systems Abuse",
            List.of("&7{staff-description}", "", "&fGameplay Offender Level: {gameplay-status}", "", "Click to issue new punishment."), 14, true, 17));

        return config;
    }

    public List<PunishSlotConfig> getEnabledSlots() {
        return slots.values().stream()
                .filter(PunishSlotConfig::isEnabled)
                .sorted(Comparator.comparingInt(PunishSlotConfig::getSlotNumber))
                .toList();
    }

    public PunishSlotConfig getSlot(int slotNumber) {
        return slots.get(slotNumber);
    }
}
