package gg.modl.minecraft.fabric;

import dev.simplix.cirrus.fabric.CirrusFabric;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ModlFabricMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private FabricBridgeComponent bridgeComponent;
    private CirrusFabric cirrus;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[modl] Initializing modl Fabric mod");

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private void onServerStarted(MinecraftServer server) {
        Path dataFolder = server.getRunDirectory().resolve("config").resolve("modl");
        dataFolder.toFile().mkdirs();

        FabricBridgePluginContext context = new FabricBridgePluginContext(server, dataFolder);
        bridgeComponent = new FabricBridgeComponent(context, server);

        try {
            cirrus = new CirrusFabric(server);
            cirrus.init();
        } catch (Exception e) {
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
