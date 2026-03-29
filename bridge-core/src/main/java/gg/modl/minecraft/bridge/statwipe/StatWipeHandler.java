package gg.modl.minecraft.bridge.statwipe;

import gg.modl.minecraft.bridge.BridgePlayerProvider;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class StatWipeHandler {
    private static final String PLAYER_PLACEHOLDER = "{player}";
    private static final String UUID_PLACEHOLDER = "{uuid}";

    private final Logger logger;
    private final BridgeConfig config;
    private final BridgePlayerProvider playerProvider;

    public boolean execute(String username, String uuid, String punishmentId) {
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
                playerProvider.dispatchConsoleCommand(command);
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
