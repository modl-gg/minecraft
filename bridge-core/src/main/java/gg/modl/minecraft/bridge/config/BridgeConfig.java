package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.bridge.resource.BridgeYamlResource;
import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public class BridgeConfig {
    private static final String FILE_NAME = "bridge-config.yml";
    private static final int DEFAULT_REPORT_COOLDOWN = 60;
    private static final int DEFAULT_VIOLATION_THRESHOLD = 10;
    private static final int DEFAULT_QUERY_PORT = 25590;
    private static final int DEFAULT_REPLAY_BUFFER_DURATION = 120;
    private static final int DEFAULT_REPLAY_RADIUS = 64;
    private static final int DEFAULT_REPLAY_MOVE_THROTTLE = 50;
    private static final int DEFAULT_REPLAY_MAX_DURATION = 300;
    private static final int DEFAULT_REPLAY_LOCAL_TTL = 10080;

    @Setter private String apiKey = "";
    @Setter private boolean debug = false;
    private String proxyHost = "";
    private int proxyPort = DEFAULT_QUERY_PORT;
    private List<String> statWipeCommands = new ArrayList<>(listOf("clearstats {player}"));
    private String anticheatName = "Anti-cheat";
    private String serverName = "Server 1";
    private int reportCooldown = DEFAULT_REPORT_COOLDOWN;
    private Map<String, Integer> reportViolationThresholds = new LinkedHashMap<>(mapOf("default", DEFAULT_VIOLATION_THRESHOLD));

    private boolean replayEnabled = false;
    private boolean replayAutoRecord = false;
    private int replayBufferDuration = DEFAULT_REPLAY_BUFFER_DURATION;
    private int replayMaxDuration = DEFAULT_REPLAY_MAX_DURATION;
    private int replayRadius = DEFAULT_REPLAY_RADIUS;
    private int replayMoveThrottle = DEFAULT_REPLAY_MOVE_THROTTLE;
    private boolean replaySaveLocal = true;
    private int replayLocalTtl = DEFAULT_REPLAY_LOCAL_TTL;

    public int getReportViolationThreshold(String checkName) {
        Integer checkSpecific = reportViolationThresholds.get(checkName.toLowerCase(Locale.ROOT));
        if (checkSpecific != null) return checkSpecific;
        Integer defaultVal = reportViolationThresholds.get("default");
        return defaultVal != null ? defaultVal : DEFAULT_VIOLATION_THRESHOLD;
    }

    public void updateProxyConnection(String host, int port) {
        this.proxyHost = host;
        this.proxyPort = port;
    }

    public static boolean exists(Path dataDir) {
        return Files.exists(dataDir.resolve(FILE_NAME));
    }

    public static BridgeConfig load(Path dataDir) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return new BridgeConfig();

        Map<String, Object> data = BridgeYamlResource.loadMap(file);
        if (data.isEmpty()) return new BridgeConfig();
        return fromMap(data);
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
            writer.write("# Bridge configuration for modl.gg plugin.\n");
            yaml.dump(toMap(), writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static BridgeConfig fromMap(Map<String, Object> data) {
        BridgeConfig config = new BridgeConfig();
        config.proxyHost = getStr(data, "proxy-host", getStr(data, "host", getStr(data, "query-host", "")));
        config.proxyPort = getInt(data, "proxy-port", getInt(data, "port", getInt(data, "query-port", DEFAULT_QUERY_PORT)));
        config.anticheatName = getStr(data, "anticheat-name", "Anti-cheat");
        config.serverName = getStr(data, "server-name", "Server 1");
        config.reportCooldown = getInt(data, "report-cooldown", DEFAULT_REPORT_COOLDOWN);
        config.debug = getBool(data, "debug", false);

        Object cmds = data.get("stat-wipe-commands");
        if (cmds instanceof List<?>) {
            List<?> list = (List<?>) cmds;
            List<String> strList = new ArrayList<>();
            for (Object o : list) strList.add(String.valueOf(o));
            config.statWipeCommands = strList;
        }

        Object threshObj = data.get("report-violation-threshold");
        if (threshObj instanceof Map<?, ?>) {
            Map<?, ?> threshMap = (Map<?, ?>) threshObj;
            Map<String, Integer> thresholds = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : threshMap.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (entry.getValue() instanceof Number) {
                    thresholds.put(key, ((Number) entry.getValue()).intValue());
                }
            }
            config.reportViolationThresholds = thresholds;
        }

        config.replayEnabled = getBool(data, "replay-enabled", false);
        config.replayAutoRecord = getBool(data, "replay-auto-record", false);
        config.replayBufferDuration = getInt(data, "replay-buffer-duration", DEFAULT_REPLAY_BUFFER_DURATION);
        config.replayMaxDuration = getInt(data, "replay-max-duration", DEFAULT_REPLAY_MAX_DURATION);
        config.replayRadius = getInt(data, "replay-radius", DEFAULT_REPLAY_RADIUS);
        config.replayMoveThrottle = getInt(data, "replay-move-throttle", DEFAULT_REPLAY_MOVE_THROTTLE);
        config.replaySaveLocal = getBool(data, "replay-save-local", true);
        config.replayLocalTtl = getInt(data, "replay-local-ttl", DEFAULT_REPLAY_LOCAL_TTL);

        return config;
    }

    private Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("proxy-host", proxyHost);
        map.put("proxy-port", proxyPort);
        map.put("stat-wipe-commands", statWipeCommands);
        map.put("anticheat-name", anticheatName);
        map.put("server-name", serverName);
        map.put("debug", debug);
        map.put("report-cooldown", reportCooldown);
        map.put("report-violation-threshold", reportViolationThresholds);
        map.put("replay-enabled", replayEnabled);
        map.put("replay-auto-record", replayAutoRecord);
        map.put("replay-buffer-duration", replayBufferDuration);
        map.put("replay-max-duration", replayMaxDuration);
        map.put("replay-radius", replayRadius);
        map.put("replay-move-throttle", replayMoveThrottle);
        map.put("replay-save-local", replaySaveLocal);
        map.put("replay-local-ttl", replayLocalTtl);
        return map;
    }

    private static String getStr(Map<String, Object> map, String key, String def) {
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
