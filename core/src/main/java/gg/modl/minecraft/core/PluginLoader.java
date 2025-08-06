package gg.modl.minecraft.core;


import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.commands.TicketCommands;
import gg.modl.minecraft.core.impl.commands.PlayerLookupCommand;
import gg.modl.minecraft.core.impl.commands.ModlReloadCommand;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.commands.punishments.*;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.sync.SyncService;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

// import static gg.modl.minecraft.core.Constants.QUERY_MOJANG; // Moved to config
@Getter
public class PluginLoader {
    private final ModlHttpClient httpClient;
    private final Cache cache;
    private final SyncService syncService;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache) {
        throw new UnsupportedOperationException("This constructor is deprecated. Use the HttpManager overload instead.");
    }

    public PluginLoader(Platform platform, PlatformCommandRegister commandRegister, Path dataDirectory, ChatMessageCache chatMessageCache, HttpManager httpManager) {
        this.chatMessageCache = chatMessageCache;
        cache = new Cache();

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

        // Initialize sync service

        this.syncService = new SyncService(platform, httpClient, cache, logger, this.localeManager,
                httpManager.getApiUrl(), httpManager.getApiKey());
        
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
        
        // Register reload command
        commandManager.registerCommand(new ModlReloadCommand(
            httpManager.getHttpClient(), platform, cache, this.localeManager, punishCommand, playerLookupCommand));
        
        // Register manual punishment commands
        commandManager.registerCommand(new BanCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new MuteCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new KickCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new BlacklistCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        commandManager.registerCommand(new PardonCommand(httpManager.getHttpClient(), platform, cache, this.localeManager));
        
        // Register player commands
        commandManager.registerCommand(new IAmMutedCommand(platform, cache, this.localeManager));
        commandManager.registerCommand(new TicketCommands(platform, httpManager.getHttpClient(), httpManager.getPanelUrl(), this.localeManager, chatMessageCache));

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
     * Stop all services (should be called on plugin disable)
     */
    public void shutdown() {
        if (syncService != null) {
            syncService.stop();
        }
    }
}