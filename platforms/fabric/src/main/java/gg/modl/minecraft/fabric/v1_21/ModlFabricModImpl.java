package gg.modl.minecraft.fabric.v1_21;

import com.alessiodp.libby.FabricLibraryManager;
import com.alessiodp.libby.Library;
import dev.simplix.cirrus.fabric.CirrusFabric;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.boot.*;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.util.PluginLogger;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class ModlFabricModImpl implements DedicatedServerModInitializer {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final PluginLogger PLUGIN_LOGGER = new PluginLogger() {
        @Override public void info(String msg) { LOGGER.info(msg); }
        @Override public void warning(String msg) { LOGGER.warn(msg); }
        @Override public void severe(String msg) { LOGGER.error(msg); }
    };

    private FabricBridgeComponent bridgeComponent;
    private CirrusFabric cirrus;
    private BootConfig bootConfig;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[modl] Initializing modl Fabric mod");

        loadLibraries();

        Path dataFolder = FabricLoader.getInstance().getConfigDir().resolve("modl");
        dataFolder.toFile().mkdirs();
        bootConfig = loadOrCreateBootConfig(dataFolder);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void loadLibraries() {
        FabricLibraryManager libraryManager = new FabricLibraryManager(MOD_ID, LOGGER);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-snapshots/");
        libraryManager.addRepository("https://jitpack.io");
        libraryManager.addRepository("https://repo.aikar.co/content/groups/aikar/");

        for (LibraryRecord record : Libraries.PROTO_DEPS) loadLibrary(libraryManager, record);
        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
        loadLibrary(libraryManager, Libraries.SLF4J_SIMPLE);
        loadLibrary(libraryManager, Libraries.CIRRUS_FABRIC);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_FABRIC);
        loadLibrary(libraryManager, Libraries.ADVENTURE_KEY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_API);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_MINIMESSAGE);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(libraryManager, Libraries.EXAMINATION_API);
        loadLibrary(libraryManager, Libraries.EXAMINATION_STRING);

        LOGGER.info("[modl] Runtime libraries loaded successfully");
    }

    private void loadLibrary(FabricLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.getGroupId())
                .artifactId(record.getArtifactId())
                .version(record.getVersion());

        if (record.hasRelocations()) {
            for (String[] relocation : record.getRelocations()) {
                builder.relocate(relocation[0], relocation[1]);
            }
        }
        if (record.getUrl() != null) builder.url(record.getUrl());
        if (record.hasChecksum()) builder.checksumFromBase64(record.getChecksum());

        libraryManager.loadLibrary(builder.build());
    }

    private BootConfig loadOrCreateBootConfig(Path dataFolder) {
        try {
            if (BootConfig.exists(dataFolder)) {
                BootConfig config = BootConfig.load(dataFolder);
                if (config != null && config.isValid()) {
                    LOGGER.info("Loaded configuration from boot.yml (mode: {})", config.getMode().toYaml());
                    return config;
                }
            }

            Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                    dataFolder, PlatformType.FABRIC, PLUGIN_LOGGER);
            if (migrated.isPresent()) return migrated.get();

            LOGGER.info("No configuration found. Starting setup wizard...");
            ConsoleInput input = ConsoleInput.system(PLUGIN_LOGGER);
            SetupWizard wizard = new SetupWizard(PLUGIN_LOGGER, input, PlatformType.FABRIC);
            BootConfig config = wizard.run();

            if (config != null) {
                config.save(dataFolder);
                return config;
            }

            logConfigurationError();
            return null;
        } catch (IOException e) {
            LOGGER.error("Failed to load boot.yml: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void initPacketEvents() {
        try {
            Class<?> builderClass = Class.forName(
                    "gg.modl.libs.packetevents.impl.factory.fabric.FabricPacketEventsBuilder");
            Object api = builderClass.getMethod("build", String.class).invoke(null, MOD_ID);
            com.github.retrooper.packetevents.PacketEvents.setAPI(
                    (com.github.retrooper.packetevents.PacketEventsAPI<?>) api);
            com.github.retrooper.packetevents.PacketEvents.getAPI().load();
            com.github.retrooper.packetevents.PacketEvents.getAPI().init();
            LOGGER.info("[modl] PacketEvents initialized successfully");
        } catch (Throwable e) {
            LOGGER.warn("[modl] PacketEvents unavailable: {}", e.getMessage());
        }
    }

    private void onServerStarted(MinecraftServer server) {
        if (bootConfig == null) return;

        initPacketEvents();

        Path dataFolder = FabricLoader.getInstance().getConfigDir().resolve("modl");
        FabricBridgePluginContext context = new FabricBridgePluginContext(server, dataFolder);

        try {
            cirrus = new CirrusFabric(server);
            cirrus.init();
        } catch (Throwable e) {
            LOGGER.warn("[modl] Cirrus menu system unavailable: {}", e.getMessage());
        }

        try {
            String backendUrl = bootConfig.isTestingApi()
                    ? "https://api.modl.top/v2" : "https://api.modl.gg/v2";
            String panelUrl = null;

            if (bootConfig.getMode() == BootConfig.Mode.STANDALONE) {
                panelUrl = StartupClient.callStartupWithRetry(
                        bootConfig.getApiKey(), bootConfig.isTestingApi(),
                        new StartupRequest(PluginInfo.VERSION, "FABRIC",
                                server.getVersion(), server.getMaxPlayerCount()),
                        PLUGIN_LOGGER);
                if (panelUrl == null) {
                    LOGGER.error("Failed to connect to modl.gg. Check your API key and network connection.");
                    return;
                }
            }

            if (bootConfig.getMode() == BootConfig.Mode.BRIDGE_ONLY) {
                writeBridgeConfigFromWizard(dataFolder);
            }

            bridgeComponent = new FabricBridgeComponent(context, server,
                    bootConfig.getApiKey(), backendUrl, panelUrl != null ? panelUrl : "", PLUGIN_LOGGER);
            bridgeComponent.enable(null, bootConfig.getMode() == BootConfig.Mode.BRIDGE_ONLY);
        } catch (Exception e) {
            LOGGER.error("[modl] Failed to enable bridge component", e);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (cirrus != null) cirrus.shutdown();
        if (bridgeComponent != null) bridgeComponent.disable();
    }

    private void writeBridgeConfigFromWizard(Path dataFolder) {
        if (bootConfig.getWizardProxyHost() == null) return;
        try {
            BridgeConfig bridgeConfig = BridgeConfig.load(dataFolder);
            bridgeConfig.setApiKey(bootConfig.getApiKey());
            bridgeConfig.updateProxyConnection(
                    bootConfig.getWizardProxyHost(), bootConfig.getWizardProxyPort());
            bridgeConfig.save(dataFolder);
        } catch (IOException e) {
            LOGGER.warn("Failed to write bridge-config.yml: {}", e.getMessage());
        }
    }

    private void logConfigurationError() {
        LOGGER.error("===============================================");
        LOGGER.error("modl.gg CONFIGURATION ERROR");
        LOGGER.error("===============================================");
        LOGGER.error("Could not load or create boot.yml.");
        LOGGER.error("Please configure manually in config/modl/boot.yml");
        LOGGER.error("or delete it to re-run the setup wizard.");
        LOGGER.error("===============================================");
    }
}
