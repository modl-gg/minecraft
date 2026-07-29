package gg.modl.minecraft.bridge.reporter.detection;

import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
public final class ViolationRecord {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DetectionSource source;
    private final String checkName;
    private final String verbose;
    private final long timestamp;

    public ViolationRecord(DetectionSource source, String checkName, String verbose) {
        this(source, checkName, verbose, System.currentTimeMillis());
    }

    ViolationRecord(DetectionSource source, String checkName, String verbose, long timestamp) {
        this.source = source;
        this.checkName = checkName;
        this.verbose = verbose;
        this.timestamp = timestamp;
    }

    public String getFormattedTimestamp() {
        return FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] [" + source + "] " + checkName + " | " + verbose;
    }
}
