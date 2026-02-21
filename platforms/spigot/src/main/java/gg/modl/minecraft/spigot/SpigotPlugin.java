package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.spigot.CirrusSpigot;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class SpigotPlugin extends JavaPlugin {
    
    private PluginLoader loader;

    @Override
    public synchronized void onEnable() {
        // Load runtime dependencies via libby before anything else
        loadLibraries();

        // Initialize PacketEvents before Cirrus
        initializePacketEvents();

        saveDefaultConfig();
        createLocaleFiles();

        // Auto-merge new keys from plugin update
        YamlMergeUtil.mergeWithDefaults("/config.yml",
                getDataFolder().toPath().resolve("config.yml"), getLogger());
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml",
                getDataFolder().toPath().resolve("locale/en_US.yml"), getLogger());

        // Validate configuration before proceeding
        String apiUrl = getConfig().getString("api.url");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            getLogger().severe("===============================================");
            getLogger().severe("MODL CONFIGURATION ERROR");
            getLogger().severe("===============================================");
            getLogger().severe("You must configure your API URL in config.yml!");
            getLogger().severe("Please set 'api.url' to your actual MODL panel URL.");
            getLogger().severe("Example: https://yourserver.modl.gg");
            getLogger().severe("Plugin will now disable itself.");
            getLogger().severe("===============================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HttpManager httpManager = new HttpManager(
                getConfig().getString("api.key"),
                apiUrl,
                getConfig().getBoolean("api.debug", false),
                getConfig().getBoolean("api.testing-api", false),
                getConfig().getBoolean("server.query_mojang", false)
        );

        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        new CirrusSpigot(this).init();

        SpigotPlatform platform = new SpigotPlatform(commandManager, getLogger(), getDataFolder(), getConfig().getString("server.name", "Server 1"));
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, getConfig().getInt("sync.polling_rate", 2));

        List<String> mutedCommands = getConfig().getStringList("muted_commands");

        this.loader = new PluginLoader(platform, new SpigotCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        getServer().getPluginManager().registerEvents(new SpigotListener(platform, loader.getCache(), loader.getHttpClientHolder(), chatMessageCache, loader.getSyncService(), loader.getLocaleManager(), loader.getLoginCache(), httpManager.isDebugHttp(), mutedCommands), this);

        // Get CommandMap via reflection (not exposed in Spigot API, only Paper)
        // Used to dispatch commands off the main thread without triggering AsyncCatcher
        org.bukkit.command.CommandMap cmdMap;
        try {
            cmdMap = (org.bukkit.command.CommandMap) getServer().getClass()
                    .getMethod("getCommandMap").invoke(getServer());
        } catch (Exception e) {
            getLogger().severe("Cannot access CommandMap for async command dispatch: " + e.getMessage());
            cmdMap = null;
        }
        final org.bukkit.command.CommandMap commandMap = cmdMap;

        // Register async command interceptor — dispatches modl commands off the main thread
        AsyncCommandExecutor asyncExecutor = loader.getAsyncCommandExecutor();
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
                if (commandMap == null) return;

                String message = event.getMessage();
                if (message.length() <= 1) return;

                // Extract the base command name (strip leading /, take first word, handle namespace)
                String stripped = message.substring(1).trim();
                String baseCommand = stripped.split("\\s")[0].toLowerCase();

                // Strip namespace prefix (e.g., "modl:punishment-action" -> check both forms)
                if (asyncExecutor.isAsyncCommand(baseCommand) || asyncExecutor.isAsyncCommand(baseCommand.replace("modl:", ""))) {
                    event.setCancelled(true);
                    Player player = event.getPlayer();
                    // Dispatch via CommandMap.dispatch() to bypass Paper's AsyncCatcher
                    // in CraftServer.dispatchCommand()
                    asyncExecutor.execute(() -> {
                        try {
                            commandMap.dispatch(player, stripped);
                        } catch (Exception ex) {
                            getLogger().severe("Failed to dispatch async command: " + ex.getMessage());
                        }
                    });
                }
            }
        }, this);
    }

    @Override
    public synchronized void onDisable() {
        if (loader != null) {
            loader.shutdown();
        }
        // Terminate PacketEvents
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
    }

    private void initializePacketEvents() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().init();
        getLogger().info("PacketEvents initialized successfully");
    }

    private void loadLibraries() {
        BukkitLibraryManager libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.COMMON) {
            loadLibrary(libraryManager, record);
        }

        // Load ACF (core first, then platform-specific)
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_BUKKIT);

        // Load Cirrus (platform-specific shadow jar includes cirrus-api + cirrus-common)
        loadLibrary(libraryManager, Libraries.CIRRUS_SPIGOT);

        // Load PacketEvents (API first, then netty, then platform implementation)
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_SPIGOT);

        // Load Adventure (transitive deps first, then API, then serializers)
        loadLibrary(libraryManager, Libraries.EXAMINATION_API);
        loadLibrary(libraryManager, Libraries.EXAMINATION_STRING);
        loadLibrary(libraryManager, Libraries.ADVENTURE_KEY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_API);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);

        getLogger().info("Runtime libraries loaded successfully");
    }

    private void loadLibrary(BukkitLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.groupId())
                .artifactId(record.artifactId())
                .version(record.version())
                .id(record.id());

        if (record.hasRelocation()) {
            builder.relocate(record.oldRelocation(), record.newRelocation());
        }

        if (record.url() != null) {
            builder.url(record.url());
        }

        if (record.hasChecksum()) {
            builder.checksum(record.checksum());
        }

        libraryManager.loadLibrary(builder.build());
    }

    private void createLocaleFiles() {
        try {
            // Create locale directory
            File localeDir = new File(getDataFolder(), "locale");
            if (!localeDir.exists()) {
                localeDir.mkdirs();
                getLogger().info("Created locale directory at: " + localeDir.getPath());
            }
            
            // Create default en_US.yml if it doesn't exist
            File enUsFile = new File(localeDir, "en_US.yml");
            if (!enUsFile.exists()) {
                // Copy the default locale from resources
                try (InputStream defaultLocale = getResource("locale/en_US.yml")) {
                    if (defaultLocale != null) {
                        Files.copy(defaultLocale, enUsFile.toPath());
                        getLogger().info("Created default locale file at: " + enUsFile.getPath());
                    } else {
                        getLogger().warning("Default locale resource not found in JAR");
                    }
                }
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create locale files: " + e.getMessage());
        }
    }
}