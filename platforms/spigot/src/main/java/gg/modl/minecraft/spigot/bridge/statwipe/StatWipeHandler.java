package gg.modl.minecraft.spigot.bridge.statwipe;

import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class StatWipeHandler {
    private static final String PLAYER_PLACEHOLDER = "{player}";
    private static final String UUID_PLACEHOLDER = "{uuid}";

    private final JavaPlugin plugin;
    private final BridgeConfig config;

    public boolean execute(String username, String uuid, String punishmentId) {
        Logger logger = plugin.getLogger();
        List<String> commands = config.getStatWipeCommands();

        if (commands.isEmpty()) {
            logger.warning("No stat-wipe-commands configured");
            return false;
        }

        boolean allSuccess = true;
        for (String template : commands) {
            String command = template
                    .replace(PLAYER_PLACEHOLDER, username)
                    .replace(UUID_PLACEHOLDER, uuid);
            try {
                if (config.isDebug()) {
                    logger.info("Dispatching command: " + command);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                logger.severe("Failed to execute command '" + command + "': " + e.getMessage());
                allSuccess = false;
            }
        }

        if (allSuccess) {
            logger.info("Executed " + commands.size() + " stat-wipe command(s) for " + username
                    + " (punishment: " + punishmentId + ")");
        }
        return allSuccess;
    }
}
