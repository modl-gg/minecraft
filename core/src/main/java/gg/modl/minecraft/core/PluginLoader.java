package gg.modl.minecraft.core;


import co.aikar.commands.CommandManager;
import co.aikar.commands.ConditionFailedException;
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
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.PermissionUtil;
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
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final boolean queryMojang;
    private final AsyncCommandExecutor asyncCommandExecutor;

    /**
     * Get the current HTTP client from the holder.
     * Use this for components that need the client but don't need dynamic switching.
     */
    public ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache) {
        throw new UnsupportedOperationException("This constructor is deprecated. Use the HttpManager overload instead.");
    }

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.chatMessageCache = chatMessageCache;
        this.queryMojang = httpManager.isQueryMojang();
        this.asyncCommandExecutor = new AsyncCommandExecutor();
        cache = new Cache();
        cache.setQueryMojang(httpManager.isQueryMojang());
        loginCache = new LoginCache();

        // Set the cache on the platform for menu access
        platform.setCache(cache);

        this.httpClientHolder = httpManager.getHttpClientHolder();

        // Read configured locale from config.yml
        Logger logger = Logger.getLogger("MODL-" + platform.getClass().getSimpleName());
        String configuredLocale = readLocaleFromConfig(dataDirectory, logger);

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

        // Load locale config values from config.yml
        loadLocaleConfig(dataDirectory, logger);

        // Set panel URL on locale manager (derived from api.url)
        this.localeManager.setPanelUrl(httpManager.getPanelUrl());

        // Set the locale manager on the platform for menu access
        platform.setLocaleManager(this.localeManager);

        // Load database configuration for migration
        DatabaseConfig databaseConfig = loadDatabaseConfig(dataDirectory, logger);

        // Initialize sync service with configurable polling rate
        this.syncService = new SyncService(platform, httpClientHolder, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey(), syncPollingRateSeconds, dataDirectory.toFile(), databaseConfig,
                httpManager.isDebugHttp());
        
        // Log configuration details (only in debug mode)
        if (httpManager.isDebugHttp()) {
            logger.info("MODL Configuration:");
            logger.info("  API URL: " + httpManager.getApiUrl());
            logger.info("  API Key: " + (httpManager.getApiKey().length() > 8 ?
                httpManager.getApiKey().substring(0, 8) + "..." : "***"));
            logger.info("  Debug Mode: " + httpManager.isDebugHttp());
        }
        
        // Start sync service
        syncService.start();

        CommandManager<?, ?, ?, ?, ?, ?> commandManager = platform.getCommandManager();
        commandManager.enableUnstableAPI("help");

        // Load and register command aliases from config before registering commands
        Map<String, String> commandAliases = loadCommandAliases(dataDirectory, logger);
        registerCommandReplacements(commandManager, commandAliases);

        commandManager.getCommandContexts().registerContext(AbstractPlayer.class, (c)
                -> fetchPlayer(c.popFirstArg(), platform, getHttpClient(), queryMojang));

        commandManager.getCommandContexts().registerContext(Account.class, (c) -> fetchPlayer(c.popFirstArg(), getHttpClient()));

        // Register ACF command conditions for permission checks
        registerCommandConditions(commandManager, cache, this.localeManager);
