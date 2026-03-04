package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.YamlMergeUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public PunishGuiConfig getPunishGuiConfig() { return punishGuiConfig; }
    public ReportGuiConfig getReportGuiConfig() { return reportGuiConfig; }
    public StandingGuiConfig getStandingGuiConfig() { return standingGuiConfig; }
    public StaffChatConfig getStaffChatConfig() { return staffChatConfig; }
    public ChatManagementConfig getChatManagementConfig() { return chatManagementConfig; }
    public Staff2faConfig getStaff2faConfig() { return staff2faConfig; }
}
