package gg.modl.minecraft.core.procedure;

import gg.modl.minecraft.core.util.TimeUtil;

import java.util.Date;
import java.util.Optional;

public class ArgumentChecker {

    // check for if -s is in the argument
    public static boolean isSilent(String arguments) {
        return arguments.contains("-s");
    }

    public static boolean isAltBlocking(String arguments) {
        return arguments.contains("-ab");
    }

    public static boolean isStatWiping(String arguments) {
        return arguments.contains("-w");
    }

    public static boolean isChatLogging(String arguments) {
        return arguments.contains("-cl");
    }

    // get the expiration using TimeUtil to parse amount of time and add it to current date
    public static Optional<Date> getExpiration(String arguments) {
        String[] args = arguments.split(" ");
        if (args[0] == null) return Optional.empty();
        return Optional.ofNullable(TimeUtil.getTime(args[0]));
    }

    public static long getDuration(String arguments) {
        return getDuration(arguments.split(" "));
    }

    public static long getDuration(String[] arguments) {
        long duration = -1L;

        for (String arg : arguments) {
            long duration2 = TimeUtil.getDuration(arg);
            if (duration2 != -1L) {
                duration = duration2;
                break;
            }
        }

        return duration;
    }

    // get rid of -s flag and expiration and get only the reason
    public static String getReason(String arguments) {
        String[] args = arguments.split(" ");

        StringBuilder sb = new StringBuilder();
        int start = (getExpiration(arguments).isEmpty()) ? 0 : 1;
        for (int i = start; i < args.length; i++) {
            String s = args[i];
            if (!s.equalsIgnoreCase("-s") && !s.equalsIgnoreCase("-ab")
                    && !s.equalsIgnoreCase("-cl") && !s.equalsIgnoreCase("-sw")) {

                sb.append(args[i]);
                if (i + 1 != args.length) sb.append(" ");
            }
        }

        return sb.toString();
    }
}
