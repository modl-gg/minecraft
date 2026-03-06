package gg.modl.minecraft.core;

import co.aikar.commands.CommandManager;
import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.MessageKeys;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.LoginCache;
import gg.modl.minecraft.core.impl.commands.AltsCommand;
import gg.modl.minecraft.core.impl.commands.ChatCommand;
import gg.modl.minecraft.core.impl.commands.ChatLogsCommand;
import gg.modl.minecraft.core.impl.commands.CommandLogsCommand;
import gg.modl.minecraft.core.impl.commands.FreezeCommand;
import gg.modl.minecraft.core.impl.commands.HistoryCommand;
import gg.modl.minecraft.core.impl.commands.InspectCommand;
import gg.modl.minecraft.core.impl.commands.InterceptNetworkChatCommand;
import gg.modl.minecraft.core.impl.commands.LocalChatCommand;
import gg.modl.minecraft.core.impl.commands.MaintenanceCommand;
import gg.modl.minecraft.core.impl.commands.ModlReloadCommand;
import gg.modl.minecraft.core.impl.commands.NotesCommand;
import gg.modl.minecraft.core.impl.commands.PunishmentActionCommand;
import gg.modl.minecraft.core.impl.commands.ReportsCommand;
import gg.modl.minecraft.core.impl.commands.StaffChatCommand;
import gg.modl.minecraft.core.impl.commands.StaffCommand;
import gg.modl.minecraft.core.impl.commands.StaffListCommand;
import gg.modl.minecraft.core.impl.commands.StaffModeCommand;
import gg.modl.minecraft.core.impl.commands.TargetCommand;
import gg.modl.minecraft.core.impl.commands.player.TicketCommands;
import gg.modl.minecraft.core.impl.commands.VanishCommand;
import gg.modl.minecraft.core.impl.commands.VerifyCommand;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.commands.player.StandingCommand;
import gg.modl.minecraft.core.impl.commands.punishments.BanCommand;
import gg.modl.minecraft.core.impl.commands.punishments.BlacklistCommand;
import gg.modl.minecraft.core.impl.commands.punishments.KickCommand;
import gg.modl.minecraft.core.impl.commands.punishments.MuteCommand;
import gg.modl.minecraft.core.impl.commands.punishments.PardonCommand;
import gg.modl.minecraft.core.impl.commands.punishments.PunishCommand;
import gg.modl.minecraft.core.impl.commands.punishments.WarnCommand;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.locale.MessageRenderer;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.*;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.PlayerLookupUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import gg.modl.minecraft.core.util.PluginLogger;

@Getter
public class PluginLoader {
    private static final Yaml yaml = new Yaml();

    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final SyncService syncService;
    private final UpdateCheckerService updateCheckerService;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final boolean queryMojang;
    private final AsyncCommandExecutor asyncCommandExecutor;
    private final Path dataDirectory;
    private final PluginLogger logger;
    private final boolean debugMode;
    private final ConfigManager configManager;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final Staff2faService staff2faService;
    private final MaintenanceService maintenanceService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final ChatCommandLogService chatCommandLogService;
    private final FreezeService freezeService;
    private final StaffModeService staffModeService;
    private final VanishService vanishService;
    private final BridgeService bridgeService;

