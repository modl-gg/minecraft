package gg.modl.minecraft.api.http.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing punishment preview calculations.
 */
@Data
@NoArgsConstructor
public class PunishmentPreviewResponse {
    private int status;
    private boolean success;
    private String message;

    // Player's current status
    private String socialStatus;
    private String gameplayStatus;
    private int socialPoints;
    private int gameplayPoints;

    // Offense level calculation
    private String offenseLevel;

    // Punishment preview for each severity
    private SeverityPreview lenient;
    private SeverityPreview regular;
    private SeverityPreview aggravated;

    // For single-severity punishments
    private SeverityPreview singleSeverity;

    // Punishment type info
    private boolean singleSeverityPunishment;
    private boolean permanentUntilUsernameChange;
    private boolean permanentUntilSkinChange;
    private boolean canBeAltBlocking;
    private boolean canBeStatWiping;
    private String category;

    public boolean isSuccess() {
        return success || (status >= 200 && status < 300);
    }

    /**
     * Check if this is a single-severity type (includes permanent-until-change types).
     */
    public boolean isSingleSeverityType() {
        return singleSeverityPunishment || permanentUntilUsernameChange || permanentUntilSkinChange;
    }

    @Data
    @NoArgsConstructor
    public static class SeverityPreview {
        private String severity;
        private int points;
        private long durationMs;
        private String durationFormatted;
        private String punishmentType;
        private boolean permanent;

        // New status after this punishment
        private String newSocialStatus;
        private String newGameplayStatus;
        private int newSocialPoints;
        private int newGameplayPoints;
    }
}
