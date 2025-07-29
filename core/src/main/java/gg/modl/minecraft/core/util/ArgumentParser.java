package gg.modl.minecraft.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdwMy])");
    
    public static long getDuration(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return -1; // Permanent
        }
        
        // Look for time patterns like 1d, 2h, 30m, etc.
        Matcher matcher = DURATION_PATTERN.matcher(arguments);
        long totalMillis = 0;
        
        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            
            switch (unit) {
                case "s":
                    totalMillis += amount * 1000L;
                    break;
                case "m":
                    totalMillis += amount * 60 * 1000L;
                    break;
                case "h":
                    totalMillis += amount * 60 * 60 * 1000L;
                    break;
                case "d":
                    totalMillis += amount * 24 * 60 * 60 * 1000L;
                    break;
                case "w":
                    totalMillis += amount * 7 * 24 * 60 * 60 * 1000L;
                    break;
                case "M":
                    totalMillis += amount * 30L * 24 * 60 * 60 * 1000L;
                    break;
                case "y":
                    totalMillis += amount * 365L * 24 * 60 * 60 * 1000L;
                    break;
            }
        }
        
        return totalMillis > 0 ? totalMillis : -1;
    }
    
    public static String getReason(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return "Unspecified";
        }
        
        // Remove time patterns and flags to extract reason
        String reason = arguments;
        reason = reason.replaceAll("\\d+[smhdwMy]", ""); // Remove time patterns
        reason = reason.replaceAll("-[a-zA-Z]+", ""); // Remove flags like -s, -ab, -sw
        reason = reason.trim();
        
        return reason.isEmpty() ? "Unspecified" : reason;
    }
    
    public static boolean isSilent(String arguments) {
        return arguments != null && arguments.contains("-s");
    }
    
    public static boolean isAltBlocking(String arguments) {
        return arguments != null && arguments.contains("-ab");
    }
    
    public static boolean isStatWiping(String arguments) {
        return arguments != null && arguments.contains("-sw");
    }
    
    public static boolean isChatLogging(String arguments) {
        return arguments != null && arguments.contains("-cl");
    }
}