    public ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public PluginLoader(Platform platform, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.dataDirectory = dataDirectory;
        this.debugMode = httpManager.isDebugHttp();
        this.chatMessageCache = chatMessageCache;
        this.queryMojang = httpManager.isQueryMojang();
        this.asyncCommandExecutor = new AsyncCommandExecutor();
        cache = new Cache();
        cache.setQueryMojang(httpManager.isQueryMojang());
        loginCache = new LoginCache();
        platform.setCache(cache);

        this.configManager = new ConfigManager(dataDirectory, platform.getLogger());
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        this.httpClientHolder = httpManager.getHttpClientHolder();
        this.logger = platform.getLogger();

        PluginLogger logger = this.logger;
        Map<String, Object> configYml = readConfigYml(dataDirectory, logger);
        String configuredLocale = readLocaleFromConfig(configYml, logger);

        this.localeManager = new LocaleManager(configuredLocale);
        Path localeFile = dataDirectory.resolve("locale").resolve(configuredLocale + ".yml");
        if (Files.exists(localeFile)) {
            if (httpManager.isDebugHttp()) logger.info("Loading locale from external file: " + localeFile);
            this.localeManager.loadFromFile(localeFile);
        }
        this.localeManager.setRenderer(new MessageRenderer());
        loadLocaleConfig(configYml, logger);
        PunishmentMessages.setPanelUrl(httpManager.getPanelUrl());
        platform.setLocaleManager(this.localeManager);

        DatabaseConfig databaseConfig = loadDatabaseConfig(configYml, dataDirectory, logger);

        this.staff2faService = new Staff2faService(configManager.getStaff2faConfig());
        this.chatCommandLogService = new ChatCommandLogService();

        this.syncService = new SyncService(platform, httpClientHolder, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey(), syncPollingRateSeconds, dataDirectory.toFile(), databaseConfig,
                httpManager.isDebugHttp(), this.staff2faService, this.chatCommandLogService);

        if (httpManager.isDebugHttp()) {
            logger.info("modl.gg Configuration:");
            logger.info("  API URL: " + httpManager.getApiUrl());
            logger.info("  API Key: " + (httpManager.getApiKey().length() > 8 ?
                httpManager.getApiKey().substring(0, 8) + "..." : "***"));
            logger.info("  Debug Mode: " + httpManager.isDebugHttp());
        }

        syncService.start();

        UpdateCheckerConfig updateCheckerConfig = loadUpdateCheckerConfig(configYml, logger);
        this.updateCheckerService = new UpdateCheckerService(logger, this.debugMode, PluginInfo.VERSION);
        this.updateCheckerService.start(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);

        CommandManager<?, ?, ?, ?, ?, ?> commandManager = platform.getCommandManager();
        commandManager.enableUnstableAPI("help");
        commandManager.getLocales().addMessage(java.util.Locale.ENGLISH, MessageKeys.ERROR_PREFIX, "{message}");

        Map<String, String> commandAliases = loadCommandAliases(configYml, logger);
        registerCommandReplacements(commandManager, commandAliases);

        commandManager.getCommandContexts().registerContext(AbstractPlayer.class, (c) -> {
            AbstractPlayer player = fetchPlayer(c.popFirstArg(), platform, getHttpClient(), queryMojang);
            if (player == null) throw new ConditionFailedException(localeManager.getMessage("general.player_not_found"));
            return player;
        });

        commandManager.getCommandContexts().registerContext(Account.class, (c) -> {
            Account account = fetchPlayer(c.popFirstArg(), getHttpClient());
            if (account == null) throw new ConditionFailedException(localeManager.getMessage("general.player_not_found"));
            return account;
        });

        registerCommandConditions(commandManager, cache, this.localeManager, this.staff2faService);

        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager);
        commandManager.registerCommand(punishCommand);
        commandManager.getCommandCompletions().registerCompletion("punishment-types", c ->
            punishCommand.getPunishmentTypeNames()
        );
        punishCommand.initializePunishmentTypes();
        syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);

        initializeStaffPermissions(httpManager.getHttpClient(), cache, logger, httpManager.isDebugHttp());

        commandManager.registerCommand(new ModlReloadCommand(cache, this.localeManager, this::reloadRuntimeConfiguration));
        commandManager.registerCommand(new BanCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new MuteCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new KickCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new BlacklistCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new PardonCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new WarnCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new IAmMutedCommand(platform, cache, this.localeManager));
        commandManager.registerCommand(new StandingCommand(httpClientHolder, platform, this.localeManager, configManager));
        commandManager.registerCommand(new TicketCommands(asyncCommandExecutor, platform, httpManager.getHttpClient(), httpManager.getPanelUrl(),
            this.localeManager, chatMessageCache));

        PunishmentTypeCacheManager punishmentTypeCache = new PunishmentTypeCacheManager();
        punishmentTypeCache.initialize(httpManager.getHttpClient(), logger);
        syncService.addPunishmentTypesListener(punishmentTypeCache::update);

        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl(), punishmentTypeCache);
        commandManager.registerCommand(inspectCommand);
        commandManager.registerCommand(new StaffCommand(asyncCommandExecutor, httpClientHolder, platform, cache,
            httpManager.getPanelUrl()));
        HistoryCommand historyCommand = new HistoryCommand(httpClientHolder, platform, cache, this.localeManager, punishmentTypeCache);
        commandManager.registerCommand(historyCommand);
        commandManager.registerCommand(new AltsCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new NotesCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));
        commandManager.registerCommand(new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));

        this.staffChatService = new StaffChatService();
        this.chatManagementService = new ChatManagementService();
        this.maintenanceService = new MaintenanceService();
        this.networkChatInterceptService = new NetworkChatInterceptService();
        this.freezeService = new FreezeService();
        this.staffModeService = new StaffModeService();
        platform.setStaffModeService(this.staffModeService);
        platform.setStaff2faService(this.staff2faService);
        this.vanishService = new VanishService();
        this.bridgeService = new BridgeService();
        platform.setBridgeService(this.bridgeService);

        commandManager.registerCommand(new StaffChatCommand(platform, cache, this.localeManager, staffChatService, configManager.getStaffChatConfig()));
        commandManager.registerCommand(new LocalChatCommand(platform, cache, this.localeManager, staffChatService));
        commandManager.registerCommand(new ChatCommand(platform, cache, this.localeManager, staffChatService, chatManagementService,
                configManager.getStaffChatConfig(), configManager.getChatManagementConfig()));
        commandManager.registerCommand(new StaffListCommand(platform, cache, this.localeManager, vanishService, httpClientHolder, httpManager.getPanelUrl()));
        commandManager.registerCommand(new VerifyCommand(platform, this.localeManager, staff2faService, httpClientHolder));
        commandManager.registerCommand(new MaintenanceCommand(platform, cache, this.localeManager, maintenanceService));
        commandManager.registerCommand(new InterceptNetworkChatCommand(networkChatInterceptService, cache, this.localeManager));
        commandManager.registerCommand(new ChatLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));
        commandManager.registerCommand(new CommandLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));
        commandManager.registerCommand(new FreezeCommand(platform, cache, this.localeManager, freezeService, bridgeService));
        commandManager.registerCommand(new StaffModeCommand(platform, cache, this.localeManager, staffModeService, vanishService, bridgeService));
        commandManager.registerCommand(new VanishCommand(platform, cache, this.localeManager, vanishService, bridgeService));
        commandManager.registerCommand(new TargetCommand(platform, cache, this.localeManager, staffModeService, bridgeService));

        for (Map.Entry<String, String> entry : commandAliases.entrySet()) {
            if (entry.getKey().equals("modl")) continue;
            if (entry.getValue().isEmpty()) continue;
            for (String alias : entry.getValue().split("\\|")) asyncCommandExecutor.registerAsyncAlias(alias.trim());
        }
    }

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient, boolean queryMojang) {
        return PlayerLookupUtil.fetchPlayer(target, platform, httpClient, queryMojang);
    }

    public static Account fetchPlayer(String target, ModlHttpClient httpClient) {
        return PlayerLookupUtil.fetchAccount(target, httpClient);
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readConfigYml(Path dataDirectory, PluginLogger logger) {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) return Collections.emptyMap();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                return config != null ? config : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("Failed to read config.yml: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String readLocaleFromConfig(Map<String, Object> config, PluginLogger logger) {
        if (config.containsKey("locale")) {
            String locale = (String) config.get("locale");
            if (locale != null && !locale.isEmpty()) {
                logger.info("Using locale: " + locale);
                return locale;
            }
        }
        return "en_US";
    }

    private static final Map<String, String> DEFAULT_COMMAND_ALIASES = Map.ofEntries(
            Map.entry("modl", "modl"),
            Map.entry("punish", "punish|p"),
            Map.entry("ban", "ban"),
            Map.entry("mute", "mute"),
            Map.entry("kick", "kick"),
            Map.entry("blacklist", "blacklist"),
            Map.entry("pardon", "pardon"),
            Map.entry("unban", "unban"),
            Map.entry("unmute", "unmute"),
            Map.entry("warn", "warn"),
            Map.entry("inspect", "inspect|ins|check|lookup|look|info"),
            Map.entry("staffmenu", "staffmenu|sm"),
            Map.entry("history", "history|hist"),
            Map.entry("alts", "alts|alt"),
            Map.entry("notes", "notes"),
            Map.entry("reports", "reports"),
            Map.entry("iammuted", "iammuted"),
            Map.entry("report", "report"),
            Map.entry("chatreport", "chatreport"),
            Map.entry("hackreport", "hackreport|hr"),
            Map.entry("apply", "apply"),
            Map.entry("bugreport", "bugreport"),
            Map.entry("support", "support"),
            Map.entry("tclaim", "tclaim|claimticket"),
            Map.entry("standing", "standing"),
            Map.entry("punishment_action", "modl:punishment-action"),
            Map.entry("staffchat", "staffchat|sc"),
            Map.entry("localchat", "localchat|lc"),
            Map.entry("chat", "chat"),
            Map.entry("stafflist", "stafflist|sl"),
            Map.entry("freeze", "freeze"),
            Map.entry("staffmode", "staffmode"),
            Map.entry("vanish", "vanish|v"),
            Map.entry("target", "target"),
            Map.entry("maintenance", "maintenance"),
            Map.entry("verify", "verify"),
            Map.entry("interceptnetworkchat", "interceptnetworkchat|inc"),
            Map.entry("chatlogs", "chatlogs"),
            Map.entry("commandlogs", "commandlogs")
    );

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadCommandAliases(Map<String, Object> config, PluginLogger logger) {
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_COMMAND_ALIASES);
        try {
            if (config.containsKey("commands")) {
                Map<String, Object> commands = (Map<String, Object>) config.get("commands");
                if (commands != null) {
                    for (Map.Entry<String, Object> entry : commands.entrySet()) {
                        Object rawValue = entry.getValue();
                        if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) aliases.put(entry.getKey(), "");
                        else aliases.put(entry.getKey(), String.valueOf(rawValue));
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load command aliases from config: " + e.getMessage());
        }
        return aliases;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommandReplacements(CommandManager commandManager, Map<String, String> aliases) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (entry.getValue().isEmpty()) commandManager.getCommandReplacements().addReplacement("cmd_" + entry.getKey(), "modl:__disabled_" + entry.getKey());
            else commandManager.getCommandReplacements().addReplacement("cmd_" + entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLocaleConfig(Map<String, Object> config, PluginLogger logger) {
        try {
            if (config.containsKey("locale_config")) {
                Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                this.localeManager.setConfigValues(localeConfig);

                String dateFormat = this.localeManager.getDateFormatPattern();
                MenuItems.setDateFormat(dateFormat);
                DateFormatter.setDateFormat(dateFormat);
                PunishmentMessages.setDateFormat(dateFormat);

                String timezone = (String) localeConfig.getOrDefault("timezone", "");
                if (timezone != null && !timezone.isEmpty()) {
                    MenuItems.setTimezone(timezone);
                    DateFormatter.setTimezone(timezone);
                    PunishmentMessages.setTimezone(timezone);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale config: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private DatabaseConfig loadDatabaseConfig(Map<String, Object> config, Path dataDirectory, PluginLogger logger) {
        try {
            if (config.containsKey("migration")) {
                Map<String, Object> migration = (Map<String, Object>) config.get("migration");
                Map<String, Object> litebans = (Map<String, Object>) migration.get("litebans");
                Map<String, Object> database = (Map<String, Object>) litebans.get("database");

                String host = (String) database.getOrDefault("host", "localhost");
                int port = parseIntegerValue(database.getOrDefault("port", 3306), 3306);
                String dbName = (String) database.getOrDefault("database", "minecraft");
                String username = (String) database.getOrDefault("username", "root");
                String password = (String) database.getOrDefault("password", "");
                String type = (String) database.getOrDefault("type", "mysql");
                String tablePrefix = (String) database.getOrDefault("table_prefix", "litebans_");

                DatabaseConfig.DatabaseType dbType = DatabaseConfig.DatabaseType.fromString(type);

                // Try to read table prefix from LiteBans config if it exists
                String detectedPrefix = detectLiteBansTablePrefix(dataDirectory, logger);
                if (detectedPrefix != null) {
                    tablePrefix = detectedPrefix;
                    logger.info("Detected LiteBans table prefix from config: " + tablePrefix);
                }

                logger.info("Loaded database config: " + type + " @ " + host + ":" + port + "/" + dbName);
                logger.info("Using table prefix: " + tablePrefix);

                return new DatabaseConfig(host, port, dbName, username, password, dbType, tablePrefix);
            }
        } catch (Exception e) {
            logger.warning("Failed to load database config: " + e.getMessage());
        }

        return createDefaultDatabaseConfig();
    }

    private DatabaseConfig createDefaultDatabaseConfig() {
        return new DatabaseConfig("localhost", 3306, "minecraft", "root", "", DatabaseConfig.DatabaseType.MYSQL, "litebans_");
    }

    private String detectLiteBansTablePrefix(Path dataDirectory, PluginLogger logger) {
        try {
            // LiteBans config is in plugins/LiteBans/config.yml
            Path litebansConfig = dataDirectory.getParent().resolve("LiteBans").resolve("config.yml");
            
            if (!Files.exists(litebansConfig)) {
                logger.info("LiteBans config not found, using prefix from modl.gg config");
                return null;
            }
            
            try (InputStream inputStream = new FileInputStream(litebansConfig.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                
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

    public void shutdown() {
        if (updateCheckerService != null) updateCheckerService.stop();
        if (syncService != null) syncService.stop();
        if (loginCache != null) loginCache.shutdown();
        if (asyncCommandExecutor != null) asyncCommandExecutor.shutdown();
    }

    private void reloadRuntimeConfiguration() {
        configManager.reloadAll();
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        Map<String, Object> freshConfig = readConfigYml(this.dataDirectory, this.logger);
        UpdateCheckerConfig updateCheckerConfig = loadUpdateCheckerConfig(freshConfig, this.logger);
        updateCheckerService.reload(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);
    }

    @SuppressWarnings("unchecked")
    private static UpdateCheckerConfig loadUpdateCheckerConfig(Map<String, Object> config, PluginLogger logger) {
        boolean enabled = true;
        int intervalMinutes = UpdateCheckerService.getDefaultIntervalMinutes();

        try {
            if (config.containsKey("update_checker")) {
                Object updateCheckerNode = config.get("update_checker");
                if (updateCheckerNode instanceof Map) {
                    Map<String, Object> updateChecker = (Map<String, Object>) updateCheckerNode;

                    Object enabledValue = updateChecker.get("enabled");
                    if (enabledValue instanceof Boolean) enabled = (Boolean) enabledValue;
                    else if (enabledValue instanceof String) enabled = Boolean.parseBoolean((String) enabledValue);

                    Object intervalValue = updateChecker.get("interval_minutes");
                    intervalMinutes = parseIntegerValue(intervalValue, intervalMinutes);
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

    private static int parseIntegerValue(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static final class UpdateCheckerConfig {
        private final boolean enabled;
        private final int intervalMinutes;

        private UpdateCheckerConfig(boolean enabled, int intervalMinutes) {
            this.enabled = enabled;
            this.intervalMinutes = intervalMinutes;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommandConditions(CommandManager commandManager, Cache cache, LocaleManager localeManager, Staff2faService staff2faService) {
        commandManager.getCommandConditions().addCondition("staff", context -> {
            if (!context.getIssuer().isPlayer()) return;
            if (PermissionUtil.isStaff(context.getIssuer(), cache)) throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
            if (staff2faService != null && staff2faService.isEnabled() && !staff2faService.isAuthenticated(context.getIssuer().getUniqueId())) {
                throw new ConditionFailedException(localeManager.getMessage("staff_2fa.not_verified"));
            }
        });

        commandManager.getCommandConditions().addCondition("staff_no2fa", context -> {
            if (!context.getIssuer().isPlayer()) return;
            if (PermissionUtil.isStaff(context.getIssuer(), cache)) throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
        });

        commandManager.getCommandConditions().addCondition("player", context -> {
            if (!context.getIssuer().isPlayer()) throw new ConditionFailedException(localeManager.getMessage("iammuted.only_players"));
        });

        commandManager.getCommandConditions().addCondition("permission", context -> {
            if (!context.getIssuer().isPlayer()) return;
            String permission = context.getConfigValue("value", "");
            if (permission.isEmpty()) return;
            if (!PermissionUtil.hasPermission(context.getIssuer(), cache, permission)) {
                String message;
                if (permission.startsWith("punishment.apply.")) {
                    String type = permission.replace("punishment.apply.", "").replace("-", " ");
                    message = localeManager.getPunishmentMessage("general.no_permission_punishment",
                            Map.of("type", type));
                } else message = localeManager.getMessage("general.no_permission");
                throw new ConditionFailedException(message);
            }
            if (staff2faService != null && staff2faService.isEnabled() && !staff2faService.isAuthenticated(context.getIssuer().getUniqueId())) {
                throw new ConditionFailedException(localeManager.getMessage("staff_2fa.not_verified"));
            }
        });

        commandManager.getCommandConditions().addCondition("admin", context -> {
            if (!context.getIssuer().isPlayer()) return;
            if (!PermissionUtil.hasAnyPermission(context.getIssuer(), cache, Permissions.SETTINGS_VIEW, Permissions.SETTINGS_MODIFY, "admin.reload")) throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
        });
    }

    private static void initializeStaffPermissions(ModlHttpClient httpClient, Cache cache, PluginLogger logger, boolean debugMode) {
        StaffPermissionLoader.load(httpClient, cache, logger, debugMode, false);
    }
}