package gg.modl.minecraft.core.util;

import java.util.List;

public class MutedCommandUtil {
    public static boolean isBlockedCommand(String commandLine, List<String> mutedCommands) {
        if (commandLine == null || mutedCommands == null || mutedCommands.isEmpty()) return false;

        String line = commandLine;
        if (line.startsWith("/")) line = line.substring(1);

        int spaceIndex = line.indexOf(' ');
        String baseCommand = spaceIndex >= 0 ? line.substring(0, spaceIndex) : line;

        for (String blocked : mutedCommands) {
            if (baseCommand.equalsIgnoreCase(blocked)) return true;
        }
        return false;
    }
}
