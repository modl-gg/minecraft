package gg.modl.minecraft.bridge;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface BridgePluginContext {

    BridgeScheduler getScheduler();

    BridgePlayerProvider getPlayerProvider();

    Path getDataFolder();

    Logger getLogger();

    void saveDefaultResource(String resourcePath);

    String getMinecraftVersion();
}
