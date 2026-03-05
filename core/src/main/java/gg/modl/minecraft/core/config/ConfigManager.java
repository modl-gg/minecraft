package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.YamlMergeUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Central config loader that manages per-feature config files with backward-compatible
 * fallback to config.yml sections.
 */
public class ConfigManager {
    private final Path dataFolder;
    private final Logger logger;

    private PunishGuiConfig punishGuiConfig;
    private ReportGuiConfig reportGuiConfig;
    private StandingGuiConfig standingGuiConfig;
    private StaffChatConfig staffChatConfig;
    private ChatManagementConfig chatManagementConfig;
    private Staff2faConfig staff2faConfig;
    /** Resource file names for GUI configs that should be auto-created. */
    private static final String[] GUI_CONFIG_FILES = {
            "punish_gui.yml", "report_gui.yml", "standing_gui.yml"
    };

    public ConfigManager(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        migrateGuiConfigsFromConfigYml();
        createDefaultGuiConfigFiles();
        mergeGuiConfigDefaults();
        reloadAll();
    }

    /**
     * Reload all configuration files. Called by /modl reload.
     */
    public void reloadAll() {
        punishGuiConfig = PunishGuiConfig.load(dataFolder, logger);
        reportGuiConfig = ReportGuiConfig.load(dataFolder, logger);
        standingGuiConfig = StandingGuiConfig.load(dataFolder, logger);
        staffChatConfig = StaffChatConfig.load(dataFolder, logger);
        chatManagementConfig = ChatManagementConfig.load(dataFolder, logger);
        staff2faConfig = Staff2faConfig.load(dataFolder, logger);
    }

    /**
     * Load a section from a dedicated YAML file, falling back to config.yml.
     * @param fileName The dedicated file name (e.g., "staff_chat.yml")
     * @param sectionName The section key in config.yml to fall back to (e.g., "staff_chat")
     * @return Parsed map, or null if neither source exists
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadSection(Path dataFolder, String fileName, String sectionName, Logger logger) {
        // Try dedicated file first
        Path dedicatedFile = dataFolder.resolve(fileName);
        if (Files.exists(dedicatedFile)) {
            try (InputStream is = Files.newInputStream(dedicatedFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null) {
                    return data;
                }
            } catch (Exception e) {
                logger.warning("Failed to load " + fileName + ": " + e.getMessage());
            }
        }

        // Fallback to config.yml section
        Path configFile = dataFolder.resolve("config.yml");
        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                if (root != null && root.containsKey(sectionName)) {
                    Object section = root.get(sectionName);
                    if (section instanceof Map) {
                        return (Map<String, Object>) section;
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load " + sectionName + " from config.yml: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Copy default GUI config files from JAR resources to the data folder if they don't exist.
     */
    private void createDefaultGuiConfigFiles() {
        for (String fileName : GUI_CONFIG_FILES) {
            Path target = dataFolder.resolve(fileName);
            if (!Files.exists(target)) {
                try (InputStream is = getClass().getResourceAsStream("/" + fileName)) {
                    if (is != null) {
                        Files.copy(is, target);
                        logger.info("Created default config file: " + fileName);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to create " + fileName + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Merge new keys from JAR resource defaults into existing GUI config files.
     */
    private void mergeGuiConfigDefaults() {
        for (String fileName : GUI_CONFIG_FILES) {
            YamlMergeUtil.mergeWithDefaults("/" + fileName,
                    dataFolder.resolve(fileName), logger);
        }
    }

    /**
     * Migrate GUI config sections from config.yml to dedicated files, then remove them from config.yml.
     * Preserves user customizations by extracting their config.yml sections before JAR defaults are copied.
     */
    private void migrateGuiConfigsFromConfigYml() {
        Path configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) return;

        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);

            // Migrate punish_gui.yml (includes punishment_type_items_by_ordinal)
            migratePunishGuiSection(lines);

            // Migrate report_gui.yml and standing_gui.yml
            migrateSection(lines, "report_gui", "report_gui.yml");
            migrateSection(lines, "standing_gui", "standing_gui.yml");

            // Remove all migrated sections from config.yml
            // staff_members_menu was moved to locale files
            List<String> sectionsToRemove = List.of(
                    "punish_gui", "report_gui", "standing_gui", "punishment_type_items_by_ordinal",
                    "staff_members_menu"
            );
            List<String> cleaned = removeTopLevelSections(lines, sectionsToRemove);
            if (cleaned.size() < lines.size()) {
                Files.writeString(configFile, String.join("\n", cleaned) + "\n", StandardCharsets.UTF_8);
                logger.info("Removed migrated GUI sections from config.yml");
            }
        } catch (Exception e) {
            logger.warning("Failed to migrate GUI configs from config.yml: " + e.getMessage());
        }
    }

    private void migratePunishGuiSection(List<String> lines) throws IOException {
        Path targetFile = dataFolder.resolve("punish_gui.yml");
        if (Files.exists(targetFile)) return;

        String punishContent = extractAndUnindentSection(lines, "punish_gui");
        String ptiboContent = extractRawSection(lines, "punishment_type_items_by_ordinal");

        if (punishContent == null && ptiboContent == null) return;

        StringBuilder sb = new StringBuilder();
        if (ptiboContent != null) {
            sb.append(ptiboContent);
            if (punishContent != null) sb.append("\n\n");
        }
        if (punishContent != null) {
            sb.append(punishContent);
        }

        Files.writeString(targetFile, sb.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
        logger.info("Migrated punish GUI config from config.yml to punish_gui.yml");
    }

    private void migrateSection(List<String> lines, String sectionKey, String fileName) throws IOException {
        Path targetFile = dataFolder.resolve(fileName);
        if (Files.exists(targetFile)) return;

        String content = extractAndUnindentSection(lines, sectionKey);
        if (content == null) return;

        Files.writeString(targetFile, content.stripTrailing() + "\n", StandardCharsets.UTF_8);
        logger.info("Migrated " + sectionKey + " from config.yml to " + fileName);
    }

    /**
     * Extract a top-level YAML section's children, unindented to root level.
     * Returns null if the section doesn't exist.
     */
    static String extractAndUnindentSection(List<String> lines, String key) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (isTopLevelKey(lines.get(i), key)) {
                start = i + 1;
                break;
            }
        }
        if (start == -1) return null;

        List<String> content = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                break;
            }
            content.add(line);
        }

