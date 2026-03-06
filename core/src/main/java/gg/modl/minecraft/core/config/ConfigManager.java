package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final Yaml yaml = new Yaml();

    private final Path dataFolder;
    private final PluginLogger logger;

    @Getter private PunishGuiConfig punishGuiConfig;
    @Getter private ReportGuiConfig reportGuiConfig;
    @Getter private StandingGuiConfig standingGuiConfig;
    @Getter private StaffChatConfig staffChatConfig;
    @Getter private ChatManagementConfig chatManagementConfig;
    @Getter private Staff2faConfig staff2faConfig;
    @Getter private Map<Integer, String> punishmentTypeItems;

    @Getter
    public static class StaffChatConfig {
        private boolean enabled = true;
        private String prefix = "!";
        private String format = "&b[Staff] &f{player}: &7{message}";

        public String formatMessage(String inGameName, String panelName, String message) {
            return format
                .replace("{player}", inGameName)
                .replace("{panel-name}", panelName != null ? panelName : inGameName)
                .replace("{message}", message)
                .replace("&", "§");
        }
    }

    @Getter
    public static class ChatManagementConfig {
        private int clearLines = 100;
    }

    @Getter
    public static class Staff2faConfig {
        private boolean enabled = false;
    }

    @Getter
    public static class StandingGuiConfig {
        private String socialItem = "minecraft:creeper_head";
        private String gameplayItem = "minecraft:tnt";
    }
    private static final String[] GUI_CONFIG_FILES = {
            "punish_gui.yml", "report_gui.yml"
    };

    public ConfigManager(Path dataFolder, PluginLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        migrateGuiConfigsFromConfigYml();
        createDefaultGuiConfigFiles();
        mergeGuiConfigDefaults();
        reloadAll();
    }

    public void reloadAll() {
        punishGuiConfig = PunishGuiConfig.load(dataFolder, logger);
        reportGuiConfig = ReportGuiConfig.load(dataFolder, logger);
        standingGuiConfig = loadStandingGuiConfig();
        staffChatConfig = loadStaffChatConfig();
        chatManagementConfig = loadChatManagementConfig();
        staff2faConfig = loadStaff2faConfig();
        punishmentTypeItems = loadPunishmentTypeItems();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, String> loadPunishmentTypeItems() {
        Map<Integer, String> items = new HashMap<>(getDefaultPunishmentTypeItems());
        try {
            Map<?, ?> itemsMap = null;
            Path configFile = dataFolder.resolve("config.yml");
            if (Files.exists(configFile)) try (InputStream is = Files.newInputStream(configFile)) {
                Map<String, Object> root = yaml.load(is);
                if (root != null && root.containsKey("punishment_type_items_by_ordinal"))
                    itemsMap = (Map<?, ?>) root.get("punishment_type_items_by_ordinal");
            }

            if (itemsMap != null) for (Map.Entry<?, ?> entry : itemsMap.entrySet())
                try {
                    int ordinal = entry.getKey() instanceof Integer ? (Integer) entry.getKey() : Integer.parseInt(entry.getKey().toString());
                    String itemId = entry.getValue().toString();
                    String[] parts = itemId.split(":");
                    if (parts.length > 2) itemId = parts[0] + ":" + parts[1];
                    items.put(ordinal, itemId);
                } catch (NumberFormatException ignored) {}

        } catch (Exception e) { logger.warning("Failed to load punishment type items: " + e.getMessage()); }
        return items;
    }

    private StaffChatConfig loadStaffChatConfig() {
        StaffChatConfig config = new StaffChatConfig();
        Map<String, Object> data = loadSection(dataFolder, "staff_chat.yml", "staff_chat", logger);
        if (data == null) return config;

        Object enabled = data.get("enabled");
        if (enabled != null) config.enabled = Boolean.TRUE.equals(enabled);
        Object prefix = data.get("prefix");
        if (prefix != null) config.prefix = String.valueOf(prefix);
        Object format = data.get("format");
        if (format != null) config.format = String.valueOf(format);

        return config;
    }

    private ChatManagementConfig loadChatManagementConfig() {
        ChatManagementConfig config = new ChatManagementConfig();
        Map<String, Object> data = loadSection(dataFolder, "chat_management.yml", "chat_management", logger);
        if (data == null) return config;

        if (data.containsKey("clear_lines")) {
            Object val = data.get("clear_lines");
            if (val instanceof Number) config.clearLines = ((Number) val).intValue();
            else if (val instanceof String)
                try { config.clearLines = Integer.parseInt((String) val); } catch (NumberFormatException ignored) {}
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private StandingGuiConfig loadStandingGuiConfig() {
        StandingGuiConfig config = new StandingGuiConfig();
        try {
            Map<String, Object> gui = null;

            Path configFile = dataFolder.resolve("config.yml");
            if (Files.exists(configFile)) try (InputStream is = Files.newInputStream(configFile)) {
                Map<String, Object> root = yaml.load(is);
                if (root != null && root.containsKey("standing_gui"))
                    gui = (Map<String, Object>) root.get("standing_gui");
            }


            if (gui == null) {
                Path dedicatedFile = dataFolder.resolve("standing_gui.yml");
                if (Files.exists(dedicatedFile)) try (InputStream is = Files.newInputStream(dedicatedFile)) {
                    Map<String, Object> data = yaml.load(is);
                    if (data != null && data.containsKey("standing_gui"))
                        gui = (Map<String, Object>) data.get("standing_gui");
                    else if (data != null) gui = data;
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
        } catch (Exception e) { logger.warning("Failed to load standing GUI config: " + e.getMessage()); }
        return config;
    }

    private Staff2faConfig loadStaff2faConfig() {
        Staff2faConfig config = new Staff2faConfig();
        Map<String, Object> data = loadSection(dataFolder, "staff_2fa.yml", "staff_2fa", logger);
        if (data == null) return config;

        if (data.containsKey("enabled")) config.enabled = Boolean.TRUE.equals(data.get("enabled"));

        return config;
    }

    public static Map<Integer, String> getDefaultPunishmentTypeItems() {
        Map<Integer, String> items = new HashMap<>();
        items.put(0, "minecraft:leather_boots");
        items.put(1, "minecraft:paper");
        items.put(2, "minecraft:barrier");
        items.put(3, "minecraft:barrier");
        items.put(4, "minecraft:player_head");
        items.put(5, "minecraft:bedrock");
        items.put(6, "minecraft:feather");
        items.put(7, "minecraft:pufferfish");
        items.put(8, "minecraft:creeper_head");
        items.put(9, "minecraft:ink_sac");
        items.put(10, "minecraft:name_tag");
        items.put(11, "minecraft:armor_stand");
        items.put(12, "minecraft:lava_bucket");
        items.put(13, "minecraft:spider_eye");
        items.put(14, "minecraft:diamond_sword");
        items.put(15, "minecraft:gold_ingot");
        items.put(16, "minecraft:experience_bottle");
        items.put(17, "minecraft:barrier");
        return items;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadSection(Path dataFolder, String fileName, String sectionName, PluginLogger logger) {
        Path dedicatedFile = dataFolder.resolve(fileName);
        if (Files.exists(dedicatedFile)) try (InputStream is = Files.newInputStream(dedicatedFile)) {
            Map<String, Object> data = yaml.load(is);
            if (data != null) return data;
        } catch (Exception e) { logger.warning("Failed to load " + fileName + ": " + e.getMessage()); }

        Path configFile = dataFolder.resolve("config.yml");
        if (Files.exists(configFile)) try (InputStream is = Files.newInputStream(configFile)) {
            Map<String, Object> root = yaml.load(is);
            if (root != null && root.containsKey(sectionName)) {
                Object section = root.get(sectionName);
                if (section instanceof Map) return (Map<String, Object>) section;
            }
        } catch (Exception e) { logger.warning("Failed to load " + sectionName + " from config.yml: " + e.getMessage()); }

        return null;
    }

    private void createDefaultGuiConfigFiles() {
        for (String fileName : GUI_CONFIG_FILES) {
            Path target = dataFolder.resolve(fileName);
            if (!Files.exists(target)) try (InputStream is = getClass().getResourceAsStream("/" + fileName)) {
                if (is != null) {
                    Files.copy(is, target);
                    logger.info("Created default config file: " + fileName);
                }
            } catch (Exception e) { logger.warning("Failed to create " + fileName + ": " + e.getMessage()); }
        }
    }

    private void mergeGuiConfigDefaults() {
        for (String fileName : GUI_CONFIG_FILES)
            YamlMergeUtil.mergeWithDefaults("/" + fileName, dataFolder.resolve(fileName), logger);
    }

    private void migrateGuiConfigsFromConfigYml() {
        Path configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) return;

        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);

            migratePunishGuiSection(lines);
            migrateSection(lines);
            List<String> sectionsToRemove = List.of(
                    "punish_gui", "report_gui",
                    "staff_members_menu"
            );
            List<String> cleaned = removeTopLevelSections(lines, sectionsToRemove);
            if (cleaned.size() < lines.size()) {
                Files.writeString(configFile, String.join("\n", cleaned) + "\n", StandardCharsets.UTF_8);
                logger.info("Removed migrated GUI sections from config.yml");
            }
        } catch (Exception e) { logger.warning("Failed to migrate GUI configs from config.yml: " + e.getMessage()); }
    }

    private void migratePunishGuiSection(List<String> lines) throws IOException {
        Path targetFile = dataFolder.resolve("punish_gui.yml");
        if (Files.exists(targetFile)) return;

        String punishContent = extractAndUnindentSection(lines, "punish_gui");
        if (punishContent == null) return;

        Files.writeString(targetFile, punishContent.stripTrailing() + "\n", StandardCharsets.UTF_8);
        logger.info("Migrated punish GUI config from config.yml to punish_gui.yml");
    }

    private void migrateSection(List<String> lines) throws IOException {
        Path targetFile = dataFolder.resolve("report_gui.yml");
        if (Files.exists(targetFile)) return;

        String content = extractAndUnindentSection(lines, "report_gui");
        if (content == null) return;

        Files.writeString(targetFile, content.stripTrailing() + "\n", StandardCharsets.UTF_8);
        logger.info("Migrated " + "report_gui" + " from config.yml to " + "report_gui.yml");
    }

    static String extractAndUnindentSection(List<String> lines, String key) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++)
            if (isTopLevelKey(lines.get(i), key)) {
                start = i + 1;
                break;
            }

        if (start == -1) return null;

        List<String> content = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
            content.add(line);
        }

        while (!content.isEmpty() && content.get(content.size() - 1).isBlank())
            content.remove(content.size() - 1);

        if (content.isEmpty()) return null;

        int minIndent = Integer.MAX_VALUE;
        for (String line : content) {
            if (line.isBlank()) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            minIndent = Math.min(minIndent, indent);
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;

        StringBuilder sb = new StringBuilder();
        for (String line : content) {
            if (line.isBlank()) sb.append("\n");
            else sb.append(line.substring(Math.min(minIndent, line.length()))).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    static List<String> removeTopLevelSections(List<String> lines, List<String> sectionKeys) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            boolean matched = false;
            for (String key : sectionKeys)
                if (isTopLevelKey(lines.get(i), key)) {
                    matched = true;
                    break;
                }

            if (matched) {
                i++;
                while (i < lines.size()) {
                    String line = lines.get(i);
                    if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;

                    i++;
                }
                while (!result.isEmpty() && result.get(result.size() - 1).isBlank())
                    result.remove(result.size() - 1);

            } else {
                result.add(lines.get(i));
                i++;
            }
        }

        while (!result.isEmpty() && result.get(result.size() - 1).isBlank())
            result.remove(result.size() - 1);

        return result;
    }

    private static boolean isTopLevelKey(String line, String key) {
        return line.startsWith(key + ":");
    }

}
