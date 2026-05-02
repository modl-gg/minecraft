package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.PluginLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeConfigSource {

    private final Path dataFolder;
    private final PluginLogger logger;
    private final Map<String, Object> root;

    private RuntimeConfigSource(Path dataFolder, PluginLogger logger, Map<String, Object> root) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.root = immutableCopy(root);
    }

    public static RuntimeConfigSource load(Path dataFolder, PluginLogger logger) {
        return new RuntimeConfigSource(dataFolder, logger, readConfigYml(dataFolder, logger));
    }

    public Map<String, Object> root() {
        return root;
    }

    public Map<String, Object> rootSection(String sectionName) {
        Object section = root.get(sectionName);
        if (section instanceof Map) return immutableStringKeyMap((Map<?, ?>) section);
        return Collections.emptyMap();
    }

    public Map<String, Object> section(String fileName, String sectionName) {
        Map<String, Object> dedicated = readYamlFile(dataFolder.resolve(fileName), "Failed to load " + fileName + ": ");
        if (dedicated != null) return dedicated;

        Map<String, Object> section = rootSection(sectionName);
        if (!section.isEmpty()) return section;
        return Collections.emptyMap();
    }

    public Map<String, Object> dedicatedSection(String fileName, String sectionName) {
        Map<String, Object> dedicated = readYamlFile(dataFolder.resolve(fileName), "Failed to load " + fileName + ": ");
        if (dedicated == null) return Collections.emptyMap();
        Object nested = dedicated.get(sectionName);
        if (nested instanceof Map) return immutableStringKeyMap((Map<?, ?>) nested);
        return dedicated;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readConfigYml(Path dataFolder, PluginLogger logger) {
        try {
            Path configFile = dataFolder.resolve("config.yml");
            if (!Files.exists(configFile)) return Collections.emptyMap();
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Map<String, Object> config = new Yaml().load(inputStream);
                return config != null ? config : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("Failed to read config.yml: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYamlFile(Path file, String warningPrefix) {
        if (!Files.exists(file)) return null;

        try (InputStream inputStream = Files.newInputStream(file)) {
            Map<String, Object> data = new Yaml().load(inputStream);
            if (data != null) return immutableCopy(data);
        } catch (Exception e) {
            logger.warning(warningPrefix + e.getMessage());
        }

        return null;
    }

    private static Map<String, Object> immutableStringKeyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
