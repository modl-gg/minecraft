package gg.modl.minecraft.core.punishment;

import gg.modl.minecraft.core.util.TimeUtil;

import java.util.Map;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class PunishmentFlagParser {
    private static final Map<String, String> SEVERITY_ALIASES = mapOf(
        "lenient", "low",
        "normal", "regular",
        "regular", "regular",
        "aggravated", "severe",
        "severe", "severe",
        "low", "low"
    );
    private static final String SEVERITY_LOW = "low", SEVERITY_REGULAR = "regular", SEVERITY_SEVERE = "severe";

    private final boolean silent;
    private final boolean altBlocking;
    private final boolean statWipe;
    private final boolean duration;
    private final boolean severity;

    private PunishmentFlagParser(boolean silent, boolean altBlocking, boolean statWipe, boolean duration, boolean severity) {
        this.silent = silent;
        this.altBlocking = altBlocking;
        this.statWipe = statWipe;
        this.duration = duration;
        this.severity = severity;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Flags parse(String args) {
        Flags result = new Flags();
        if (args == null || args.trim().isEmpty()) return result;

        String[] arguments = args.split(" ");
        StringBuilder reasonBuilder = new StringBuilder();

        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];

            if (severity && arg.equalsIgnoreCase("-severity") && i + 1 < arguments.length) {
                String severityInput = arguments[++i].toLowerCase();
                result.severity = SEVERITY_ALIASES.getOrDefault(severityInput, severityInput);
            } else if (severity && arg.equalsIgnoreCase("-lenient")) result.severity = SEVERITY_LOW;
            else if (severity && (arg.equalsIgnoreCase("-regular") || arg.equalsIgnoreCase("-normal"))) result.severity = SEVERITY_REGULAR;
            else if (severity && arg.equalsIgnoreCase("-severe")) result.severity = SEVERITY_SEVERE;
            else if (altBlocking && (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab"))) result.altBlocking = true;
            else if (silent && (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s"))) result.silent = true;
            else if (statWipe && (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw"))) result.statWipe = true;
            else if (duration && tryConsumeDuration(arg, result)) continue;
            else appendToReason(reasonBuilder, arg);
        }

        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private boolean tryConsumeDuration(String arg, Flags result) {
        long parsed = TimeUtil.getDuration(arg);
        if (parsed != -1L && result.duration == 0) {
            result.duration = parsed;
            return true;
        }
        return false;
    }

    private static void appendToReason(StringBuilder builder, String arg) {
        if (builder.length() > 0) builder.append(" ");
        builder.append(arg);
    }

    public static final class Flags {
        private String severity;
        private String reason = "";
        private long duration;
        private boolean silent;
        private boolean altBlocking;
        private boolean statWipe;

        public String getSeverity() { return severity; }
        public String getReason() { return reason; }
        public long getDuration() { return duration; }
        public boolean isSilent() { return silent; }
        public boolean isAltBlocking() { return altBlocking; }
        public boolean isStatWipe() { return statWipe; }
    }

    public static final class Builder {
        private boolean silent;
        private boolean altBlocking;
        private boolean statWipe;
        private boolean duration;
        private boolean severity;

        public Builder silent(boolean value) { this.silent = value; return this; }
        public Builder altBlocking(boolean value) { this.altBlocking = value; return this; }
        public Builder statWipe(boolean value) { this.statWipe = value; return this; }
        public Builder duration(boolean value) { this.duration = value; return this; }
        public Builder severity(boolean value) { this.severity = value; return this; }

        public PunishmentFlagParser build() {
            return new PunishmentFlagParser(silent, altBlocking, statWipe, duration, severity);
        }
    }
}