//
        // Removed duplicate - TicketCommands registered below with proper panelUrl

        // Register punishment command with tab completion
        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager);
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
        commandManager.registerCommand(new ModlReloadCommand(platform, cache, this.localeManager));

        // Register manual punishment commands
        commandManager.registerCommand(new BanCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new MuteCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new KickCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new BlacklistCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new PardonCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new WarnCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));

        // Register player commands
        commandManager.registerCommand(new IAmMutedCommand(platform, cache, this.localeManager));
        commandManager.registerCommand(new StandingCommand(httpClientHolder, platform, this.localeManager));
        commandManager.registerCommand(new TicketCommands(platform, httpClientHolder, httpManager.getHttpClient(), httpManager.getPanelUrl(), this.localeManager, chatMessageCache));

        // Register GUI commands (menus require V2 API - commands will check dynamically via holder)
        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl());
        commandManager.registerCommand(inspectCommand);
        inspectCommand.initializePunishmentTypes();
        syncService.addPunishmentTypesListener(inspectCommand::updatePunishmentTypesCache);
        commandManager.registerCommand(new StaffCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));
        commandManager.registerCommand(new HistoryCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new AltsCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new NotesCommand(httpClientHolder, platform, cache, this.localeManager));
        commandManager.registerCommand(new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));
        commandManager.registerCommand(new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()));

        // Register all command aliases for async execution (everything except ModlReloadCommand's "modl")
        // Built dynamically from configured aliases
        for (Map.Entry<String, String> entry : commandAliases.entrySet()) {
            if (entry.getKey().equals("modl")) continue; // ModlReloadCommand runs synchronously
            for (String alias : entry.getValue().split("\\|")) {
                asyncCommandExecutor.registerAsyncAlias(alias.trim());
            }
        }
    }

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient, boolean queryMojang) {
        AbstractPlayer player = platform.getAbstractPlayer(target, false);
        if (player != null) return player;

        Account account = httpClient.getPlayer(new PlayerNameRequest(target)).join().getPlayer();

        if (account != null)
            return new AbstractPlayer(account.getMinecraftUuid(), "test", false);

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
     * Read the locale setting from config.yml, defaulting to "en_US"
     */
    @SuppressWarnings("unchecked")
    private static String readLocaleFromConfig(Path dataDirectory, Logger logger) {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                return "en_US";
            }
            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                if (config != null && config.containsKey("locale")) {
                    String locale = (String) config.get("locale");
                    if (locale != null && !locale.isEmpty()) {
                        logger.info("Using locale: " + locale);
                        return locale;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to read locale from config: " + e.getMessage());
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
            Map.entry("apply", "apply"),
            Map.entry("bugreport", "bugreport"),
            Map.entry("support", "support"),
            Map.entry("tclaim", "tclaim|claimticket"),
            Map.entry("standing", "standing"),
            Map.entry("punishment_action", "modl:punishment-action")
    );

    /**
     * Load command aliases from config.yml, falling back to defaults for missing entries.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> loadCommandAliases(Path dataDirectory, Logger logger) {
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_COMMAND_ALIASES);
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                return aliases;
            }
            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                if (config != null && config.containsKey("commands")) {
                    Map<String, Object> commands = (Map<String, Object>) config.get("commands");
                    if (commands != null) {
                        for (Map.Entry<String, Object> entry : commands.entrySet()) {
                            String value = String.valueOf(entry.getValue());
                            if (value != null && !value.isEmpty()) {
                                aliases.put(entry.getKey(), value);
                            }
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
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommandReplacements(CommandManager commandManager, Map<String, String> aliases) {
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            commandManager.getCommandReplacements().addReplacement("cmd_" + entry.getKey(), entry.getValue());
        }
    }

    /**
     * Load locale config values from config.yml and pass to LocaleManager
     */
    @SuppressWarnings("unchecked")
    private void loadLocaleConfig(Path dataDirectory, Logger logger) {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                logger.info("Config file not found, using default locale config values");
                return;
            }

            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);

                if (config != null && config.containsKey("locale_config")) {
                    Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                    this.localeManager.setConfigValues(localeConfig);

                    // Propagate date format to static utility classes
                    String dateFormat = this.localeManager.getDateFormatPattern();
                    gg.modl.minecraft.core.impl.menus.util.MenuItems.setDateFormat(dateFormat);
                    gg.modl.minecraft.core.util.DateFormatter.setDateFormat(dateFormat);
                    gg.modl.minecraft.core.util.PunishmentMessages.setDateFormat(dateFormat);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale config: " + e.getMessage());
        }
    }

    /**
     * Load database configuration from config.yml
     */
    private DatabaseConfig loadDatabaseConfig(Path dataDirectory, Logger logger) {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                logger.warning("[Migration] Config file not found, using default database config");
                return createDefaultDatabaseConfig();
            }

            Yaml yaml = new Yaml();
            try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
                Map<String, Object> config = yaml.load(inputStream);
                
                if (config != null && config.containsKey("migration")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> migration = (Map<String, Object>) config.get("migration");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> litebans = (Map<String, Object>) migration.get("litebans");
                    @SuppressWarnings("unchecked")
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
            }
        } catch (Exception e) {
            logger.warning("[Migration] Failed to load database config: " + e.getMessage());
            e.printStackTrace();
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
                logger.info("[Migration] LiteBans config not found, using prefix from MODL config");
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
    private static void registerCommandConditions(CommandManager commandManager, Cache cache, LocaleManager localeManager) {
        // "staff" condition - checks if the issuer is a staff member
        commandManager.getCommandConditions().addCondition("staff", context -> {
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
            String permission = context.getConfigValue("value", "");
            if (permission.isEmpty()) {
                return; // No permission specified, allow
            }
            if (!PermissionUtil.hasPermission(context.getIssuer(), cache, permission)) {
                // Try to get a nicer message based on permission type
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
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffRole(), staffMember.getPermissions());
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