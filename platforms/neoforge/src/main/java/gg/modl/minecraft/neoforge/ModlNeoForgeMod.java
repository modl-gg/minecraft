package gg.modl.minecraft.neoforge;

import dev.simplix.cirrus.neoforge.CirrusNeoForge;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(ModlNeoForgeMod.MOD_ID)
public class ModlNeoForgeMod {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private NeoForgeBridgeComponent bridgeComponent;
    private CirrusNeoForge cirrus;

    public ModlNeoForgeMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path dataFolder = server.getRunDirectory().toPath().resolve("config").resolve("modl");
        dataFolder.toFile().mkdirs();

        NeoForgeBridgePluginContext context = new NeoForgeBridgePluginContext(server, dataFolder);
        bridgeComponent = new NeoForgeBridgeComponent(context, server);

        try {
            cirrus = new CirrusNeoForge(server);
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

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (cirrus != null) cirrus.shutdown();
        if (bridgeComponent != null) bridgeComponent.disable();
    }
}
