package gg.modl.minecraft.core.config;

import lombok.AllArgsConstructor;
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

    private final Map<Integer, ReportSlotConfig> slots = new HashMap<>();
    private InfoConfig infoConfig;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ReportSlotConfig {
        private String item = "minecraft:paper", title = "Unknown";
        private List<String> description = new ArrayList<>();
        private int slotNumber;
        private boolean enabled = false, chatReport = false, replayCapture = false;
    }

    @Data @NoArgsConstructor
    public static class InfoConfig {
        private String item = "minecraft:oak_sign", title = "&eInformation";
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
                    Yaml yaml = new Yaml();
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
                    Yaml yaml = new Yaml();
                    Map<String, Object> rootConfig = yaml.load(inputStream);
                    if (rootConfig != null && rootConfig.containsKey("report_gui")) {
                        reportGui = (Map<?, ?>) rootConfig.get("report_gui");
                    }
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
        if (data.containsKey("replay-capture")) config.setReplayCapture((Boolean) data.get("replay-capture"));
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

        config.slots.put(1, new ReportSlotConfig("minecraft:paper", "", List.of(), 1, false, false, false));
        config.slots.put(2, new ReportSlotConfig("minecraft:feather", "Chat Abuse",
            List.of("&7Low-quality chat, spam, or inappropriate language", "", "&eClick to report"), 2, true, true, false));
        config.slots.put(3, new ReportSlotConfig("minecraft:pufferfish", "Anti-social",
            List.of("&7Harassment, threats, or bullying", "", "&eClick to report"), 3, true, true, false));
        config.slots.put(4, new ReportSlotConfig("minecraft:name_tag", "Inappropriate Username/Skin",
            List.of("&7Offensive username or skin", "", "&eClick to report"), 4, true, false, false));
        config.slots.put(5, new ReportSlotConfig("minecraft:diamond_sword", "Cheating",
            List.of("&7Hacking or using unfair modifications", "", "&eClick to report"), 5, true, false, true));
        config.slots.put(6, new ReportSlotConfig("minecraft:book", "Game Rule Violation",
            List.of("&7Violating server-specific game rules", "", "&eClick to report"), 6, true, false, true));
        config.slots.put(7, new ReportSlotConfig("minecraft:paper", "", List.of(), 7, false, false, false));
        config.slots.put(8, new ReportSlotConfig("minecraft:paper", "", List.of(), 8, false, false, false));
        config.slots.put(9, new ReportSlotConfig("minecraft:tnt", "Exploits",
            List.of("&7Abusing bugs or exploits", "", "&eClick to report"), 9, true, false, true));
        config.slots.put(10, new ReportSlotConfig("minecraft:experience_bottle", "Stats Boosting",
            List.of("&7Artificially inflating stats", "", "&eClick to report"), 10, true, false, true));
        config.slots.put(11, new ReportSlotConfig("minecraft:paper", "Other",
            List.of("&7Any other rule violation", "", "&eClick to report"), 11, true, false, false));
        config.slots.put(12, new ReportSlotConfig("minecraft:paper", "", List.of(), 12, false, false, false));
        config.slots.put(13, new ReportSlotConfig("minecraft:paper", "", List.of(), 13, false, false, false));
        config.slots.put(14, new ReportSlotConfig("minecraft:paper", "", List.of(), 14, false, false, false));

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
