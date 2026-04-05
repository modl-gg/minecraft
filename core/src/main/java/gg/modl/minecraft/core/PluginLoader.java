package gg.modl.minecraft.core;

import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.impl.commands.staff.AltsCommand;
import gg.modl.minecraft.core.impl.commands.staff.ChatCommand;
import gg.modl.minecraft.core.impl.commands.staff.ChatLogsCommand;
import gg.modl.minecraft.core.impl.commands.staff.CommandLogsCommand;
import gg.modl.minecraft.core.impl.commands.staff.FreezeCommand;
import gg.modl.minecraft.core.impl.commands.staff.HistoryCommand;
import gg.modl.minecraft.core.impl.commands.staff.InspectCommand;
import gg.modl.minecraft.core.impl.commands.staff.InterceptNetworkChatCommand;
import gg.modl.minecraft.core.impl.commands.staff.LocalChatCommand;
import gg.modl.minecraft.core.impl.commands.staff.MaintenanceCommand;
import gg.modl.minecraft.core.impl.commands.ModlHelpCommand;
import gg.modl.minecraft.core.impl.commands.staff.ModlReloadCommand;
import gg.modl.minecraft.core.impl.commands.staff.NotesCommand;
import gg.modl.minecraft.core.impl.commands.staff.PunishmentActionCommand;
import gg.modl.minecraft.core.impl.commands.staff.ReportsCommand;
import gg.modl.minecraft.core.impl.commands.staff.StaffChatCommand;
import gg.modl.minecraft.core.impl.commands.staff.StaffCommand;
import gg.modl.minecraft.core.impl.commands.staff.StaffListCommand;
import gg.modl.minecraft.core.impl.commands.staff.StaffModeCommand;
import gg.modl.minecraft.core.impl.commands.staff.ReplayCommand;
import gg.modl.minecraft.core.impl.commands.staff.TargetCommand;
import gg.modl.minecraft.core.impl.commands.player.ApplyCommand;
import gg.modl.minecraft.core.impl.commands.player.BugReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ChatReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ClaimTicketCommand;
import gg.modl.minecraft.core.impl.commands.player.HackReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ReportCommand;
import gg.modl.minecraft.core.impl.commands.player.SupportCommand;
import gg.modl.minecraft.core.impl.commands.player.TicketCommandUtil;
import gg.modl.minecraft.core.impl.commands.staff.VanishCommand;
import gg.modl.minecraft.core.impl.commands.staff.VerifyCommand;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.commands.player.StandingCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.BanCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.BlacklistCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.KickCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.MuteCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.PardonCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.PunishCommand;
import gg.modl.minecraft.core.impl.commands.staff.punishments.WarnCommand;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.locale.MessageRenderer;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.*;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.PlayerLookupUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import gg.modl.minecraft.core.util.PluginLogger;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Getter
public class PluginLoader {
    private final Lamp<? extends CommandActor> lamp;
    private final HttpClientHolder httpClientHolder;
    private final CachedProfileRegistry cachedProfileRegistry;
    private final Cache cache;
    private final SyncService syncService;
    private final UpdateCheckerService updateCheckerService;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final AsyncCommandExecutor asyncCommandExecutor;
    private final Path dataDirectory;
    private final PluginLogger logger;
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
    private final boolean queryMojang, debugMode;

