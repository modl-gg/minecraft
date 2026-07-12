package gg.modl.minecraft.core;

import gg.modl.minecraft.core.service.UpdateCheckerService;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlValues;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static gg.modl.minecraft.core.util.Java8Collections.entry;
import static gg.modl.minecraft.core.util.Java8Collections.mapOfEntries;

public final class PluginConfiguration {
    static final String LITEBANS_HOST_PLACEHOLDER = "litebans-database-host";
    static final String LITEBANS_DATABASE_PLACEHOLDER = "litebans_database";
    static final String LITEBANS_USERNAME_PLACEHOLDER = "litebans_user";
    static final String LITEBANS_PASSWORD_PLACEHOLDER = "change-me";

    private static final Map<String, String> DEFAULT_COMMAND_ALIASES = mapOfEntries(
            entry("modl", "modl"),
            entry("punish", "punish|p"),
            entry("ban", "ban"),
            entry("mute", "mute"),
            entry("kick", "kick"),
            entry("blacklist", "blacklist"),
            entry("pardon", "pardon"),
            entry("unban", "unban"),
            entry("unmute", "unmute"),
            entry("warn", "warn"),
            entry("inspect", "inspect|ins|check|lookup|look|info"),
            entry("staffmenu", "staffmenu|sm"),
            entry("history", "history|hist"),
            entry("alts", "alts|alt"),
            entry("notes", "notes"),
            entry("reports", "reports"),
            entry("iammuted", "iammuted"),
            entry("report", "report"),
            entry("chatreport", "chatreport"),
            entry("hackreport", "hackreport|hr"),
            entry("apply", "apply"),
            entry("bugreport", "bugreport"),
            entry("support", "support"),
            entry("tclaim", "tclaim|claimticket"),
            entry("standing", "standing"),
            entry("punishment_action", "modl:punishment-action"),
            entry("staffchat", "staffchat|sc"),
            entry("localchat", "localchat|lc"),
            entry("chat", "chat"),
            entry("stafflist", "stafflist|sl"),
            entry("freeze", "freeze"),
            entry("staffmode", "staffmode"),
            entry("vanish", "vanish|v"),
            entry("target", "target"),
            entry("verify", "verify"),
            entry("interceptnetworkchat", "interceptnetworkchat|inc"),
            entry("chatlogs", "chatlogs"),
            entry("commandlogs", "commandlogs"),
            entry("replay", "replay")
    );

    private PluginConfiguration() {
    }

