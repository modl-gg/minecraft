package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.YamlMergeUtil;
import lombok.Getter;
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

public class ConfigManager {
    private static final Yaml yaml = new Yaml();

    private final Path dataFolder;
    private final Logger logger;

    @Getter private PunishGuiConfig punishGuiConfig;
    @Getter private ReportGuiConfig reportGuiConfig;
    @Getter private StandingGuiConfig standingGuiConfig;
    @Getter private StaffChatConfig staffChatConfig;
    @Getter private ChatManagementConfig chatManagementConfig;
    @Getter private Staff2faConfig staff2faConfig;
    private static final String[] GUI_CONFIG_FILES = {
            "punish_gui.yml", "report_gui.yml"
    };

    public ConfigManager(Path dataFolder, Logger logger) {
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
        standingGuiConfig = StandingGuiConfig.load(dataFolder, logger);
        staffChatConfig = StaffChatConfig.load(dataFolder, logger);
        chatManagementConfig = ChatManagementConfig.load(dataFolder, logger);
        staff2faConfig = Staff2faConfig.load(dataFolder, logger);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadSection(Path dataFolder, String fileName, String sectionName, Logger logger) {
        Path dedicatedFile = dataFolder.resolve(fileName);
        if (Files.exists(dedicatedFile)) {
            try (InputStream is = Files.newInputStream(dedicatedFile)) {
                Map<String, Object> data = yaml.load(is);
                if (data != null) return data;
            } catch (Exception e) {
                logger.warning("Failed to load " + fileName + ": " + e.getMessage());
            }
        }

        Path configFile = dataFolder.resolve("config.yml");
        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                Map<String, Object> root = yaml.load(is);
                if (root != null && root.containsKey(sectionName)) {
                    Object section = root.get(sectionName);
                    if (section instanceof Map) return (Map<String, Object>) section;
                }
            } catch (Exception e) {
                logger.warning("Failed to load " + sectionName + " from config.yml: " + e.getMessage());
            }
        }

        return null;
    }

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

    private void mergeGuiConfigDefaults() {
        for (String fileName : GUI_CONFIG_FILES) {
            YamlMergeUtil.mergeWithDefaults("/" + fileName,
                    dataFolder.resolve(fileName), logger);
        }
    }

    private void migrateGuiConfigsFromConfigYml() {
        Path configFile = dataFolder.resolve("config.yml");
        if (!Files.exists(configFile)) return;

        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);

            migratePunishGuiSection(lines);
            migrateSection(lines, "report_gui", "report_gui.yml");
            List<String> sectionsToRemove = List.of(
                    "punish_gui", "report_gui",
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
        if (punishContent == null) return;

        Files.writeString(targetFile, punishContent.stripTrailing() + "\n", StandardCharsets.UTF_8);
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
            if (!line.isEmpty() && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
            content.add(line);
        }

        while (!content.isEmpty() && content.get(content.size() - 1).isBlank()) {
            content.remove(content.size() - 1);
        }
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
            for (String key : sectionKeys) {
                if (isTopLevelKey(lines.get(i), key)) {
                    matched = true;
                    break;
                }
            }

            if (matched) {
                i++;
                while (i < lines.size()) {
                    String line = lines.get(i);
                    if (!line.isEmpty() && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                        break;
                    }
                    i++;
                }
                while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(lines.get(i));
                i++;
            }
        }

        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean isTopLevelKey(String line, String key) {
        return line.startsWith(key + ":");
    }

}