    public ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public PluginLoader(Platform platform, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.dataDirectory = dataDirectory;
        this.debugMode = httpManager.isDebugHttp();
        this.chatMessageCache = chatMessageCache;
        this.queryMojang = httpManager.isQueryMojang();
        this.asyncCommandExecutor = new AsyncCommandExecutor();
        cachedProfileRegistry = new CachedProfileRegistry();
        cache = new Cache(cachedProfileRegistry);
        cache.setQueryMojang(httpManager.isQueryMojang());
        loginCache = new LoginCache();
        platform.setCache(cache);

        this.configManager = new ConfigManager(dataDirectory, platform.getLogger());
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        this.httpClientHolder = httpManager.getHttpClientHolder();
        this.logger = platform.getLogger();

        Map<String, Object> configYml = readConfigYml(dataDirectory, this.logger);
        String configuredLocale = readLocaleFromConfig(configYml, this.logger);

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

        this.staff2faService = new Staff2faService(cachedProfileRegistry, configManager.getStaff2faConfig());
        this.chatCommandLogService = new ChatCommandLogService();

        this.syncService = new SyncService(platform, httpClientHolder, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey(), httpManager.getPanelUrl(),
                syncPollingRateSeconds, dataDirectory.toFile(), databaseConfig,
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

        this.lamp = platform.buildLamp(builder -> {
            builder.parameterTypes(types -> {
                types.addParameterType(AbstractPlayer.class, (input, context) -> {
                    String name = input.readString();
                    AbstractPlayer player = fetchPlayer(name, platform, getHttpClient(), queryMojang);
                    if (player == null) throw new RuntimeException(localeManager.getMessage("general.player_not_found"));
                    return player;
                });
                types.addParameterType(Account.class, (input, context) -> {
                    String name = input.readString();
                    Account account = fetchPlayer(name, getHttpClient());
                    if (account == null) throw new RuntimeException(localeManager.getMessage("general.player_not_found"));
                    return account;
                });
            });
        });

        Map<String, String> commandAliases = loadCommandAliases(configYml, logger);

        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager);
        lamp.register(punishCommand);
        punishCommand.initializePunishmentTypes();
        syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);

        initializeStaffPermissions(httpManager.getHttpClient(), cache, logger, httpManager.isDebugHttp());

        lamp.register(new ModlHelpCommand(cache, this.localeManager));
        lamp.register(new ModlReloadCommand(this.localeManager, this::reloadRuntimeConfiguration));
        lamp.register(new BanCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new MuteCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new KickCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new BlacklistCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new PardonCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new WarnCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new IAmMutedCommand(platform, cache, this.localeManager));
        lamp.register(new StandingCommand(httpClientHolder, platform, this.localeManager, configManager, cache));

