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
public class ReportGuiConfig {
    private static final Yaml yaml = new Yaml();

    private final Map<Integer, ReportSlotConfig> slots = new HashMap<>();
    private InfoConfig infoConfig;

    @Data @NoArgsConstructor
    public static class ReportSlotConfig {
        private int slotNumber;
        private boolean enabled = false;
        private String item = "minecraft:paper";
        private String title = "Unknown";
        private boolean chatReport = false;
        private List<String> description = new ArrayList<>();

        public ReportSlotConfig(int slotNumber, boolean enabled, String item, String title, boolean chatReport, List<String> description) {
            this.slotNumber = slotNumber;
            this.enabled = enabled;
            this.item = item;
            this.title = title;
            this.chatReport = chatReport;
            this.description = description != null ? description : new ArrayList<>();
        }
    }

    @Data @NoArgsConstructor
    public static class InfoConfig {
        private String item = "minecraft:oak_sign";
        private String title = "&eInformation";
        private List<String> description = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static ReportGuiConfig load(Path dataDirectory, PluginLogger logger) {
        ReportGuiConfig config = new ReportGuiConfig();
        try {
            Map<?, ?> reportGui = null;
            Path dedicatedFile = dataDirectory.resolve("report_gui.yml");
            if (Files.exists(dedicatedFile)) {
                try (InputStream is = Files.newInputStream(dedicatedFile)) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null && data.containsKey("report_gui")) reportGui = (Map<?, ?>) data.get("report_gui");
                    else if (data != null) reportGui = data;
                }
                if (reportGui != null) logger.info("Loaded from report_gui.yml");
            }

            if (reportGui == null) {
                Path configFile = dataDirectory.resolve("config.yml");
                if (!Files.exists(configFile)) {
                    logger.info("Report GUI config file not found, using defaults");
                    return createDefault();
                }
                try (InputStream inputStream = Files.newInputStream(configFile)) {
                    Map<String, Object> rootConfig = yaml.load(inputStream);
                    if (rootConfig != null && rootConfig.containsKey("report_gui")) reportGui = (Map<?, ?>) rootConfig.get("report_gui");
                }
            }

            if (reportGui != null) {
                for (Map.Entry<?, ?> entry : reportGui.entrySet()) {
                    Object key = entry.getKey();

                    if ("info".equals(key)) {
                        Map<String, Object> infoData = (Map<String, Object>) entry.getValue();
                        config.infoConfig = parseInfoConfig(infoData);
                        continue;
                    }

                    try {
                        int slotNumber;
                        if (key instanceof Integer) slotNumber = (Integer) key;
                        else if (key instanceof String) slotNumber = Integer.parseInt((String) key);
                        else continue;

                        if (slotNumber < 1 || slotNumber > 14) continue;

                        Map<String, Object> slotData = (Map<String, Object>) entry.getValue();
                        ReportSlotConfig slotConfig = parseSlotConfig(slotNumber, slotData);
                        config.slots.put(slotNumber, slotConfig);
                    } catch (NumberFormatException ignored) {}
                }

                if (config.infoConfig == null) config.infoConfig = new InfoConfig();

                logger.info("Loaded " + config.slots.size() + " report slots from config");
            } else {
                logger.info("No report_gui section found, using defaults");
                return createDefault();
            }
        } catch (Exception e) {
            logger.warning("Failed to load config: " + e.getMessage());
            return createDefault();
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private static ReportSlotConfig parseSlotConfig(int slotNumber, Map<String, Object> data) {
        ReportSlotConfig config = new ReportSlotConfig();
        config.setSlotNumber(slotNumber);

        if (data.containsKey("enabled")) config.setEnabled((Boolean) data.get("enabled"));
        if (data.containsKey("item")) config.setItem((String) data.get("item"));
        if (data.containsKey("title")) config.setTitle((String) data.get("title"));
        if (data.containsKey("chat-report")) config.setChatReport((Boolean) data.get("chat-report"));
        if (data.containsKey("description")) {
            Object desc = data.get("description");
            if (desc instanceof List) config.setDescription(new ArrayList<>((List<String>) desc));
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private static InfoConfig parseInfoConfig(Map<String, Object> data) {
        InfoConfig config = new InfoConfig();
        if (data.containsKey("item")) config.setItem((String) data.get("item"));
        if (data.containsKey("title")) config.setTitle((String) data.get("title"));
        if (data.containsKey("description")) {
            Object desc = data.get("description");
            if (desc instanceof List) config.setDescription(new ArrayList<>((List<String>) desc));
        }
        return config;
    }

    public static ReportGuiConfig createDefault() {
        ReportGuiConfig config = new ReportGuiConfig();

        config.slots.put(1, new ReportSlotConfig(1, false, "minecraft:paper", "", false, List.of()));
        config.slots.put(2, new ReportSlotConfig(2, true, "minecraft:feather", "Chat Abuse", true,
            List.of("&7Low-quality chat, spam, or inappropriate language", "", "&eClick to report")));
        config.slots.put(3, new ReportSlotConfig(3, true, "minecraft:pufferfish", "Anti-social", true,
            List.of("&7Harassment, threats, or bullying", "", "&eClick to report")));
        config.slots.put(4, new ReportSlotConfig(4, true, "minecraft:name_tag", "Inappropriate Username/Skin", false,
            List.of("&7Offensive username or skin", "", "&eClick to report")));
        config.slots.put(5, new ReportSlotConfig(5, true, "minecraft:diamond_sword", "Cheating", false,
            List.of("&7Hacking or using unfair modifications", "", "&eClick to report")));
        config.slots.put(6, new ReportSlotConfig(6, true, "minecraft:book", "Game Rule Violation", false,
            List.of("&7Violating server-specific game rules", "", "&eClick to report")));
        config.slots.put(7, new ReportSlotConfig(7, false, "minecraft:paper", "", false, List.of()));
        config.slots.put(8, new ReportSlotConfig(8, false, "minecraft:paper", "", false, List.of()));
        config.slots.put(9, new ReportSlotConfig(9, true, "minecraft:tnt", "Exploits", false,
            List.of("&7Abusing bugs or exploits", "", "&eClick to report")));
        config.slots.put(10, new ReportSlotConfig(10, true, "minecraft:experience_bottle", "Stats Boosting", false,
            List.of("&7Artificially inflating stats", "", "&eClick to report")));
        config.slots.put(11, new ReportSlotConfig(11, true, "minecraft:paper", "Other", false,
            List.of("&7Any other rule violation", "", "&eClick to report")));
        config.slots.put(12, new ReportSlotConfig(12, false, "minecraft:paper", "", false, List.of()));
        config.slots.put(13, new ReportSlotConfig(13, false, "minecraft:paper", "", false, List.of()));
        config.slots.put(14, new ReportSlotConfig(14, false, "minecraft:paper", "", false, List.of()));

        InfoConfig infoConfig = new InfoConfig();
        infoConfig.setItem("minecraft:oak_sign");
        infoConfig.setTitle("&eInformation");
        infoConfig.setDescription(List.of(
            "&7Please review the server rules",
            "&7before submitting a report.",
            "",
            "&7False reports may result in",
            "&7action against your account."
        ));
        config.setInfoConfig(infoConfig);

        return config;
    }

    public ReportSlotConfig getSlot(int slotNumber) {
        return slots.get(slotNumber);
    }
}
