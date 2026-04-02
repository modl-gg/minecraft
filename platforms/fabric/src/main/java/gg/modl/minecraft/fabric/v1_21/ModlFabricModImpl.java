package gg.modl.minecraft.fabric.v1_21;

import com.alessiodp.libby.FabricLibraryManager;
import com.alessiodp.libby.Library;
import dev.simplix.cirrus.fabric.CirrusFabric;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.core.Libraries;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ModlFabricModImpl implements DedicatedServerModInitializer {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private FabricBridgeComponent bridgeComponent;
    private CirrusFabric cirrus;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[modl] Initializing modl Fabric mod");

        loadLibraries();

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

        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
        loadLibrary(libraryManager, Libraries.SLF4J_SIMPLE);
        loadLibrary(libraryManager, Libraries.CIRRUS_FABRIC);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
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

    private void onServerStarted(MinecraftServer server) {
        Path dataFolder = server.getRunDirectory().resolve("config").resolve("modl");
        dataFolder.toFile().mkdirs();

        FabricBridgePluginContext context = new FabricBridgePluginContext(server, dataFolder);
        bridgeComponent = new FabricBridgeComponent(context, server);

        try {
            cirrus = new CirrusFabric(server);
            cirrus.init();
        } catch (Throwable e) {
            LOGGER.warn("[modl] Cirrus menu system unavailable: {}", e.getMessage());
        }

        try {
            BridgeConfig config = BridgeConfig.load(dataFolder);
            boolean connectToProxy = !config.getProxyHost().isEmpty();
            bridgeComponent.enable(null, connectToProxy);
        } catch (Exception e) {
            LOGGER.error("[modl] Failed to enable bridge component", e);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (cirrus != null) cirrus.shutdown();
        if (bridgeComponent != null) bridgeComponent.disable();
    }
}