        TicketCommandUtil ticketUtil = new TicketCommandUtil(cache);
        ModlHttpClient httpClient = httpManager.getHttpClient();
        String panelUrl = httpManager.getPanelUrl();
        lamp.register(new ReportCommand(asyncCommandExecutor, platform, httpClient, panelUrl, this.localeManager, chatMessageCache, ticketUtil));
        lamp.register(new ChatReportCommand(platform, httpClient, panelUrl, this.localeManager, chatMessageCache, ticketUtil));
        lamp.register(new HackReportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil));
        lamp.register(new ApplyCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil));
        lamp.register(new BugReportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil));
        lamp.register(new SupportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil));
        lamp.register(new ClaimTicketCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil));

        PunishmentTypeCacheManager punishmentTypeCache = new PunishmentTypeCacheManager();
        punishmentTypeCache.initialize(httpManager.getHttpClient(), logger);
        syncService.addPunishmentTypesListener(punishmentTypeCache::update);

        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl(), punishmentTypeCache);
        lamp.register(inspectCommand);
        lamp.register(new StaffCommand(asyncCommandExecutor, httpClientHolder, platform, cache,
            httpManager.getPanelUrl()));
        HistoryCommand historyCommand = new HistoryCommand(httpClientHolder, platform, cache, this.localeManager, punishmentTypeCache);
        lamp.register(historyCommand);
        lamp.register(new AltsCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new NotesCommand(httpClientHolder, platform, cache, this.localeManager));
        lamp.register(new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));
        lamp.register(new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));

        this.staffChatService = new StaffChatService(cachedProfileRegistry);
        this.chatManagementService = new ChatManagementService(cachedProfileRegistry);
        this.maintenanceService = new MaintenanceService();
        this.networkChatInterceptService = new NetworkChatInterceptService(cachedProfileRegistry);
        this.freezeService = new FreezeService(cachedProfileRegistry);
        this.staffModeService = new StaffModeService(cachedProfileRegistry);
        platform.setStaffModeService(this.staffModeService);
        platform.setStaff2faService(this.staff2faService);
        this.vanishService = new VanishService(cachedProfileRegistry);
        this.bridgeService = new BridgeService();
        platform.setBridgeService(this.bridgeService);
        platform.setChatInputManager(new ChatInputManager(platform));

        lamp.register(new StaffChatCommand(platform, cache, this.localeManager, staffChatService, configManager.getStaffChatConfig()));
        lamp.register(new LocalChatCommand(platform, cache, this.localeManager, staffChatService));
        lamp.register(new ChatCommand(platform, cache, this.localeManager, staffChatService, chatManagementService,
                configManager.getStaffChatConfig(), configManager.getChatManagementConfig()));
        lamp.register(new StaffListCommand(platform, cache, this.localeManager, vanishService, httpClientHolder, httpManager.getPanelUrl()));
        lamp.register(new VerifyCommand(platform, this.localeManager, staff2faService, httpClientHolder));
        lamp.register(new MaintenanceCommand(platform, cache, this.localeManager, maintenanceService));
        lamp.register(new InterceptNetworkChatCommand(networkChatInterceptService, cache, this.localeManager));
        lamp.register(new ChatLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));
        lamp.register(new CommandLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));
        lamp.register(new FreezeCommand(platform, cache, this.localeManager, freezeService, bridgeService));
        lamp.register(new StaffModeCommand(platform, cache, this.localeManager, staffModeService, vanishService, bridgeService));
        lamp.register(new VanishCommand(platform, cache, this.localeManager, vanishService, bridgeService));
        lamp.register(new TargetCommand(platform, cache, this.localeManager, staffModeService, bridgeService));
        lamp.register(new ReplayCommand(platform, cache, this.localeManager, httpManager.getPanelUrl()));

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
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Map<String, Object> config = new Yaml().load(inputStream);
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
            entry("maintenance", "maintenance"),
            entry("verify", "verify"),
            entry("interceptnetworkchat", "interceptnetworkchat|inc"),
            entry("chatlogs", "chatlogs"),
            entry("commandlogs", "commandlogs"),
            entry("replay", "replay")
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

    @SuppressWarnings("unchecked")
    private void loadLocaleConfig(Map<String, Object> config, PluginLogger logger) {
        try {
            if (config.containsKey("locale_config")) {
                Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                this.localeManager.setConfigValues(localeConfig);

                String dateFormat = this.localeManager.getDateFormatPattern();
                MenuItems.setDateFormat(dateFormat);
                DateFormatter.setDateFormat(dateFormat);

                String timezone = (String) localeConfig.getOrDefault("timezone", "");
                if (timezone != null && !timezone.isEmpty()) {
                    MenuItems.setTimezone(timezone);
                    DateFormatter.setTimezone(timezone);
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

                String detectedPrefix = detectLiteBansTablePrefix(dataDirectory, logger);
                if (detectedPrefix != null) {
                    tablePrefix = detectedPrefix;
                    logger.info("Detected LiteBans table prefix from config: " + tablePrefix);
                }

                logger.info("Loaded database config: " + type + " @ " + host + ":" + port + "/" + dbName);
                logger.info("Using table prefix: " + tablePrefix);

                return new DatabaseConfig(host, dbName, username, password, dbType, tablePrefix, port);
            }
        } catch (Exception e) {
            logger.warning("Failed to load database config: " + e.getMessage());
        }

        return createDefaultDatabaseConfig();
    }

    private DatabaseConfig createDefaultDatabaseConfig() {
        return new DatabaseConfig("localhost", "minecraft", "root", "", DatabaseConfig.DatabaseType.MYSQL, "litebans_", 3306);
    }

    private String detectLiteBansTablePrefix(Path dataDirectory, PluginLogger logger) {
        try {
            Path litebansConfig = dataDirectory.getParent().resolve("LiteBans").resolve("config.yml");

            if (!Files.exists(litebansConfig)) {
                logger.info("LiteBans config not found, using prefix from modl.gg config");
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

    private static void initializeStaffPermissions(ModlHttpClient httpClient, Cache cache, PluginLogger logger, boolean debugMode) {
        StaffPermissionLoader.load(httpClient, cache, logger, debugMode, false);
    }
}
