package gg.modl.minecraft.core;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.dynamic.Annotations;
import revxrsal.commands.annotation.list.AnnotationList;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.exception.SendableException;
import revxrsal.commands.parameter.ParameterType;
import revxrsal.commands.parameter.StringParameterType;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.config.ConfigManager;
import gg.modl.minecraft.core.config.RuntimeConfigSource;
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
import gg.modl.minecraft.core.command.AdminOnly;
import gg.modl.minecraft.core.command.ConfiguredCommandAliases;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.PlayerQuerySuggestions;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffNo2fa;
import gg.modl.minecraft.core.command.StaffOnly;
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
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.locale.MessageRenderer;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.realtime.MinecraftRealtimeClient;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.UpdateCheckerService;
import gg.modl.minecraft.core.service.VanishService;
import static gg.modl.minecraft.core.util.Java8Collections.entry;
import static gg.modl.minecraft.core.util.Java8Collections.mapOfEntries;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.PlayerLookupUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentActionMessages;
import gg.modl.minecraft.core.util.PunishmentTypeCacheManager;
import gg.modl.minecraft.core.util.StaffPermissionLoader;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.lang.annotation.Annotation;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import gg.modl.minecraft.core.util.PluginLogger;

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
    private final HttpManager httpManager;
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
    private final MinecraftRealtimeClient realtimeClient;
    private final boolean queryMojang, debugMode;

    public ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public PluginLoader(Platform platform, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager, int syncPollingRateSeconds) {
        this.dataDirectory = dataDirectory;
        this.httpManager = httpManager;
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

        RuntimeConfigSource runtimeConfigSource = configManager.getRuntimeConfigSource();
        Map<String, Object> configYml = runtimeConfigSource.root();
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
        this.realtimeClient = startRealtimeClientIfEnabled(platform, httpManager);

        UpdateCheckerConfig updateCheckerConfig = loadUpdateCheckerConfig(configYml, logger);
        this.updateCheckerService = new UpdateCheckerService(logger, this.debugMode, PluginInfo.VERSION);
        this.updateCheckerService.start(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);

        IpLookupConfig ipLookupConfig = loadIpLookupConfig(configYml, logger);
        IpApiClient.initialize(ipLookupConfig.enabled, ipLookupConfig.url);

        Map<String, String> commandAliases = loadCommandAliases(configYml, logger);
        ConfiguredCommandAliases configuredCommandAliases = new ConfiguredCommandAliases(commandAliases);
        PunishmentActionMessages.setCommandPath(configuredCommandAliases.primaryAlias("punishment_action"));

        this.lamp = platform.buildLamp(builder -> {
            builder.annotationReplacer(Command.class, (element, annotation) ->
                remapCommandAnnotation(annotation, configuredCommandAliases));
            builder.commandCondition(context ->
                enforceCommandAccess(context.command().annotations(), context.actor()));
            builder.suggestionProviders(suggestionProviders -> suggestionProviders.addProviderFactoryLast((type, annotations, lamp) -> {
                if (type instanceof Class) {
                    Class<?> clazz = (Class<?>) type;
                    if (clazz == AbstractPlayer.class || clazz == Account.class) {
                        return PlayerQuerySuggestions.onlinePlayerNames(platform);
                    }
                }

                if (!(type instanceof Class) || type != String.class) {
                    return null;
                }

                Named named = annotations.get(Named.class);
                if (named == null || !"player".equals(named.value())) {
                    return null;
                }

                return PlayerQuerySuggestions.onlinePlayerNames(platform);
            }));
            builder.parameterTypes(types -> {
                types.addParameterTypeFactory(new ParameterType.Factory<CommandActor>() {
                    @Override
                    public <T> ParameterType<CommandActor, T> create(Type type, AnnotationList annotations, Lamp<CommandActor> lamp) {
                        if (!(type instanceof Class) || type != String.class) return null;
                        if (!annotations.contains(ConsumeRemaining.class)) return null;
                        @SuppressWarnings("unchecked")
                        ParameterType<CommandActor, T> greedyString = (ParameterType<CommandActor, T>) StringParameterType.greedy();
                        return greedyString;
                    }
                });
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

        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager);
        if (registerIfEnabled(configuredCommandAliases, punishCommand, "punish")) {
            punishCommand.initializePunishmentTypes();
            syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);
        }

        initializeStaffPermissions(httpManager.getHttpClient(), cache, logger, httpManager.isDebugHttp());

        registerIfEnabled(configuredCommandAliases, new ModlHelpCommand(cache, this.localeManager), "modl");
        registerIfEnabled(configuredCommandAliases, new ModlReloadCommand(this.localeManager, this::reloadRuntimeConfiguration), "modl");
        registerIfEnabled(configuredCommandAliases, new BanCommand(httpClientHolder, platform, cache, this.localeManager), "ban");
        registerIfEnabled(configuredCommandAliases, new MuteCommand(httpClientHolder, platform, cache, this.localeManager), "mute");
        registerIfEnabled(configuredCommandAliases, new KickCommand(httpClientHolder, platform, cache, this.localeManager), "kick");
        registerIfEnabled(configuredCommandAliases, new BlacklistCommand(httpClientHolder, platform, cache, this.localeManager), "blacklist");
        registerIfEnabled(configuredCommandAliases, new PardonCommand(httpClientHolder, platform, cache, this.localeManager), "pardon", "unban", "unmute");
        registerIfEnabled(configuredCommandAliases, new WarnCommand(httpClientHolder, platform, cache, this.localeManager), "warn");
        registerIfEnabled(configuredCommandAliases, new IAmMutedCommand(platform, cache, this.localeManager), "iammuted");
        registerIfEnabled(configuredCommandAliases, new StandingCommand(httpClientHolder, platform, this.localeManager, configManager, cache), "standing");

        TicketCommandUtil ticketUtil = new TicketCommandUtil(cache);
        ModlHttpClient httpClient = httpManager.getHttpClient();
        String panelUrl = httpManager.getPanelUrl();
        registerIfEnabled(configuredCommandAliases, new ReportCommand(asyncCommandExecutor, platform, httpClient, panelUrl, this.localeManager, chatMessageCache, ticketUtil), "report");
        registerIfEnabled(configuredCommandAliases, new ChatReportCommand(platform, httpClient, panelUrl, this.localeManager, chatMessageCache, ticketUtil), "chatreport");
        registerIfEnabled(configuredCommandAliases, new HackReportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil), "hackreport");
        registerIfEnabled(configuredCommandAliases, new ApplyCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil), "apply");
        registerIfEnabled(configuredCommandAliases, new BugReportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil), "bugreport");
        registerIfEnabled(configuredCommandAliases, new SupportCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil), "support");
        registerIfEnabled(configuredCommandAliases, new ClaimTicketCommand(platform, httpClient, panelUrl, this.localeManager, ticketUtil), "tclaim");

        PunishmentTypeCacheManager punishmentTypeCache = new PunishmentTypeCacheManager();
        punishmentTypeCache.initialize(httpManager.getHttpClient(), logger);
        syncService.addPunishmentTypesListener(punishmentTypeCache::update);

        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl(), punishmentTypeCache);
        registerIfEnabled(configuredCommandAliases, inspectCommand, "inspect");
        registerIfEnabled(configuredCommandAliases, new StaffCommand(asyncCommandExecutor, httpClientHolder, platform, cache,
            httpManager.getPanelUrl()), "staffmenu");
        HistoryCommand historyCommand = new HistoryCommand(httpClientHolder, platform, cache, this.localeManager, punishmentTypeCache);
        registerIfEnabled(configuredCommandAliases, historyCommand, "history");
        registerIfEnabled(configuredCommandAliases, new AltsCommand(httpClientHolder, platform, cache, this.localeManager), "alts");
        registerIfEnabled(configuredCommandAliases, new NotesCommand(httpClientHolder, platform, cache, this.localeManager), "notes");
        registerIfEnabled(configuredCommandAliases, new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()), "reports");
        registerIfEnabled(configuredCommandAliases, new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()), "punishment_action");

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

        registerIfEnabled(configuredCommandAliases, new StaffChatCommand(platform, cache, this.localeManager, staffChatService, configManager.getStaffChatConfig()), "staffchat");
        registerIfEnabled(configuredCommandAliases, new LocalChatCommand(platform, cache, this.localeManager, staffChatService), "localchat");
        registerIfEnabled(configuredCommandAliases, new ChatCommand(platform, cache, this.localeManager, staffChatService, chatManagementService,
                configManager.getStaffChatConfig(), configManager.getChatManagementConfig()), "chat");
        registerIfEnabled(configuredCommandAliases, new StaffListCommand(platform, cache, this.localeManager, vanishService, httpClientHolder, httpManager.getPanelUrl()), "stafflist");
        registerIfEnabled(configuredCommandAliases, new VerifyCommand(platform, this.localeManager, staff2faService, httpClientHolder), "verify");
        registerIfEnabled(configuredCommandAliases, new InterceptNetworkChatCommand(networkChatInterceptService, cache, this.localeManager), "interceptnetworkchat");
        registerIfEnabled(configuredCommandAliases, new ChatLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager), "chatlogs");
        registerIfEnabled(configuredCommandAliases, new CommandLogsCommand(httpClientHolder, chatCommandLogService, cache, this.localeManager), "commandlogs");
        registerIfEnabled(configuredCommandAliases, new FreezeCommand(platform, cache, this.localeManager, freezeService, bridgeService), "freeze");
        registerIfEnabled(configuredCommandAliases, new StaffModeCommand(platform, cache, this.localeManager, staffModeService, vanishService, bridgeService), "staffmode");
        registerIfEnabled(configuredCommandAliases, new VanishCommand(platform, cache, this.localeManager, vanishService, bridgeService), "vanish");
        registerIfEnabled(configuredCommandAliases, new TargetCommand(platform, cache, this.localeManager, staffModeService, bridgeService), "target");
        registerIfEnabled(configuredCommandAliases, new ReplayCommand(platform, cache, this.localeManager, httpManager.getPanelUrl()), "replay");

        registerAsyncAliases(configuredCommandAliases,
            "punish", "ban", "mute", "kick", "blacklist", "pardon", "unban", "unmute", "warn",
            "inspect", "staffmenu", "history", "alts", "notes", "reports", "iammuted",
            "report", "chatreport", "hackreport", "apply", "bugreport", "support", "tclaim", "standing",
            "punishment_action", "staffchat", "localchat", "chat", "stafflist", "freeze",
            "staffmode", "vanish", "target", "verify", "interceptnetworkchat", "chatlogs",
            "commandlogs", "replay");
        platform.finalizeLampRegistration(lamp);
    }

    public static AbstractPlayer fetchPlayer(String target, Platform platform, ModlHttpClient httpClient, boolean queryMojang) {
        return PlayerLookupUtil.fetchPlayer(target, platform, httpClient, queryMojang);
    }

    public static Account fetchPlayer(String target, ModlHttpClient httpClient) {
        return PlayerLookupUtil.fetchAccount(target, httpClient);
    }

    private static String readLocaleFromConfig(Map<String, Object> config, PluginLogger logger) {
        if (config.containsKey("locale")) {
            String locale = (String) config.get("locale");
            if (locale != null && !locale.isEmpty()) {
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

    private static Collection<Annotation> remapCommandAnnotation(Command annotation, ConfiguredCommandAliases commandAliases) {
        String[] remappedValues = commandAliases.resolveCommandValues(annotation.value());
        if (Arrays.equals(annotation.value(), remappedValues)) return Collections.singletonList(annotation);
        if (remappedValues.length == 0) return Collections.emptyList();
        return Collections.singletonList(Annotations.create(Command.class, "value", remappedValues));
    }

    private boolean registerIfEnabled(ConfiguredCommandAliases commandAliases, Object command, String... keys) {
        if (!commandAliases.anyEnabled(keys)) return false;
        lamp.register(command);
        return true;
    }

    private void registerAsyncAliases(ConfiguredCommandAliases commandAliases, String... keys) {
        for (String key : keys) {
            for (String alias : commandAliases.aliasesFor(key)) {
                asyncCommandExecutor.registerAsyncAlias(alias);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLocaleConfig(Map<String, Object> config, PluginLogger logger) {
        try {
            if (config.containsKey("locale_config")) {
                Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                this.localeManager.setConfigValues(localeConfig);

                String dateFormat = this.localeManager.getDateFormatPattern();
                DateFormatter.setDateFormat(dateFormat);

                String timezone = (String) localeConfig.getOrDefault("timezone", "");
                DateFormatter.setTimezone(timezone);
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale config: " + e.getMessage());
        }
    }

    static final String LITEBANS_HOST_PLACEHOLDER = "litebans-database-host";
    static final String LITEBANS_DATABASE_PLACEHOLDER = "litebans_database";
    static final String LITEBANS_USERNAME_PLACEHOLDER = "litebans_user";
    static final String LITEBANS_PASSWORD_PLACEHOLDER = "change-me";

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

            int port = parseIntegerValue(database.getOrDefault("port", 3306), 3306);
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

    private DatabaseConfig createDefaultDatabaseConfig() {
        return new DatabaseConfig(LITEBANS_HOST_PLACEHOLDER, LITEBANS_DATABASE_PLACEHOLDER,
                LITEBANS_USERNAME_PLACEHOLDER, LITEBANS_PASSWORD_PLACEHOLDER,
                DatabaseConfig.DatabaseType.MYSQL, "litebans_", 3306);
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

    public void shutdown() {
        if (realtimeClient != null) realtimeClient.stop();
        if (updateCheckerService != null) updateCheckerService.stop();
        if (syncService != null) syncService.stop();
        if (loginCache != null) loginCache.shutdown();
        if (asyncCommandExecutor != null) asyncCommandExecutor.shutdown();
        IpApiClient.shutdown();
        WebPlayer.shutdown();
        gg.modl.minecraft.core.impl.menus.util.PlayerHeadItemBuilder.shutdown();
        gg.modl.minecraft.core.util.Java8Collections.shutdown();
        if (httpManager != null) httpManager.shutdown();
    }

    private MinecraftRealtimeClient startRealtimeClientIfEnabled(Platform platform, HttpManager httpManager) {
        StartupResponse startupResponse = StartupClient.getLastStartupResponse();
        boolean localEnabled = configManager.getRealtimeConfig() != null && configManager.getRealtimeConfig().isEnabled();
        if (startupResponse != null && !httpManager.getPanelUrl().equals(startupResponse.getPanelUrl())) {
            startupResponse = null;
        }
        if (!MinecraftRealtimeClient.canStart(localEnabled, startupResponse)) {
            if (debugMode && localEnabled) logger.info("[Realtime] Startup response did not enable realtime; staying on HTTP polling only");
            return null;
        }

        MinecraftRealtimeClient client = new MinecraftRealtimeClient(
            httpManager.getApiKey(),
            startupResponse,
            platform,
            syncService,
            logger,
            debugMode
        );
        client.start();
        if (debugMode) logger.info("[Realtime] Client started beside authoritative HTTP polling");
        return client;
    }

    private void reloadRuntimeConfiguration() {
        configManager.reloadAll();
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        Map<String, Object> freshConfig = configManager.getRuntimeConfigSource().root();
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

    @SuppressWarnings("unchecked")
    private static IpLookupConfig loadIpLookupConfig(Map<String, Object> config, PluginLogger logger) {
        boolean enabled = true;
        String url = "https://ipwho.is/{ip}";

        try {
            if (config.containsKey("ip-lookup")) {
                Object node = config.get("ip-lookup");
                if (node instanceof Map) {
                    Map<String, Object> ipLookup = (Map<String, Object>) node;

                    Object enabledValue = ipLookup.get("enabled");
                    if (enabledValue instanceof Boolean) enabled = (Boolean) enabledValue;
                    else if (enabledValue instanceof String) enabled = Boolean.parseBoolean((String) enabledValue);

                    Object urlValue = ipLookup.get("url");
                    if (urlValue instanceof String && !((String) urlValue).isBlank()) {
                        url = (String) urlValue;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load ip-lookup config: " + e.getMessage());
        }

        return new IpLookupConfig(enabled, url);
    }

    private static final class IpLookupConfig {
        private final boolean enabled;
        private final String url;

        private IpLookupConfig(boolean enabled, String url) {
            this.enabled = enabled;
            this.url = url;
        }
    }

    private static void initializeStaffPermissions(ModlHttpClient httpClient, Cache cache, PluginLogger logger, boolean debugMode) {
        StaffPermissionLoader.load(httpClient, cache, logger, debugMode, false);
    }

    private void enforceCommandAccess(AnnotationList annotations, CommandActor actor) {
        if (annotations.contains(PlayerOnly.class) && gg.modl.minecraft.core.util.CommandUtil.isConsole(actor)) {
            throw deny(localeManager.getMessage("general.players_only"));
        }

        if (gg.modl.minecraft.core.util.CommandUtil.isConsole(actor)) {
            return;
        }

        boolean staffOnly = annotations.contains(StaffOnly.class);
        boolean adminOnly = annotations.contains(AdminOnly.class);
        RequiresPermission requiresPermission = annotations.get(RequiresPermission.class);
        boolean staffNo2fa = annotations.contains(StaffNo2fa.class);

        if (staffOnly && !PermissionUtil.isStaff(actor, cache)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (adminOnly && !cache.hasPermission(actor.uniqueId(), Permissions.ADMIN)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (requiresPermission != null && !PermissionUtil.hasPermission(actor, cache, requiresPermission.value())) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        if (staffNo2fa && !PermissionUtil.isStaff(actor, cache)) {
            throw deny(localeManager.getMessage("general.no_permission"));
        }

        // Staff-scoped commands require an AUTHENTICATED 2FA state when 2FA is enabled. The @StaffNo2fa
        // carve-out (applied to /verify) is exempt so staff can complete verification.
        boolean staffScoped = staffOnly || adminOnly || requiresPermission != null;
        if (staffScoped && !staffNo2fa && staff2faService != null
                && staff2faService.isEnabled() && !staff2faService.isAuthenticated(actor.uniqueId())) {
            throw deny(localeManager.getMessage("staff_2fa.not_verified"));
        }
    }

    private static SendableException deny(String message) {
        return new SendableException(message) {
            @Override
            public void sendTo(CommandActor actor) {
                actor.reply(message);
            }
        };
    }
}