    static String readLocaleFromConfig(Map<String, Object> config) {
        if (config.containsKey("locale")) {
            String locale = (String) config.get("locale");
            if (locale != null && !locale.isEmpty()) {
                return locale;
            }
        }
        return "en_US";
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> loadCommandAliases(Map<String, Object> config, PluginLogger logger) {
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_COMMAND_ALIASES);
        try {
            if (config.containsKey("commands")) {
                Map<String, Object> commands = (Map<String, Object>) config.get("commands");
                if (commands != null) {
                    for (Map.Entry<String, Object> entry : commands.entrySet()) {
                        Object rawValue = entry.getValue();
                        if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) {
                            logger.warning("Command '" + entry.getKey() + "' has a blank alias in config and will be disabled.");
                            aliases.put(entry.getKey(), "");
                        } else {
                            aliases.put(entry.getKey(), String.valueOf(rawValue));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load command aliases from config: " + e.getMessage());
        }
        return aliases;
    }

    @SuppressWarnings("unchecked")
    static DatabaseConfig loadDatabaseConfig(Map<String, Object> config, Path dataDirectory, PluginLogger logger) {
        try {
            if (!config.containsKey("migration")) return null;
            Map<String, Object> migration = (Map<String, Object>) config.get("migration");
            if (migration == null) return null;
            Map<String, Object> litebans = (Map<String, Object>) migration.get("litebans");
            if (litebans == null) return null;
            Map<String, Object> database = (Map<String, Object>) litebans.get("database");
            if (database == null) return null;

            String host = (String) database.get("host");
            String username = (String) database.get("username");
            String password = (String) database.get("password");
            if (host == null || username == null || password == null) return null;
            if (LITEBANS_HOST_PLACEHOLDER.equals(host)
                    || LITEBANS_USERNAME_PLACEHOLDER.equals(username)
                    || LITEBANS_PASSWORD_PLACEHOLDER.equals(password)) {
                return null;
            }

            int port = YamlValues.toInt(database.getOrDefault("port", 3306), 3306);
            String dbName = (String) database.getOrDefault("database", LITEBANS_DATABASE_PLACEHOLDER);
            String type = (String) database.getOrDefault("type", "mysql");
            String tablePrefix = (String) database.getOrDefault("table_prefix", "litebans_");

            DatabaseConfig.DatabaseType dbType = DatabaseConfig.DatabaseType.fromString(type);

            String detectedPrefix = detectLiteBansTablePrefix(dataDirectory, logger);
            if (detectedPrefix != null) {
                tablePrefix = detectedPrefix;
            }

            return new DatabaseConfig(host, dbName, username, password, dbType, tablePrefix, port);
        } catch (Exception e) {
            logger.warning("Failed to load database config: " + e.getMessage());
        }

        return null;
    }

    private static String detectLiteBansTablePrefix(Path dataDirectory, PluginLogger logger) {
        try {
            Path litebansConfig = dataDirectory.getParent().resolve("LiteBans").resolve("config.yml");

            if (!Files.exists(litebansConfig)) {
                return null;
            }

            try (InputStream inputStream = Files.newInputStream(litebansConfig)) {
                Map<String, Object> config = new Yaml().load(inputStream);

                if (config != null && config.containsKey("sql")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sql = (Map<String, Object>) config.get("sql");

                    if (sql.containsKey("table_prefix")) {
                        String prefix = (String) sql.get("table_prefix");
                        if (prefix != null && !prefix.isEmpty()) return prefix;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to read LiteBans config: " + e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    static UpdateCheckerConfig loadUpdateCheckerConfig(Map<String, Object> config, PluginLogger logger) {
        boolean enabled = true;
        int intervalMinutes = UpdateCheckerService.getDefaultIntervalMinutes();

        try {
            if (config.containsKey("update_checker")) {
                Object updateCheckerNode = config.get("update_checker");
                if (updateCheckerNode instanceof Map) {
                    Map<String, Object> updateChecker = (Map<String, Object>) updateCheckerNode;

                    enabled = YamlValues.toBoolean(updateChecker.get("enabled"), enabled);
                    intervalMinutes = YamlValues.toInt(updateChecker.get("interval_minutes"), intervalMinutes);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load update checker config: " + e.getMessage());
        }

        if (intervalMinutes < 1) {
            logger.warning("update_checker.interval_minutes must be at least 1. Using 1 minute.");
            intervalMinutes = 1;
        }

        return new UpdateCheckerConfig(enabled, intervalMinutes);
    }

    @SuppressWarnings("unchecked")
    static IpLookupConfig loadIpLookupConfig(Map<String, Object> config, PluginLogger logger) {
        boolean enabled = true;
        String url = "https://ipwho.is/{ip}";

        try {
            if (config.containsKey("ip-lookup")) {
                Object node = config.get("ip-lookup");
                if (node instanceof Map) {
                    Map<String, Object> ipLookup = (Map<String, Object>) node;

                    enabled = YamlValues.toBoolean(ipLookup.get("enabled"), enabled);

                    Object urlValue = ipLookup.get("url");
                    if (urlValue instanceof String && !((String) urlValue).trim().isEmpty()) {
                        url = (String) urlValue;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load ip-lookup config: " + e.getMessage());
        }

        return new IpLookupConfig(enabled, url);
    }

    static final class UpdateCheckerConfig {
        final boolean enabled;
        final int intervalMinutes;

        private UpdateCheckerConfig(boolean enabled, int intervalMinutes) {
            this.enabled = enabled;
            this.intervalMinutes = intervalMinutes;
        }
    }

    static final class IpLookupConfig {
        final boolean enabled;
        final String url;

        private IpLookupConfig(boolean enabled, String url) {
            this.enabled = enabled;
            this.url = url;
        }
    }
}