        // Trim trailing blank lines
        while (!content.isEmpty() && content.get(content.size() - 1).isBlank()) {
            content.remove(content.size() - 1);
        }
        if (content.isEmpty()) return null;

        // Find minimum indent level
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
            if (line.isBlank()) {
                sb.append("\n");
            } else {
                sb.append(line.substring(Math.min(minIndent, line.length()))).append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Extract a top-level YAML section as raw text (key line + all indented content).
     * Returns null if the section doesn't exist.
     */
    static String extractRawSection(List<String> lines, String key) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (isTopLevelKey(lines.get(i), key)) {
                start = i;
                break;
            }
        }
        if (start == -1) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(start));
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                break;
            }
            sb.append("\n").append(line);
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Remove specified top-level YAML sections from lines.
     */
    static List<String> removeTopLevelSections(List<String> lines, List<String> sectionKeys) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            boolean matched = false;
            for (String key : sectionKeys) {
                if (isTopLevelKey(lines.get(i), key)) {
                    matched = true;
                    break;
                }
            }

            if (matched) {
                i++; // Skip the key line
                while (i < lines.size()) {
                    String line = lines.get(i);
                    if (!line.isEmpty() && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                        break;
                    }
                    i++;
                }
                // Remove trailing blank lines from accumulated result
                while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(lines.get(i));
                i++;
            }
        }

        // Remove trailing blank lines
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean isTopLevelKey(String line, String key) {
        return line.startsWith(key + ":");
    }

    public PunishGuiConfig getPunishGuiConfig() { return punishGuiConfig; }
    public ReportGuiConfig getReportGuiConfig() { return reportGuiConfig; }
    public StandingGuiConfig getStandingGuiConfig() { return standingGuiConfig; }
    public StaffChatConfig getStaffChatConfig() { return staffChatConfig; }
    public ChatManagementConfig getChatManagementConfig() { return chatManagementConfig; }
    public Staff2faConfig getStaff2faConfig() { return staff2faConfig; }
}
