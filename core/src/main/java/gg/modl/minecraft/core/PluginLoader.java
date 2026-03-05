package gg.modl.minecraft.core;


import co.aikar.commands.CommandManager;
import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.MessageKeys;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.LoginCache;
import gg.modl.minecraft.core.impl.commands.TicketCommands;
import gg.modl.minecraft.core.impl.commands.ModlReloadCommand;
import gg.modl.minecraft.core.impl.commands.InspectCommand;
import gg.modl.minecraft.core.impl.commands.StaffCommand;
import gg.modl.minecraft.core.impl.commands.HistoryCommand;
import gg.modl.minecraft.core.impl.commands.AltsCommand;
import gg.modl.minecraft.core.impl.commands.NotesCommand;
import gg.modl.minecraft.core.impl.commands.ReportsCommand;
import gg.modl.minecraft.core.impl.commands.PunishmentActionCommand;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.commands.player.StandingCommand;
import gg.modl.minecraft.core.impl.commands.punishments.*;
import gg.modl.minecraft.core.impl.commands.*;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.locale.MessageRenderer;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.*;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

@Getter
public class PluginLoader {
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
    private final Logger logger;
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

    /**
     * Get the current HTTP client from the holder.
     * Use this for components that need the client but don't need dynamic switching.
     */
    public ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.dataDirectory = dataDirectory;
        this.debugMode = httpManager.isDebugHttp();
        this.chatMessageCache = chatMessageCache;
        this.queryMojang = httpManager.isQueryMojang();
        this.asyncCommandExecutor = new AsyncCommandExecutor();
        cache = new Cache();
        cache.setQueryMojang(httpManager.isQueryMojang());
        loginCache = new LoginCache();

        // Set the cache on the platform for menu access
        platform.setCache(cache);

        // Initialize ConfigManager for all feature configs
        this.configManager = new ConfigManager(dataDirectory, Logger.getLogger("modl-config"));

        this.httpClientHolder = httpManager.getHttpClientHolder();
        this.logger = Logger.getLogger("modl-" + platform.getClass().getSimpleName());

        // Parse config.yml once for all loader methods
        Logger logger = this.logger;
        Map<String, Object> configYml = readConfigYml(dataDirectory, logger);

        // Read configured locale from config.yml
        String configuredLocale = readLocaleFromConfig(configYml, logger);

        // Initialize locale manager with support for external locale files
        this.localeManager = new LocaleManager(configuredLocale);

        // Try to load locale from external file if it exists
        Path localeFile = dataDirectory.resolve("locale").resolve(configuredLocale + ".yml");
        if (Files.exists(localeFile)) {
            if (httpManager.isDebugHttp()) {
                logger.info("Loading locale from external file: " + localeFile);
            }
            this.localeManager.loadFromFile(localeFile);
        }

        // Initialize MessageRenderer for MiniMessage + legacy auto-detection
        MessageRenderer messageRenderer = new MessageRenderer();
        this.localeManager.setRenderer(messageRenderer);

        // Load locale config values from config.yml
        loadLocaleConfig(configYml, logger);

        // Set panel URL on PunishmentMessages (derived from api.url config)
        PunishmentMessages.setPanelUrl(httpManager.getPanelUrl());

        // Set the locale manager on the platform for menu access
        platform.setLocaleManager(this.localeManager);

        // Load database configuration for migration
        DatabaseConfig databaseConfig = loadDatabaseConfig(configYml, dataDirectory, logger);

        // Initialize staff 2FA service early (needed by sync service)
        this.staff2faService = new Staff2faService(configManager.getStaff2faConfig());

        // Initialize chat/command log service early (needed by sync service)
        this.chatCommandLogService = new ChatCommandLogService();

        // Initialize sync service with configurable polling rate
        this.syncService = new SyncService(platform, httpClientHolder, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey(), syncPollingRateSeconds, dataDirectory.toFile(), databaseConfig,
                httpManager.isDebugHttp(), this.staff2faService, this.chatCommandLogService);
        
        // Log configuration details (only in debug mode)
        if (httpManager.isDebugHttp()) {
            logger.info("modl.gg Configuration:");
            logger.info("  API URL: " + httpManager.getApiUrl());
            logger.info("  API Key: " + (httpManager.getApiKey().length() > 8 ?
                httpManager.getApiKey().substring(0, 8) + "..." : "***"));
            logger.info("  Debug Mode: " + httpManager.isDebugHttp());
        }
        
        // Start sync service
        syncService.start();

