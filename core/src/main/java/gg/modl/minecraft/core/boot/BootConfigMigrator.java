package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BootConfigMigrator {
    private static final String PLACEHOLDER_API_URL = "https://yourserver.modl.gg";

    @SuppressWarnings("unchecked")
    public static Optional<BootConfig> migrateFromConfigYml(Path dataDir, PlatformType platformType, PluginLogger logger) {
        Path configFile = dataDir.resolve("config.yml");
        if (!Files.exists(configFile)) return Optional.empty();

        try {
            Map<String, Object> config = loadYaml(configFile);
            if (config == null) return Optional.empty();

            String apiKey = getNestedString(config, "api.key", BootConfig.PLACEHOLDER_API_KEY);
            String apiUrl = getNestedString(config, "api.url", PLACEHOLDER_API_URL);

            if (BootConfig.PLACEHOLDER_API_KEY.equals(apiKey) || PLACEHOLDER_API_URL.equals(apiUrl)) {
                return Optional.empty();
            }

            boolean testingApi = getNestedBool(config, "api.testing-api", false);

            BootConfig boot = new BootConfig();
            boot.setApiKey(apiKey);
            boot.setPanelUrl(apiUrl);
            boot.setTestingApi(testingApi);

            if (platformType == PlatformType.SPIGOT) {
                String bridgeHost = getNestedString(config, "bridge.host", "");
                if (!bridgeHost.isEmpty()) {
                    boot.setMode(BootConfig.Mode.BRIDGE_ONLY);
                } else {
                    boot.setMode(BootConfig.Mode.STANDALONE);
                }
                migrateBridgeConfig(dataDir, logger);
            } else {
                boot.setMode(BootConfig.Mode.PROXY);
                String bridgeHost = getNestedString(config, "bridge.host", "");
                if (!bridgeHost.isEmpty()) {
                    int bridgePort = getNestedInt(config, "bridge.port", 25590);
                    boot.setBackendBridges(Collections.singletonList(new BootConfig.BackendBridge(bridgeHost, bridgePort)));
                }
            }

            boot.save(dataDir);
            logger.info("Migrated configuration to boot.yml");
            return Optional.of(boot);
        } catch (Exception e) {
            logger.warning("Failed to migrate config.yml to boot.yml: " + e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static void migrateBridgeConfig(Path dataDir, PluginLogger logger) {
        Path bridgeConfigFile = dataDir.getParent().resolve("modl-bridge").resolve("config.yml");
        if (!Files.exists(bridgeConfigFile)) return;

        try {
            Map<String, Object> bridgeYml = loadYaml(bridgeConfigFile);
            if (bridgeYml == null) return;

            Map<String, Object> bridgeConfigMap = new LinkedHashMap<>();
            bridgeConfigMap.put("query-enabled", getBool(bridgeYml, "query-enabled", true));
            bridgeConfigMap.put("query-port", getInt(bridgeYml, "query-port", 25590));

            Object cmds = bridgeYml.get("stat-wipe-commands");
            if (cmds instanceof List<?>) {
                List<?> list = (List<?>) cmds;
                List<String> strList = new ArrayList<>();
                for (Object o : list) strList.add(String.valueOf(o));
                bridgeConfigMap.put("stat-wipe-commands", strList);
            } else {
                bridgeConfigMap.put("stat-wipe-commands", Collections.singletonList("clearstats {player}"));
            }

            bridgeConfigMap.put("anticheat-name", getString(bridgeYml, "anticheat-name", "Anti-cheat"));
            bridgeConfigMap.put("server-name", getString(bridgeYml, "server-name", "Server 1"));
            bridgeConfigMap.put("report-cooldown", getInt(bridgeYml, "report-cooldown", 60));

            Map<String, Integer> thresholds = new LinkedHashMap<>();
            Object threshObj = bridgeYml.get("report-violation-threshold");
            if (threshObj instanceof Map<?, ?>) {
                Map<?, ?> threshMap = (Map<?, ?>) threshObj;
                for (Map.Entry<?, ?> entry : threshMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Number) {
                        thresholds.put(key, ((Number) entry.getValue()).intValue());
                    } else if (entry.getValue() instanceof Map<?, ?>) {
                        Map<?, ?> checksMap = (Map<?, ?>) entry.getValue();
                        for (Map.Entry<?, ?> check : checksMap.entrySet()) {
                            if (check.getValue() instanceof Number) {
                                thresholds.put(String.valueOf(check.getKey()), ((Number) check.getValue()).intValue());
                            }
                        }
                    }
                }
            }
            if (thresholds.isEmpty()) thresholds.put("default", 10);
            bridgeConfigMap.put("report-violation-threshold", thresholds);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Path bridgeConfigOut = dataDir.resolve("bridge-config.yml");
            Yaml yaml = new Yaml(options);
            try (Writer writer = Files.newBufferedWriter(bridgeConfigOut)) {
                writer.write("# Bridge configuration migrated from modl-bridge/config.yml.\n");
                yaml.dump(bridgeConfigMap, writer);
            }

            logger.info("Migrated bridge settings from modl-bridge/config.yml to bridge-config.yml");
        } catch (Exception e) {
            logger.warning("Failed to migrate bridge config: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return new Yaml().load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private static String getNestedString(Map<String, Object> map, String path, String def) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?>) current = ((Map<?, ?>) current).get(part);
            else return def;
        }
        return current instanceof String ? (String) current : def;
    }

    @SuppressWarnings("unchecked")
    private static int getNestedInt(Map<String, Object> map, String path, int def) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?>) current = ((Map<?, ?>) current).get(part);
            else return def;
        }
        return current instanceof Number ? ((Number) current).intValue() : def;
    }

    @SuppressWarnings("unchecked")
    private static boolean getNestedBool(Map<String, Object> map, String path, boolean def) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?>) current = ((Map<?, ?>) current).get(part);
            else return def;
        }
        return current instanceof Boolean ? (Boolean) current : def;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }
}
