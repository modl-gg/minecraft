package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {
    private static boolean newer(String candidate, String baseline) {
        return SemanticVersion.parse(candidate).isNewerThan(SemanticVersion.parse(baseline));
    }

    @Test
    void newerPatchIsAnUpdate() {
        assertTrue(newer("2.2.3", "2.2.2"));
        assertFalse(newer("2.2.2", "2.2.3"));
    }

    @Test
    void olderReleaseIsNotAnUpdateForNewerPreRelease() {
        assertFalse(newer("2.2.3", "2.3.0-BETA"));
        assertTrue(newer("2.3.0-BETA", "2.2.3"));
    }

    @Test
    void releaseIsNewerThanItsPreRelease() {
        assertTrue(newer("2.3.0", "2.3.0-BETA"));
        assertFalse(newer("2.3.0-BETA", "2.3.0"));
    }

    @Test
    void equalVersionsAreNotUpdates() {
        assertFalse(newer("2.3.0", "2.3.0"));
        assertFalse(newer("2.3.0-BETA", "2.3.0-beta"));
        assertEquals(SemanticVersion.parse("2.3.0"), SemanticVersion.parse("v2.3.0"));
    }

    @Test
    void missingSegmentsCountAsZero() {
        assertFalse(newer("2.3", "2.3.0"));
        assertTrue(newer("2.3.1", "2.3"));
    }

    @Test
    void versionPrefixAndBuildMetadataAreIgnored() {
        assertTrue(newer("v2.3.1", "2.3.0"));
        assertFalse(newer("2.3.0+build.7", "2.3.0"));
    }

    @Test
    void preReleaseIdentifiersFollowSemverPrecedence() {
        assertTrue(newer("1.0.0-beta", "1.0.0-alpha"));
        assertTrue(newer("1.0.0-alpha.1", "1.0.0-alpha"));
        assertTrue(newer("1.0.0-alpha.beta", "1.0.0-alpha.1"));
        assertTrue(newer("1.0.0-rc.2", "1.0.0-rc.1"));
        assertTrue(newer("1.0.0-rc.10", "1.0.0-rc.9"));
    }

    @Test
    void unparseableInputNeverThrows() {
        assertFalse(newer("", "2.3.0"));
        assertFalse(newer("not-a-version", "2.3.0"));
        assertTrue(newer("2.3.0", "garbage"));
    }
}
