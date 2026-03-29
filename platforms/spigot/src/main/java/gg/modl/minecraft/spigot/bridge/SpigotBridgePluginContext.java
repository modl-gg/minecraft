package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgePlayerProvider;
import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.bridge.BridgeScheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Logger;

public class SpigotBridgePluginContext implements BridgePluginContext {
    private final JavaPlugin plugin;
    @Getter private final BridgeScheduler scheduler;
    @Getter private final BridgePlayerProvider playerProvider;

    public SpigotBridgePluginContext(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = new SpigotBridgeScheduler(plugin);
        this.playerProvider = new SpigotPlayerProvider();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public Path getDataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public void saveDefaultResource(String resourcePath) {
        plugin.saveResource(resourcePath, false);
    }

    @Override
    public String getMinecraftVersion() {
        String bukkit = Bukkit.getBukkitVersion();
        int dash = bukkit.indexOf('-');
        return dash > 0 ? bukkit.substring(0, dash) : bukkit;
    }
}
