package gg.modl.minecraft.fabric.v1_21_8;

import gg.modl.minecraft.bridge.BridgePlayerProvider;
import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.bridge.BridgeScheduler;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class FabricBridgePluginContext implements BridgePluginContext {
    private final MinecraftServer server;
    private final Path dataFolder;
    private final Logger logger;
    private final FabricBridgeScheduler scheduler;
    private final FabricPlayerProvider playerProvider;

    public FabricBridgePluginContext(MinecraftServer server, Path dataFolder) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.logger = Logger.getLogger("modl");
        this.scheduler = new FabricBridgeScheduler(server);
        this.playerProvider = new FabricPlayerProvider(server);
    }

    @Override
    public BridgeScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public BridgePlayerProvider getPlayerProvider() {
        return playerProvider;
    }

    @Override
    public Path getDataFolder() {
        return dataFolder;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void saveDefaultResource(String resourcePath) {
        Path target = dataFolder.resolve(resourcePath);
        if (Files.exists(target)) return;
        try {
            target.getParent().toFile().mkdirs();
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) Files.copy(is, target);
            }
        } catch (Exception e) {
            logger.warning("Failed to save default resource: " + resourcePath + ": " + e.getMessage());
        }
    }

    @Override
    public String getMinecraftVersion() {
        return server.getVersion();
    }

    public MinecraftServer getServer() {
        return server;
    }
}
