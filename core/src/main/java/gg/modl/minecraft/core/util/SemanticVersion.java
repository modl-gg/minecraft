package gg.modl.minecraft.core.util;

import java.util.Locale;

public final class SemanticVersion implements Comparable<SemanticVersion> {
    private static final int MAX_NUMERIC_IDENTIFIER_LENGTH = 18;

    private final long[] releaseSegments;
    private final String[] preReleaseIdentifiers;

    private SemanticVersion(long[] releaseSegments, String[] preReleaseIdentifiers) {
        this.releaseSegments = releaseSegments;
        this.preReleaseIdentifiers = preReleaseIdentifiers;
    }

    public static SemanticVersion parse(String version) {
        String normalized = stripPrefix(version == null ? "" : version.trim());
        int buildMetadataStart = normalized.indexOf('+');
        if (buildMetadataStart >= 0) normalized = normalized.substring(0, buildMetadataStart);

        int preReleaseStart = normalized.indexOf('-');
        String releasePart = preReleaseStart >= 0 ? normalized.substring(0, preReleaseStart) : normalized;
        String preReleasePart = preReleaseStart >= 0 ? normalized.substring(preReleaseStart + 1) : null;

        return new SemanticVersion(parseReleaseSegments(releasePart), parsePreReleaseIdentifiers(preReleasePart));
    }

    public boolean isNewerThan(SemanticVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int releaseComparison = compareReleaseSegments(releaseSegments, other.releaseSegments);
        if (releaseComparison != 0) return releaseComparison;
        return comparePreRelease(preReleaseIdentifiers, other.preReleaseIdentifiers);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SemanticVersion && compareTo((SemanticVersion) other) == 0;
    }

    @Override
    public int hashCode() {
        long hash = 0;
        for (long segment : releaseSegments) {
            if (segment != 0) hash = 31 * hash + segment;
        }
        return (int) (hash ^ (hash >>> 32));
    }

    private static String stripPrefix(String version) {
        if (version.length() > 1 && (version.charAt(0) == 'v' || version.charAt(0) == 'V')
                && Character.isDigit(version.charAt(1))) {
            return version.substring(1);
        }
        return version;
    }

    private static long[] parseReleaseSegments(String releasePart) {
        String[] rawSegments = releasePart.split("\\.");
        long[] segments = new long[rawSegments.length];
        for (int i = 0; i < rawSegments.length; i++) {
            segments[i] = isNumeric(rawSegments[i]) ? Long.parseLong(rawSegments[i]) : 0L;
        }
        return segments;
    }

    private static String[] parsePreReleaseIdentifiers(String preReleasePart) {
        if (preReleasePart == null || preReleasePart.isEmpty()) return null;
        return preReleasePart.split("\\.");
    }

    private static int compareReleaseSegments(long[] left, long[] right) {
        int longest = Math.max(left.length, right.length);
        for (int i = 0; i < longest; i++) {
            long leftSegment = i < left.length ? left[i] : 0L;
            long rightSegment = i < right.length ? right[i] : 0L;
            int comparison = Long.compare(leftSegment, rightSegment);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static int comparePreRelease(String[] left, String[] right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;

        int longest = Math.max(left.length, right.length);
        for (int i = 0; i < longest; i++) {
            if (i >= left.length) return -1;
            if (i >= right.length) return 1;
            int comparison = comparePreReleaseIdentifier(left[i], right[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static int comparePreReleaseIdentifier(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) return Long.compare(Long.parseLong(left), Long.parseLong(right));
        if (leftNumeric) return -1;
        if (rightNumeric) return 1;
        return left.toUpperCase(Locale.ROOT).compareTo(right.toUpperCase(Locale.ROOT));
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty() || value.length() > MAX_NUMERIC_IDENTIFIER_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }
}
