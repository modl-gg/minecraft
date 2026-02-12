package gg.modl.minecraft.core.util;

import java.util.List;

public class MutedCommandUtil {

    /**
     * Check if a command line matches any of the muted commands.
     *
     * @param commandLine  the full command string (may include leading slash)
     * @param mutedCommands list of command names (without leading slash) to block
     * @return true if the command should be blocked for muted players
     */
    public static boolean isBlockedCommand(String commandLine, List<String> mutedCommands) {
        if (commandLine == null || mutedCommands == null || mutedCommands.isEmpty()) {
            return false;
        }

        String line = commandLine;
        if (line.startsWith("/")) {
            line = line.substring(1);
        }

        // Extract base command (first token before space)
        int spaceIndex = line.indexOf(' ');
        String baseCommand = spaceIndex >= 0 ? line.substring(0, spaceIndex) : line;

        for (String blocked : mutedCommands) {
            if (baseCommand.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }
}