        // Start update checker service
        UpdateCheckerConfig updateCheckerConfig = loadUpdateCheckerConfig(configYml, logger);
        this.updateCheckerService = new UpdateCheckerService(logger, this.debugMode, PluginInfo.VERSION);
        this.updateCheckerService.start(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);

        CommandManager<?, ?, ?, ?, ?, ?> commandManager = platform.getCommandManager();
        commandManager.enableUnstableAPI("help");

        // Remove the default "Error: " prefix from ACF error messages — use just the message content
        commandManager.getLocales().addMessage(java.util.Locale.ENGLISH, MessageKeys.ERROR_PREFIX, "{message}");

        // Load and register command aliases from config before registering commands
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

        // Register ACF command conditions for permission checks
        registerCommandConditions(commandManager, cache, this.localeManager, this.staff2faService);
//
        // Removed duplicate - TicketCommands registered below

        // Register punishment command with tab completion
        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl());
        commandManager.registerCommand(punishCommand);

        // Set up punishment types tab completion
        commandManager.getCommandCompletions().registerCompletion("punishment-types", c ->
            punishCommand.getPunishmentTypeNames()
        );

        // Initialize punishment types cache
        punishCommand.initializePunishmentTypes();

        // Register punishment types listeners for auto-refresh
        syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);

        // Initialize staff permissions cache at startup
        initializeStaffPermissions(httpManager.getHttpClient(), cache, logger, httpManager.isDebugHttp());

        // Register reload command
        commandManager.registerCommand(new ModlReloadCommand(platform, cache, this.localeManager, this::reloadRuntimeConfiguration));

        // Register manual punishment commands
        commandManager.registerCommand(new BanCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new MuteCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new KickCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new BlacklistCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new PardonCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new WarnCommand(httpClientHolder, platform, cache, this.localeManager));

        // Register player commands
        commandManager.registerCommand(new IAmMutedCommand(platform, cache, this.localeManager));
        commandManager.registerCommand(new StandingCommand(httpClientHolder, platform, this.localeManager));
        commandManager.registerCommand(new TicketCommands(asyncCommandExecutor, platform, httpClientHolder, httpManager.getHttpClient(), httpManager.getPanelUrl(),
            this.localeManager, chatMessageCache));

        // Register GUI commands (menus require V2 API - commands will check dynamically via holder)
        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl());
        commandManager.registerCommand(inspectCommand);
        inspectCommand.initializePunishmentTypes();
        syncService.addPunishmentTypesListener(inspectCommand::updatePunishmentTypesCache);
        commandManager.registerCommand(new StaffCommand(asyncCommandExecutor, httpClientHolder, platform, cache, this.localeManager,
            httpManager.getPanelUrl()));
        HistoryCommand historyCommand = new HistoryCommand(httpClientHolder, platform, cache, this.localeManager);
        commandManager.registerCommand(historyCommand);
        historyCommand.initializePunishmentTypes();
        syncService.addPunishmentTypesListener(historyCommand::updatePunishmentTypesCache);
        commandManager.registerCommand(new AltsCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new NotesCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));
        commandManager.registerCommand(new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));

        // ==================== STAFF TOOLS SERVICES ====================

        // Initialize services
        this.staffChatService = new StaffChatService();
        this.chatManagementService = new ChatManagementService();
        // staff2faService already initialized above (needed by sync service)
        this.maintenanceService = new MaintenanceService();
        this.networkChatInterceptService = new NetworkChatInterceptService();
        // chatCommandLogService already initialized above (needed by sync service)
        this.freezeService = new FreezeService();
        this.staffModeService = new StaffModeService();
        platform.setStaffModeService(this.staffModeService);
        platform.setStaff2faService(this.staff2faService);
        this.vanishService = new VanishService();
        this.bridgeService = new BridgeService();
        platform.setBridgeService(this.bridgeService);

        // Register staff chat commands
        commandManager.registerCommand(new StaffChatCommand(platform, cache, this.localeManager, staffChatService, configManager.getStaffChatConfig()));
        commandManager.registerCommand(new LocalChatCommand(platform, cache, this.localeManager, staffChatService));
        commandManager.registerCommand(new ChatCommand(platform, cache, this.localeManager, staffChatService, chatManagementService,
                configManager.getStaffChatConfig(), configManager.getChatManagementConfig()));

        // Register staff list command
        commandManager.registerCommand(new StaffListCommand(platform, cache, this.localeManager, vanishService, httpClientHolder, httpManager.getPanelUrl()));

        // Register 2FA command
        commandManager.registerCommand(new VerifyCommand(platform, cache, this.localeManager, staff2faService, httpClientHolder));

        // Register maintenance command
        commandManager.registerCommand(new MaintenanceCommand(platform, cache, this.localeManager, maintenanceService));

        // Register intercept and log commands
        commandManager.registerCommand(new InterceptNetworkChatCommand(networkChatInterceptService, cache, this.localeManager));
        commandManager.registerCommand(new ChatLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));
        commandManager.registerCommand(new CommandLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager));

        // Register freeze command
        commandManager.registerCommand(new FreezeCommand(platform, cache, this.localeManager, freezeService, bridgeService));

        // Register staff mode and vanish commands
        commandManager.registerCommand(new StaffModeCommand(platform, cache, this.localeManager, staffModeService, vanishService, bridgeService));
        commandManager.registerCommand(new VanishCommand(platform, cache, this.localeManager, vanishService, bridgeService));
        commandManager.registerCommand(new TargetCommand(platform, cache, this.localeManager, staffModeService, bridgeService));

        // Register all command aliases for async execution (everything except ModlReloadCommand's "modl")
        // Built dynamically from configured aliases
        for (Map.Entry<String, String> entry : commandAliases.entrySet()) {
            if (entry.getKey().equals("modl")) continue; // ModlReloadCommand runs synchronously
            if (entry.getValue().isEmpty()) continue; // Command disabled via empty alias
            for (String alias : entry.getValue().split("\\|")) {
                asyncCommandExecutor.registerAsyncAlias(alias.trim());
            }
        }
    }

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient, boolean queryMojang) {
        AbstractPlayer player = platform.getAbstractPlayer(target, false);
        if (player != null) return player;

        try {
            Account account = httpClient.getPlayer(new PlayerNameRequest(target)).join().getPlayer();

            if (account != null) {
                String username = !account.getUsernames().isEmpty()
                        ? account.getUsernames().get(account.getUsernames().size() - 1).getUsername()
                        : target;
                return new AbstractPlayer(account.getMinecraftUuid(), username, false);
            }
        } catch (Exception ignored) {
            // Player not found — expected for lookups of players not in the database
        }

        if (queryMojang)
            return platform.getAbstractPlayer(target, true);

        return null;
    }

    public static Account fetchPlayer(String target, ModlHttpClient httpClient) {
        try {
            PlayerNameResponse response = httpClient.getPlayer(new PlayerNameRequest(target)).join();
            if (response != null && response.isSuccess()) {
                return response.getPlayer();
            }
        } catch (Exception ignored) {
            // Player not found — expected for lookups of players not in the database
        }
        return null;
    }
    
    /**
     * Parse config.yml once into a map. Returns empty map if file missing or invalid.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readConfigYml(Path dataDirectory, Logger logger) {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                return Collections.emptyMap();
            }
            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                return config != null ? config : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("Failed to read config.yml: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Read the locale setting from parsed config, defaulting to "en_US"
     */
    private static String readLocaleFromConfig(Map<String, Object> config, Logger logger) {
        if (config.containsKey("locale")) {
            String locale = (String) config.get("locale");
            if (locale != null && !locale.isEmpty()) {
                logger.info("Using locale: " + locale);
                return locale;
            }
        }
        return "en_US";
    }

    /**
     * Default command aliases used when no config is present.
     */
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

    /**
     * Load command aliases from parsed config, falling back to defaults for missing entries.
     * An empty alias ("") disables the command entirely.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> loadCommandAliases(Map<String, Object> config, Logger logger) {
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_COMMAND_ALIASES);
        try {
            if (config.containsKey("commands")) {
                Map<String, Object> commands = (Map<String, Object>) config.get("commands");
                if (commands != null) {
                    for (Map.Entry<String, Object> entry : commands.entrySet()) {
                        Object rawValue = entry.getValue();
                        if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) {
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

    /**
     * Register command aliases as ACF command replacements.
     * These replacements are used in @CommandAlias("%cmd_xxx") annotations.
     * Disabled commands (empty alias) get an unreachable alias so ACF doesn't error out.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommandReplacements(CommandManager commandManager, Map<String, String> aliases) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (entry.getValue().isEmpty()) {
                // Register an unreachable alias so ACF doesn't fail on unresolved %cmd_ replacements
                commandManager.getCommandReplacements().addReplacement("cmd_" + entry.getKey(), "modl:__disabled_" + entry.getKey());
            } else {
                commandManager.getCommandReplacements().addReplacement("cmd_" + entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Load locale config values from parsed config and pass to LocaleManager
     */
    @SuppressWarnings("unchecked")
    private void loadLocaleConfig(Map<String, Object> config, Logger logger) {
        try {
            if (config.containsKey("locale_config")) {
                Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                this.localeManager.setConfigValues(localeConfig);

                // Propagate date format and timezone to static utility classes
                String dateFormat = this.localeManager.getDateFormatPattern();
                gg.modl.minecraft.core.impl.menus.util.MenuItems.setDateFormat(dateFormat);
                gg.modl.minecraft.core.util.DateFormatter.setDateFormat(dateFormat);
                gg.modl.minecraft.core.util.PunishmentMessages.setDateFormat(dateFormat);

                String timezone = (String) localeConfig.getOrDefault("timezone", "");
                if (timezone != null && !timezone.isEmpty()) {
                    gg.modl.minecraft.core.impl.menus.util.MenuItems.setTimezone(timezone);
                    gg.modl.minecraft.core.util.DateFormatter.setTimezone(timezone);
                    gg.modl.minecraft.core.util.PunishmentMessages.setTimezone(timezone);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale config: " + e.getMessage());
        }
    }

    /**
     * Load database configuration from parsed config
     */
    @SuppressWarnings("unchecked")
    private DatabaseConfig loadDatabaseConfig(Map<String, Object> config, Path dataDirectory, Logger logger) {
        try {
            if (config.containsKey("migration")) {
                Map<String, Object> migration = (Map<String, Object>) config.get("migration");
                Map<String, Object> litebans = (Map<String, Object>) migration.get("litebans");
                Map<String, Object> database = (Map<String, Object>) litebans.get("database");

                String host = (String) database.getOrDefault("host", "localhost");
                int port = (int) database.getOrDefault("port", 3306);
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
                    logger.info("[Migration] Detected LiteBans table prefix from config: " + tablePrefix);
                }

                logger.info("[Migration] Loaded database config: " + type + " @ " + host + ":" + port + "/" + dbName);
                logger.info("[Migration] Using table prefix: " + tablePrefix);

                return new DatabaseConfig(host, port, dbName, username, password, dbType, tablePrefix);
            }
        } catch (Exception e) {
            logger.warning("[Migration] Failed to load database config: " + e.getMessage());
        }

        return createDefaultDatabaseConfig();
    }

    private DatabaseConfig createDefaultDatabaseConfig() {
        return new DatabaseConfig("localhost", 3306, "minecraft", "root", "", DatabaseConfig.DatabaseType.MYSQL, "litebans_");
    }

    /**
     * Try to detect LiteBans table prefix from LiteBans config.yml
     */
    private String detectLiteBansTablePrefix(Path dataDirectory, Logger logger) {
        try {
            // LiteBans config is in plugins/LiteBans/config.yml
            Path litebansConfig = dataDirectory.getParent().resolve("LiteBans").resolve("config.yml");
            
            if (!Files.exists(litebansConfig)) {
                logger.info("[Migration] LiteBans config not found, using prefix from modl.gg config");
                return null;
            }
            
            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(litebansConfig.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                
                if (config != null && config.containsKey("sql")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sql = (Map<String, Object>) config.get("sql");
                    
                    if (sql.containsKey("table_prefix")) {
                        String prefix = (String) sql.get("table_prefix");
                        if (prefix != null && !prefix.isEmpty()) {
                            return prefix;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[Migration] Failed to read LiteBans config: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Stop all services (should be called on plugin disable)
     */
    public void shutdown() {
        if (updateCheckerService != null) {
            updateCheckerService.stop();
        }
        if (syncService != null) {
            syncService.stop();
        }
        if (loginCache != null) {
            loginCache.shutdown();
        }
        if (asyncCommandExecutor != null) {
            asyncCommandExecutor.shutdown();
        }
    }

    private void reloadRuntimeConfiguration() {
        configManager.reloadAll();
        Map<String, Object> freshConfig = readConfigYml(this.dataDirectory, this.logger);
        UpdateCheckerConfig updateCheckerConfig = loadUpdateCheckerConfig(freshConfig, this.logger);
        updateCheckerService.reload(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);
    }

    @SuppressWarnings("unchecked")
    private static UpdateCheckerConfig loadUpdateCheckerConfig(Map<String, Object> config, Logger logger) {
        boolean enabled = true;
        int intervalMinutes = UpdateCheckerService.getDefaultIntervalMinutes();

        try {
            if (config.containsKey("update_checker")) {
                Object updateCheckerNode = config.get("update_checker");
                if (updateCheckerNode instanceof Map) {
                    Map<String, Object> updateChecker = (Map<String, Object>) updateCheckerNode;

                    Object enabledValue = updateChecker.get("enabled");
                    if (enabledValue instanceof Boolean) {
                        enabled = (Boolean) enabledValue;
                    } else if (enabledValue instanceof String) {
                        enabled = Boolean.parseBoolean((String) enabledValue);
                    }

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
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
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

    /**
     * Register ACF command conditions for permission checks.
     * These conditions can be used with @Conditions annotation on commands.
     *
     * Available conditions:
     * - "staff" - Requires the issuer to be a staff member
     * - "permission:X" - Requires the issuer to have permission X (e.g., "permission:punishment.apply.ban")
     * - "player" - Requires the issuer to be a player (not console)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommandConditions(CommandManager commandManager, Cache cache, LocaleManager localeManager, Staff2faService staff2faService) {
        // "staff" condition - checks if the issuer is a staff member and 2FA authenticated
        commandManager.getCommandConditions().addCondition("staff", context -> {
            if (!context.getIssuer().isPlayer()) {
                return; // Console is always staff
            }
            if (!PermissionUtil.isStaff(context.getIssuer(), cache)) {
                throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
            }
            // Check 2FA if enabled
            if (staff2faService != null && staff2faService.isEnabled() && !staff2faService.isAuthenticated(context.getIssuer().getUniqueId())) {
                throw new ConditionFailedException(localeManager.getMessage("staff_2fa.not_verified"));
            }
        });

        // "staff_no2fa" condition - checks staff membership without 2FA (for /verify command)
        commandManager.getCommandConditions().addCondition("staff_no2fa", context -> {
            if (!context.getIssuer().isPlayer()) {
                return; // Console is always staff
            }
            if (!PermissionUtil.isStaff(context.getIssuer(), cache)) {
                throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
            }
        });

        // "player" condition - checks if the issuer is a player (not console)
        commandManager.getCommandConditions().addCondition("player", context -> {
            if (!context.getIssuer().isPlayer()) {
                throw new ConditionFailedException(localeManager.getMessage("iammuted.only_players"));
            }
        });

        // "permission:X" condition - checks if the issuer has a specific permission
        // Usage: @Conditions("permission:punishment.apply.ban")
        commandManager.getCommandConditions().addCondition("permission", context -> {
            if (!context.getIssuer().isPlayer()) {
                return; // Console always has permission
            }
            String permission = context.getConfigValue("value", "");
            if (permission.isEmpty()) {
                return; // No permission specified, allow
            }
            // Check permission first so non-staff get "no permission" instead of "verify"
            if (!PermissionUtil.hasPermission(context.getIssuer(), cache, permission)) {
                String message;
                if (permission.startsWith("punishment.apply.")) {
                    String type = permission.replace("punishment.apply.", "").replace("-", " ");
                    message = localeManager.getPunishmentMessage("general.no_permission_punishment",
                            Map.of("type", type));
                } else {
                    message = localeManager.getMessage("general.no_permission");
                }
                throw new ConditionFailedException(message);
            }
            // Check 2FA after permission (only staff with the permission need to verify)
            if (staff2faService != null && staff2faService.isEnabled() && !staff2faService.isAuthenticated(context.getIssuer().getUniqueId())) {
                throw new ConditionFailedException(localeManager.getMessage("staff_2fa.not_verified"));
            }
        });

        // "admin" condition - checks if the issuer has admin permissions
        commandManager.getCommandConditions().addCondition("admin", context -> {
            if (!context.getIssuer().isPlayer()) {
                return; // Console is always admin
            }
            if (!PermissionUtil.hasAnyPermission(context.getIssuer(), cache, "admin.settings.view", "admin.settings.modify", "admin.reload")) {
                throw new ConditionFailedException(localeManager.getMessage("general.no_permission"));
            }
        });
    }

    private static void initializeStaffPermissions(ModlHttpClient httpClient, Cache cache, Logger logger, boolean debugMode) {
        if (debugMode) {
            logger.info("Initializing staff permissions cache...");
        }
        httpClient.getStaffPermissions().thenAccept(response -> {
            int loadedCount = 0;
            for (var staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffUsername(), staffMember.getStaffRole(), staffMember.getPermissions());
                        loadedCount++;
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID for staff member: " + staffMember.getMinecraftUuid());
                    }
                }
            }
            if (debugMode) {
                logger.info("Staff permissions initialized: " + loadedCount + " staff members cached");
            }
        }).exceptionally(throwable -> {
            logger.warning("Failed to initialize staff permissions: " + throwable.getMessage());
            return null;
        });
    }
}