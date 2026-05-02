package gg.modl.minecraft.core.boot;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

final class BootConfigYaml {
    static final String FILE_NAME = "boot.yml";
    static final String KEY_MODE = "mode";
    static final String KEY_API_KEY = "api-key";
    static final String KEY_TESTING_API = "testing-api";
    static final String KEY_PROXY_TYPE = "proxy-type";
    static final String KEY_BRIDGE_PORT = "bridge-port";
    static final String KEY_PROXY_HOST = "proxy-host";
    static final String KEY_PROXY_PORT = "proxy-port";

    static final String MODE_STANDALONE = "standalone";
    static final String MODE_BRIDGE = "bridge";
    static final String MODE_BRIDGE_ONLY = "bridge-only";
    static final String MODE_PROXY = "proxy";

    static final int DEFAULT_BRIDGE_PORT = 25590;
    static final int DEFAULT_PROXY_PORT = 25590;

    private BootConfigYaml() {
    }

    static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        return new Yaml(options);
    }

    static BootConfig fromMap(Map<String, Object> data) {
        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.fromString((String) data.get(KEY_MODE)));
        config.setApiKey(getStr(data, KEY_API_KEY, ""));
        config.setTestingApi(getBool(data, KEY_TESTING_API, false));
        config.setProxyType(getStr(data, KEY_PROXY_TYPE, null));
        config.setBridgePort(getInt(data, KEY_BRIDGE_PORT, DEFAULT_BRIDGE_PORT));
        config.setWizardProxyHost(getStr(data, KEY_PROXY_HOST, null));
        config.setWizardProxyPort(getInt(data, KEY_PROXY_PORT, DEFAULT_PROXY_PORT));
        return config;
    }

    static Map<String, Object> toMap(BootConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KEY_MODE, config.getMode().toYaml());
        map.put(KEY_API_KEY, config.getApiKey());
        map.put(KEY_TESTING_API, config.isTestingApi());
        if (config.getProxyType() != null) map.put(KEY_PROXY_TYPE, config.getProxyType());
        if (config.getMode() == BootConfig.Mode.PROXY) map.put(KEY_BRIDGE_PORT, config.getBridgePort());
        if (config.getMode() == BootConfig.Mode.BRIDGE_ONLY
                && config.getWizardProxyHost() != null
                && !config.getWizardProxyHost().trim().isEmpty()) {
            map.put(KEY_PROXY_HOST, config.getWizardProxyHost().trim());
            map.put(KEY_PROXY_PORT, config.getWizardProxyPort());
        }
        return map;
    }

    static String template() {
        return "# modl.gg Boot Configuration\n"
                + "# Edit this file and restart the server.\n"
                + "# To register a new server, visit https://modl.gg/register\n"
                + "#\n"
                + "# mode: standalone | bridge-only | proxy\n"
                + "mode: proxy\n"
                + "\n"
                + "# Your API key from the modl.gg panel\n"
                + "api-key: \"" + BootConfig.PLACEHOLDER_API_KEY + "\"\n"
                + "\n"
                + "# Uncomment to use the testing API (api.modl.top)\n"
                + "# testing-api: true\n"
                + "\n"
                + "# Proxy connection target (bridge-only mode)\n"
                + "# proxy-host: \"127.0.0.1\"\n"
                + "# proxy-port: 25590\n"
                + "\n"
                + "# Bridge listen port (proxy mode only — backends connect to this port)\n"
                + "# bridge-port: 25590\n";
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
