package gg.modl.minecraft.core;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.dynamic.Annotations;
import revxrsal.commands.annotation.list.AnnotationList;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.parameter.ParameterType;
import revxrsal.commands.parameter.StringParameterType;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.PunishmentTypeClassifiers;
import gg.modl.minecraft.api.RegistryPunishmentTypeClassifier;
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
import gg.modl.minecraft.core.command.ConfiguredCommandAliases;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.PlayerQuerySuggestions;
import gg.modl.minecraft.core.impl.commands.player.ApplyCommand;
import gg.modl.minecraft.core.impl.commands.player.BugReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ChatReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ClaimTicketCommand;
import gg.modl.minecraft.core.impl.commands.player.HackReportCommand;
import gg.modl.minecraft.core.impl.commands.player.ReportCommand;
import gg.modl.minecraft.core.impl.commands.player.SupportCommand;
import gg.modl.minecraft.core.service.TicketService;
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
import gg.modl.minecraft.core.impl.menus.util.PlayerHeadItemBuilder;
import gg.modl.minecraft.core.util.Java8Collections;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.service.sync.SyncServiceContext;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.staff.StaffPermissionService;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.integration.iplookup.IpEnrichmentService;
import gg.modl.minecraft.core.integration.iplookup.PendingIpLookupService;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.login.BanEnforcementAcknowledger;
import gg.modl.minecraft.core.login.LoginRequestBuilder;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.login.PlayerNotificationMapper;
import gg.modl.minecraft.core.player.PlayerLookupService;
import gg.modl.minecraft.core.punishment.PunishmentActionMessageService;
import gg.modl.minecraft.core.punishment.PunishmentMessageService;
import gg.modl.minecraft.core.punishment.PunishmentTypeCacheManager;
import gg.modl.minecraft.core.session.PlayerSessionService;
import gg.modl.minecraft.core.session.ServerSwitchService;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import lombok.Getter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
    private final DateFormatter dateFormatter;
    private final PunishmentMessageService punishmentMessageService;
    private final PunishmentActionMessageService punishmentActionMessageService;
    private final PunishmentTypeCacheManager punishmentTypeCacheManager;
    private final IpEnrichmentService ipEnrichmentService;
    private final PendingIpLookupService pendingIpLookupService;
    private final LoginRequestBuilder loginRequestBuilder;
    private final LoginService loginService;
    private final PlayerSessionService playerSessionService;
    private final ServerSwitchService serverSwitchService;
    private final ChatService chatService;
    private final StaffPermissionService staffPermissionService;
    private final PlayerLookupService playerLookupService;

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

        this.configManager = new ConfigManager(dataDirectory, platform.getLogger());
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        this.httpClientHolder = httpManager.getHttpClientHolder();
        this.logger = platform.getLogger();

        RuntimeConfigSource runtimeConfigSource = configManager.getRuntimeConfigSource();
        Map<String, Object> configYml = runtimeConfigSource.root();
        String configuredLocale = PluginConfiguration.readLocaleFromConfig(configYml);

        this.localeManager = new LocaleManager(configuredLocale);
        Path localeFile = dataDirectory.resolve("locale").resolve(configuredLocale + ".yml");
        if (Files.exists(localeFile)) {
            if (httpManager.isDebugHttp()) logger.info("Loading locale from external file: " + localeFile);
            this.localeManager.loadFromFile(localeFile);
        }
        this.localeManager.setRenderer(new MessageRenderer());
        this.dateFormatter = loadLocaleConfig(configYml, logger);
        MenuItems.setDateFormatter(this.dateFormatter);
        this.punishmentMessageService = new PunishmentMessageService(this.localeManager, this.dateFormatter, httpManager.getPanelUrl());

        DatabaseConfig databaseConfig = PluginConfiguration.loadDatabaseConfig(configYml, dataDirectory, logger);

        this.staff2faService = new Staff2faService(cachedProfileRegistry, configManager.getStaff2faConfig());
        this.chatCommandLogService = new ChatCommandLogService();

        Map<String, String> commandAliases = PluginConfiguration.loadCommandAliases(configYml, logger);
        ConfiguredCommandAliases configuredCommandAliases = new ConfiguredCommandAliases(commandAliases);
        this.punishmentActionMessageService = new PunishmentActionMessageService(
                platform, configuredCommandAliases.primaryAlias("punishment_action"));

        this.staffPermissionService = new StaffPermissionService(httpClientHolder, cache, logger, httpManager.isDebugHttp());

        this.syncService = new SyncService(SyncServiceContext.builder()
                .platform(platform)
                .httpClientHolder(httpClientHolder)
                .cache(cache)
                .logger(logger)
                .localeManager(this.localeManager)
                .panelUrl(httpManager.getPanelUrl())
                .pollingRateSeconds(syncPollingRateSeconds)
                .dataFolder(dataDirectory.toFile())
                .databaseConfig(databaseConfig)
                .debugMode(httpManager.isDebugHttp())
                .staff2faService(this.staff2faService)
                .chatCommandLogService(this.chatCommandLogService)
                .punishmentMessageService(this.punishmentMessageService)
                .staffPermissionService(this.staffPermissionService)
                .build());

        if (httpManager.isDebugHttp()) {
            logger.info("modl.gg Configuration:");
            logger.info("  API URL: " + httpManager.getApiUrl());
            logger.info("  API Key: " + (httpManager.getApiKey().length() > 8 ?
                httpManager.getApiKey().substring(0, 8) + "..." : "***"));
            logger.info("  Debug Mode: " + httpManager.isDebugHttp());
        }

        syncService.start();
        this.realtimeClient = startRealtimeClientIfEnabled(platform, httpManager);

        PluginConfiguration.UpdateCheckerConfig updateCheckerConfig = PluginConfiguration.loadUpdateCheckerConfig(configYml, logger);
        this.updateCheckerService = new UpdateCheckerService(logger, this.debugMode, PluginInfo.VERSION);
        this.updateCheckerService.start(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);

        PluginConfiguration.IpLookupConfig ipLookupConfig = PluginConfiguration.loadIpLookupConfig(configYml, logger);
        this.ipEnrichmentService = new IpEnrichmentService(ipLookupConfig.enabled, ipLookupConfig.url);
        this.pendingIpLookupService = new PendingIpLookupService(httpClientHolder, this.ipEnrichmentService, logger);
        this.loginRequestBuilder = new LoginRequestBuilder(logger);

        PlayerLookupService playerLookup = new PlayerLookupService(platform, httpClientHolder, queryMojang);
        this.playerLookupService = playerLookup;

        this.staffChatService = new StaffChatService(cachedProfileRegistry);
        this.chatManagementService = new ChatManagementService(cachedProfileRegistry);
        this.maintenanceService = new MaintenanceService();
        this.networkChatInterceptService = new NetworkChatInterceptService(cachedProfileRegistry);
        this.freezeService = new FreezeService(cachedProfileRegistry);
        this.staffModeService = new StaffModeService(cachedProfileRegistry);
        this.vanishService = new VanishService(cachedProfileRegistry);
        this.bridgeService = new BridgeService();
        ChatInputManager chatInputManager = new ChatInputManager(platform);

        PluginServices pluginServices = new PluginServices(cache, this.localeManager, this.staff2faService,
                this.staffModeService, this.bridgeService, chatInputManager,
                this.punishmentMessageService, this.punishmentActionMessageService);
        PluginServices.install(pluginServices);
        platform.setStaffAudience(pluginServices);

        CommandAccessPolicy accessPolicy = new CommandAccessPolicy(cache, this.localeManager, this.staff2faService);

        this.lamp = platform.buildLamp(builder -> {
            builder.annotationReplacer(Command.class, (element, annotation) ->
                remapCommandAnnotation(annotation, configuredCommandAliases));
            builder.commandCondition(context ->
                accessPolicy.enforce(context.command().annotations(), context.actor()));
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
                    AbstractPlayer player = playerLookup.fetchPlayer(name);
                    if (player == null) throw CommandAccessPolicy.deny(localeManager.getMessage("general.player_not_found"));
                    return player;
                });
                types.addParameterType(Account.class, (input, context) -> {
                    String name = input.readString();
                    Account account = playerLookup.fetchAccount(name);
                    if (account == null) throw CommandAccessPolicy.deny(localeManager.getMessage("general.player_not_found"));
                    return account;
                });
            });
        });

        RegistryPunishmentTypeClassifier punishmentTypeClassifier = new RegistryPunishmentTypeClassifier();
        PunishmentTypeClassifiers.install(punishmentTypeClassifier);
        PunishCommand punishCommand = new PunishCommand(httpClientHolder, platform, cache, this.localeManager,
                punishmentTypeClassifier, this.staffPermissionService);
        if (registerIfEnabled(configuredCommandAliases, punishCommand, "punish")) {
            punishCommand.initializePunishmentTypes();
            syncService.addPunishmentTypesListener(punishCommand::updatePunishmentTypesCache);
        }

        this.staffPermissionService.initialLoad();

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

        ModlHttpClient httpClient = httpManager.getHttpClient();
        String panelUrl = httpManager.getPanelUrl();
        TicketService ticketService = new TicketService(cache, httpClient, platform, this.localeManager, panelUrl);
        pluginServices.setTicketService(ticketService);
        registerIfEnabled(configuredCommandAliases, new ReportCommand(asyncCommandExecutor, platform, httpClient, panelUrl, this.localeManager, chatMessageCache, ticketService), "report");
        registerIfEnabled(configuredCommandAliases, new ChatReportCommand(platform, this.localeManager, chatMessageCache, ticketService), "chatreport");
        registerIfEnabled(configuredCommandAliases, new HackReportCommand(platform, this.localeManager, ticketService), "hackreport");
        registerIfEnabled(configuredCommandAliases, new ApplyCommand(ticketService), "apply");
        registerIfEnabled(configuredCommandAliases, new BugReportCommand(ticketService), "bugreport");
        registerIfEnabled(configuredCommandAliases, new SupportCommand(ticketService), "support");
        registerIfEnabled(configuredCommandAliases, new ClaimTicketCommand(platform, httpClient, panelUrl, this.localeManager, ticketService), "tclaim");

        this.punishmentTypeCacheManager = new PunishmentTypeCacheManager();
        this.punishmentTypeCacheManager.initialize(httpManager.getHttpClient(), logger);
        syncService.addPunishmentTypesListener(this.punishmentTypeCacheManager::update);

        InspectCommand inspectCommand = new InspectCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl(), this.punishmentTypeCacheManager);
        registerIfEnabled(configuredCommandAliases, inspectCommand, "inspect");
        registerIfEnabled(configuredCommandAliases, new StaffCommand(asyncCommandExecutor, httpClientHolder, platform, cache,
            httpManager.getPanelUrl()), "staffmenu");
        HistoryCommand historyCommand = new HistoryCommand(httpClientHolder, platform, cache, this.localeManager, this.punishmentTypeCacheManager);
        registerIfEnabled(configuredCommandAliases, historyCommand, "history");
        registerIfEnabled(configuredCommandAliases, new AltsCommand(httpClientHolder, platform, cache, this.localeManager), "alts");
        registerIfEnabled(configuredCommandAliases, new NotesCommand(httpClientHolder, platform, cache, this.localeManager, this.dateFormatter), "notes");
        registerIfEnabled(configuredCommandAliases, new ReportsCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl(), this.dateFormatter), "reports");
        registerIfEnabled(configuredCommandAliases, new PunishmentActionCommand(httpClientHolder, platform, cache, this.localeManager, httpManager.getPanelUrl()), "punishment_action");

        this.loginService = new LoginService(this.localeManager, syncService, maintenanceService, cache,
                this.punishmentMessageService,
                new BanEnforcementAcknowledger(httpClientHolder, logger, debugMode),
                new PlayerNotificationMapper(logger));
        this.playerSessionService = new PlayerSessionService(platform, cache, this.localeManager, staff2faService,
                syncService, httpClientHolder, loginCache, chatMessageCache, bridgeService, cachedProfileRegistry);
        this.serverSwitchService = new ServerSwitchService(httpClientHolder, cache, this.localeManager, platform);
        this.chatService = new ChatService(platform, cache, this.localeManager, chatMessageCache,
                staffChatService, configManager.getStaffChatConfig(), chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService, this.punishmentMessageService);

        registerIfEnabled(configuredCommandAliases, new StaffChatCommand(platform, cache, this.localeManager, staffChatService, configManager.getStaffChatConfig()), "staffchat");
        registerIfEnabled(configuredCommandAliases, new LocalChatCommand(platform, cache, this.localeManager, staffChatService), "localchat");
        registerIfEnabled(configuredCommandAliases, new ChatCommand(platform, cache, this.localeManager, staffChatService, chatManagementService,
                configManager.getStaffChatConfig(), configManager.getChatManagementConfig()), "chat");
        registerIfEnabled(configuredCommandAliases, new StaffListCommand(platform, cache, this.localeManager, vanishService, httpClientHolder, httpManager.getPanelUrl()), "stafflist");
        registerIfEnabled(configuredCommandAliases, new VerifyCommand(platform, this.localeManager, staff2faService, httpClientHolder), "verify");
        registerIfEnabled(configuredCommandAliases, new InterceptNetworkChatCommand(networkChatInterceptService, this.localeManager), "interceptnetworkchat");
        registerIfEnabled(configuredCommandAliases, new ChatLogsCommand(httpClientHolder, chatCommandLogService, this.localeManager, this.dateFormatter), "chatlogs");
        registerIfEnabled(configuredCommandAliases, new CommandLogsCommand(httpClientHolder, chatCommandLogService, this.localeManager, this.dateFormatter), "commandlogs");
        registerIfEnabled(configuredCommandAliases, new FreezeCommand(platform, cache, this.localeManager, freezeService, bridgeService), "freeze");
        registerIfEnabled(configuredCommandAliases, new StaffModeCommand(platform, cache, this.localeManager, staffModeService, vanishService, bridgeService), "staffmode");
        registerIfEnabled(configuredCommandAliases, new VanishCommand(platform, cache, this.localeManager, vanishService, bridgeService), "vanish");
        registerIfEnabled(configuredCommandAliases, new TargetCommand(platform, cache, this.localeManager, staffModeService, bridgeService), "target");
        registerIfEnabled(configuredCommandAliases, new ReplayCommand(platform, this.localeManager, httpManager.getPanelUrl()), "replay");

        registerAsyncAliases(configuredCommandAliases,
            "punish", "ban", "mute", "kick", "blacklist", "pardon", "unban", "unmute", "warn",
            "inspect", "staffmenu", "history", "alts", "notes", "reports", "iammuted",
            "report", "chatreport", "hackreport", "apply", "bugreport", "support", "tclaim", "standing",
            "punishment_action", "staffchat", "localchat", "chat", "stafflist", "freeze",
            "staffmode", "vanish", "target", "verify", "interceptnetworkchat", "chatlogs",
            "commandlogs", "replay");
        platform.finalizeLampRegistration(lamp);
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
    private DateFormatter loadLocaleConfig(Map<String, Object> config, PluginLogger logger) {
        try {
            if (config.containsKey("locale_config")) {
                Map<String, Object> localeConfig = (Map<String, Object>) config.get("locale_config");
                this.localeManager.setConfigValues(localeConfig);

                String dateFormat = this.localeManager.getDateFormatPattern();
                String timezone = (String) localeConfig.getOrDefault("timezone", "");
                return new DateFormatter(dateFormat, timezone);
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale config: " + e.getMessage());
        }
        return new DateFormatter(DateFormatter.DEFAULT_PATTERN, null);
    }

    public void shutdown() {
        if (realtimeClient != null) realtimeClient.stop();
        if (updateCheckerService != null) updateCheckerService.stop();
        if (syncService != null) syncService.stop();
        if (loginCache != null) loginCache.shutdown();
        if (asyncCommandExecutor != null) asyncCommandExecutor.shutdown();
        if (ipEnrichmentService != null) ipEnrichmentService.shutdown();
        MojangProfiles.shutdown();
        PlayerHeadItemBuilder.shutdown();
        Java8Collections.shutdown();
        if (httpManager != null) httpManager.shutdown();
    }

    private MinecraftRealtimeClient startRealtimeClientIfEnabled(Platform platform, HttpManager httpManager) {
        boolean localEnabled = configManager.getRealtimeConfig() != null && configManager.getRealtimeConfig().isEnabled();
        if (!localEnabled) {
            return null;
        }
        try {
            StartupResponse startupResponse = StartupClient.getLastStartupResponse();
            if (startupResponse != null && !httpManager.getPanelUrl().equals(startupResponse.getPanelUrl())) {
                startupResponse = null;
            }
            if (!MinecraftRealtimeClient.canStart(localEnabled, startupResponse)) {
                if (debugMode) logger.info("[Realtime] Startup response did not enable realtime; staying on HTTP polling only");
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
        } catch (Throwable t) {
            logger.warning("[Realtime] Failed to initialize realtime client; falling back to HTTP polling: " + t);
            return null;
        }
    }

    private void reloadRuntimeConfiguration() {
        configManager.reloadAll();
        cache.setPunishmentTypeItems(configManager.getPunishmentTypeItems());
        Map<String, Object> freshConfig = configManager.getRuntimeConfigSource().root();
        PluginConfiguration.UpdateCheckerConfig updateCheckerConfig = PluginConfiguration.loadUpdateCheckerConfig(freshConfig, this.logger);
        updateCheckerService.reload(updateCheckerConfig.enabled, updateCheckerConfig.intervalMinutes);
    }
}

