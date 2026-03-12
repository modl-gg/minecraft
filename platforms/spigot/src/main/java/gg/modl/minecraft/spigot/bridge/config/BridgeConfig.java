package gg.modl.minecraft.spigot.bridge.config;

import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Bridge configuration, loaded from bridge-config.yml.
 */
@Getter
public class BridgeConfig {
    private static final String FILE_NAME = "bridge-config.yml";
    private static final int DEFAULT_REPORT_COOLDOWN = 60;
    private static final int DEFAULT_VIOLATION_THRESHOLD = 10;
    private static final int DEFAULT_QUERY_PORT = 25590;

    @Setter private String apiKey = "";
    @Setter private boolean debug = false;
    private boolean queryEnabled = true;
    private int queryPort = DEFAULT_QUERY_PORT;
    private List<String> statWipeCommands = new ArrayList<>(List.of("clearstats {player}"));
    private String anticheatName = "Anti-cheat";
    private String serverName = "Server 1";
    private int reportCooldown = DEFAULT_REPORT_COOLDOWN;
    private Map<String, Integer> reportViolationThresholds = new LinkedHashMap<>(Map.of("default", DEFAULT_VIOLATION_THRESHOLD));

    public int getReportViolationThreshold(String checkName) {
        Integer checkSpecific = reportViolationThresholds.get(checkName.toLowerCase());
        if (checkSpecific != null) return checkSpecific;
        Integer defaultVal = reportViolationThresholds.get("default");
        return defaultVal != null ? defaultVal : DEFAULT_VIOLATION_THRESHOLD;
    }

    public static boolean exists(Path dataDir) {
        return Files.exists(dataDir.resolve(FILE_NAME));
    }

    @SuppressWarnings("unchecked")
    public static BridgeConfig load(Path dataDir) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return new BridgeConfig();

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(file)) {
            Map<String, Object> data = yaml.load(is);
            if (data == null) return new BridgeConfig();
            return fromMap(data);
        }
    }

    public void save(Path dataDir) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Yaml yaml = new Yaml(options);
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write("# Bridge configuration for modl.gg Spigot plugin.\n");
            yaml.dump(toMap(), writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static BridgeConfig fromMap(Map<String, Object> data) {
        BridgeConfig config = new BridgeConfig();
        config.queryEnabled = getBool(data, "query-enabled", true);
        config.queryPort = getInt(data, "query-port", DEFAULT_QUERY_PORT);
        config.anticheatName = getStr(data, "anticheat-name", "Anti-cheat");
        config.serverName = getStr(data, "server-name", "Server 1");
        config.reportCooldown = getInt(data, "report-cooldown", DEFAULT_REPORT_COOLDOWN);

        Object cmds = data.get("stat-wipe-commands");
        if (cmds instanceof List<?> list) {
            config.statWipeCommands = list.stream().map(String::valueOf).toList();
        }

        Object threshObj = data.get("report-violation-threshold");
        if (threshObj instanceof Map<?, ?> threshMap) {
            Map<String, Integer> thresholds = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : threshMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                int val = entry.getValue() instanceof Number n ? n.intValue() : DEFAULT_VIOLATION_THRESHOLD;
                thresholds.put(key, val);
            }
            config.reportViolationThresholds = thresholds;
        }

        return config;
    }

    private Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query-enabled", queryEnabled);
        map.put("query-port", queryPort);
        map.put("stat-wipe-commands", statWipeCommands);
        map.put("anticheat-name", anticheatName);
        map.put("server-name", serverName);
        map.put("report-cooldown", reportCooldown);
        map.put("report-violation-threshold", reportViolationThresholds);
        return map;
    }

    private static String getStr(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val instanceof String s ? s : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : def;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean b ? b : def;
    }
}
