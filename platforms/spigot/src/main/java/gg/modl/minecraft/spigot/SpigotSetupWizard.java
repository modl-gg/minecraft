package gg.modl.minecraft.spigot;

import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.ConsoleInput;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.SetupWizard;
import gg.modl.minecraft.core.util.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Spigot setup wizard that intercepts console input via ServerCommandEvent.
 * System.in doesn't work on Spigot because JLine owns it. ServerCommandEvent
 * fires after JLine reads the line, so we capture it reliably.
 * The wizard runs on a background thread and blocks on a queue.
 */
public class SpigotSetupWizard implements Listener {
    private final JavaPlugin plugin;
    private final PluginLogger logger;
    private final Consumer<BootConfig> onComplete;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private volatile boolean active = false;

    public SpigotSetupWizard(JavaPlugin plugin, PluginLogger logger, Consumer<BootConfig> onComplete) {
        this.plugin = plugin;
        this.logger = logger;
        this.onComplete = onComplete;
    }

    public void start() {
        active = true;
        // Register listener immediately to block joins and capture commands
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Delay wizard prompts until after server startup finishes (60 ticks = 3s)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Run wizard on a background thread so readLine() can block
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ConsoleInput input = new ServerCommandInput();
                    SetupWizard wizard = new SetupWizard(logger, input, PlatformType.SPIGOT);
                    BootConfig config = wizard.run();

                    if (config != null) {
                        config.save(plugin.getDataFolder().toPath());
                        logger.info("Configuration saved to boot.yml.");
                        logger.info("Initializing plugin...");
                        Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(config));
                    } else {
                        fallbackToTemplate();
                    }
                } catch (Exception e) {
                    logger.severe("Setup wizard error: " + e.getMessage());
                    fallbackToTemplate();
                } finally {
                    active = false;
                    Bukkit.getScheduler().runTask(plugin, () -> HandlerList.unregisterAll(this));
                }
            });
        }, 60L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (!active) return;
        event.setCancelled(true);
        inputQueue.offer(event.getCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!active) return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                "Server is running first-time setup. Please try again shortly.");
    }

    private void fallbackToTemplate() {
        try {
            BootConfig.saveTemplate(plugin.getDataFolder().toPath());
        } catch (IOException ignored) {}
        logger.warning("Setup cancelled. A boot.yml template has been created.");
        logger.info("Edit plugins/" + plugin.getName() + "/boot.yml and restart.");
    }

    /**
     * ConsoleInput backed by ServerCommandEvent — blocks on a queue
     * until the server operator types something in the console.
     */
    private class ServerCommandInput implements ConsoleInput {
        @Override
        public String readLine(String prompt) {
            logger.info(prompt);
            try {
                return inputQueue.take();
            } catch (InterruptedException e) {
                return null;
            }
        }
    }
}
