package gg.modl.minecraft.core;


import co.aikar.commands.CommandManager;
import co.aikar.commands.ConditionFailedException;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.LoginCache;
import gg.modl.minecraft.core.impl.commands.TicketCommands;
import gg.modl.minecraft.core.impl.commands.PlayerLookupCommand;
import gg.modl.minecraft.core.impl.commands.ModlReloadCommand;
import gg.modl.minecraft.core.impl.commands.InspectCommand;
import gg.modl.minecraft.core.impl.commands.StaffCommand;
import gg.modl.minecraft.core.impl.commands.HistoryCommand;
import gg.modl.minecraft.core.impl.commands.AltsCommand;
import gg.modl.minecraft.core.impl.commands.NotesCommand;
import gg.modl.minecraft.core.impl.commands.ReportsCommand;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.commands.punishments.*;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.sync.SyncService;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

// import static gg.modl.minecraft.core.Constants.QUERY_MOJANG; // Moved to config
@Getter
public class PluginLoader {
    private final ModlHttpClient httpClient;
    private final Cache cache;
    private final SyncService syncService;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache) {
        throw new UnsupportedOperationException("This constructor is deprecated. Use the HttpManager overload instead.");
    }

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.chatMessageCache = chatMessageCache;
        cache = new Cache();
        loginCache = new LoginCache();

        // Set the cache on the platform for menu access
        platform.setCache(cache);

        this.httpClient = httpManager.getHttpClient();

        // Initialize locale manager with support for external locale files
        this.localeManager = new LocaleManager();
        Logger logger = Logger.getLogger("MODL-" + platform.getClass().getSimpleName());

        // Try to load locale from external file if it exists
        Path localeFile = dataDirectory.resolve("locale").resolve("en_US.yml");
        if (Files.exists(localeFile)) {
            logger.info("Loading locale from external file: " + localeFile);
            this.localeManager.loadFromFile(localeFile);
        }

        // Set the locale manager on the platform for menu access
        platform.setLocaleManager(this.localeManager);

        // Load database configuration for migration
        DatabaseConfig databaseConfig = loadDatabaseConfig(dataDirectory, logger);

        // Initialize sync service with configurable polling rate
        this.syncService = new SyncService(platform, httpClient, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey(), syncPollingRateSeconds, dataDirectory.toFile(), databaseConfig);
        
        // Log configuration details
        logger.info("MODL Configuration:");
        logger.info("  API URL: " + httpManager.getApiUrl());
        logger.info("  API Key: " + (httpManager.getApiKey().length() > 8 ? 
            httpManager.getApiKey().substring(0, 8) + "..." : "***"));
        logger.info("  Debug Mode: " + httpManager.isDebugHttp());
        
        // Start sync service
        syncService.start();

        CommandManager<?, ?, ?, ?, ?, ?> commandManager = platform.getCommandManager();
        commandManager.enableUnstableAPI("help");

        commandManager.getCommandContexts().registerContext(AbstractPlayer.class, (c)
                -> fetchPlayer(c.popFirstArg(), platform, httpClient));

        commandManager.getCommandContexts().registerContext(Account.class, (c) -> fetchPlayer(c.popFirstArg(), httpClient));

        // Register ACF command conditions for permission checks
        registerCommandConditions(commandManager, cache, this.localeManager);
//
        // Removed duplicate - TicketCommands registered below with proper panelUrl
        
        // Register player lookup command
        PlayerLookupCommand playerLookupCommand = new PlayerLookupCommand(httpManager.getHttpClient(), platform, cache, this.localeManager, httpManager.getPanelUrl());
        commandManager.registerCommand(playerLookupCommand);
        
        // Register punishment command with tab completion
        PunishCommand punishCommand = new PunishCommand(httpManager.getHttpClient(), platform, cache, this.localeManager);
        commandManager.registerCommand(punishCommand);
        
        // Set up punishment types tab completion
        commandManager.getCommandCompletions().registerCompletion("punishment-types", c -> 
            punishCommand.getPunishmentTypeNames()
        );
        
        // Initialize punishment types cache
        punishCommand.initializePunishmentTypes();

        // Initialize punishment types cache for player lookup
        playerLookupCommand.initializePunishmentTypes();

        // Register punishment types listeners for auto-refresh
        syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);
        syncService.addPunishmentTypesListener(playerLookupCommand::updatePunishmentTypesCache);

        // Initialize staff permissions cache at startup
        initializeStaffPermissions(httpManager.getHttpClient(), cache, logger);

        // Register reload command
        commandManager.registerCommand(new ModlReloadCommand(platform, cache, this.localeManager));
        
        // Register manual punishment commands
        commandManager.registerCommand(new BanCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new MuteCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new KickCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new BlacklistCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new PardonCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new WarnCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        
        // Register player commands
        commandManager.registerCommand(new IAmMutedCommand(platform, cache, this.localeManager));
        commandManager.registerCommand(new TicketCommands(platform, httpManager.getHttpClient(), httpManager.getPanelUrl(), this.localeManager, chatMessageCache));

        // Register GUI commands
        commandManager.registerCommand(new InspectCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new StaffCommand(httpManager.getHttpClient(), platform, cache, this.localeManager, httpManager.getPanelUrl()));
        commandManager.registerCommand(new HistoryCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new AltsCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new NotesCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new ReportsCommand(httpManager.getHttpClient(), platform, cache, this.localeManager, httpManager.getPanelUrl()));

    }

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient) {
        AbstractPlayer player = platform.getAbstractPlayer(target, false);
        if (player != null) return player;

        Account account = httpClient.getPlayer(new PlayerNameRequest(target)).join().getPlayer();

        if (account != null)
            return new AbstractPlayer(account.getMinecraftUuid(), "test", false);

        // Note: QUERY_MOJANG moved to config - for now defaulting to false
        // if (account == null && queryMojang)
        //     return platform.getAbstractPlayer(target, true);

        return null;
    }

    public static Account fetchPlayer(String target, ModlHttpClient httpClient) {
        try {
            PlayerNameResponse response = httpClient.getPlayer(new PlayerNameRequest(target)).join();
            if (response != null && response.isSuccess()) {
                return response.getPlayer();
            }
        } catch (Exception e) {
            // Log error but don't crash - return null to indicate player not found
            System.err.println("[MODL] Error fetching player by name '" + target + "': " + e.getMessage());
        }
        return null;
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
                throw new ConditionFailedException("This command can only be used by players.");
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

    private static void initializeStaffPermissions(ModlHttpClient httpClient, Cache cache, Logger logger) {
        logger.info("Initializing staff permissions cache...");
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
            logger.info("Staff permissions initialized: " + loadedCount + " staff members cached");
        }).exceptionally(throwable -> {
            logger.warning("Failed to initialize staff permissions: " + throwable.getMessage());
            return null;
        });
    }
}