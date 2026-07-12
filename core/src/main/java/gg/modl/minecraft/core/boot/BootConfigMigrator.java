package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlValues;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BootConfigMigrator {
    private static final String PLACEHOLDER_API_URL = "https://yourserver.modl.gg";

    @SuppressWarnings("unchecked")
    public static Optional<BootConfig> migrateFromConfigYml(Path dataDir, PlatformType platformType, PluginLogger logger) {
        Path configFile = dataDir.resolve("config.yml");
        if (!Files.exists(configFile)) return Optional.empty();

        try {
            Map<String, Object> config = loadYaml(configFile);
            if (config == null) return Optional.empty();

            String apiKey = YamlValues.nestedString(config, "api.key", BootConfig.PLACEHOLDER_API_KEY);
            String apiUrl = YamlValues.nestedString(config, "api.url", PLACEHOLDER_API_URL);

            if (BootConfig.PLACEHOLDER_API_KEY.equals(apiKey) || PLACEHOLDER_API_URL.equals(apiUrl)) {
                return Optional.empty();
            }

            boolean testingApi = YamlValues.nestedBool(config, "api.testing-api", false);

            BootConfig boot = new BootConfig();
            boot.setApiKey(apiKey);
            boot.setTestingApi(testingApi);

            if (isBackendPlatform(platformType)) {
                String bridgeHost = YamlValues.nestedString(config, "bridge.host", "");
                if (!bridgeHost.isEmpty()) {
                    boot.setMode(BootConfig.Mode.BRIDGE_ONLY);
                    boot.setWizardProxyHost(bridgeHost);
                    boot.setWizardProxyPort(YamlValues.nestedInt(config, "bridge.port", 25590));
                } else {
                    boot.setMode(BootConfig.Mode.STANDALONE);
                }
                migrateBridgeConfig(dataDir, logger);
            } else {
                boot.setMode(BootConfig.Mode.PROXY);
                boot.setBridgePort(YamlValues.nestedInt(config, "bridge.port", 25590));
            }

            boot.save(dataDir);
            return Optional.of(boot);
        } catch (Exception e) {
            logger.warning("Failed to migrate config.yml to boot.yml: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isBackendPlatform(PlatformType platformType) {
        return platformType == PlatformType.SPIGOT || platformType == PlatformType.FABRIC;
    }

    @SuppressWarnings("unchecked")
    private static void migrateBridgeConfig(Path dataDir, PluginLogger logger) {
        Path bridgeConfigFile = dataDir.getParent().resolve("modl-bridge").resolve("config.yml");
        if (!Files.exists(bridgeConfigFile)) return;

        try {
            Map<String, Object> bridgeYml = loadYaml(bridgeConfigFile);
            if (bridgeYml == null) return;

            Map<String, Object> bridgeConfigMap = new LinkedHashMap<>();
            bridgeConfigMap.put("query-enabled", YamlValues.asBoolean(bridgeYml.get("query-enabled"), true));
            bridgeConfigMap.put("query-port", YamlValues.asInt(bridgeYml.get("query-port"), 25590));

            Object cmds = bridgeYml.get("stat-wipe-commands");
            if (cmds instanceof List<?>) {
                List<?> list = (List<?>) cmds;
                List<String> strList = new ArrayList<>();
                for (Object o : list) strList.add(String.valueOf(o));
                bridgeConfigMap.put("stat-wipe-commands", strList);
            } else {
                bridgeConfigMap.put("stat-wipe-commands", Collections.singletonList("clearstats {player}"));
            }

            bridgeConfigMap.put("anticheat-name", YamlValues.asString(bridgeYml.get("anticheat-name"), "Anti-cheat"));
            bridgeConfigMap.put("server-name", YamlValues.asString(bridgeYml.get("server-name"), "Server 1"));
            bridgeConfigMap.put("report-cooldown", YamlValues.asInt(bridgeYml.get("report-cooldown"), 60));

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
}
