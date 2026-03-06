package gg.modl.minecraft.api.http.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing punishment preview calculations.
 */
@Data @NoArgsConstructor
public class PunishmentPreviewResponse {
    private int status;
    private boolean success;
    private String message;

    private String socialStatus;
    private String gameplayStatus;
    private int socialPoints;
    private int gameplayPoints;
    private String offenseLevel;
    private SeverityPreview lenient;
    private SeverityPreview regular;
    private SeverityPreview aggravated;
    private SeverityPreview singleSeverity;
    private boolean singleSeverityPunishment;
    private boolean permanentUntilUsernameChange;
    private boolean permanentUntilSkinChange;
    private boolean canBeAltBlocking;
    private boolean canBeStatWiping;
    private String category;

    public boolean isSuccess() {
        return success || (status >= 200 && status < 300);
    }

    @Data @NoArgsConstructor
    public static class SeverityPreview {
        private String severity;
        private int points;
        private long durationMs;
        private String durationFormatted;
        private String punishmentType;
        private boolean permanent;
        private String newSocialStatus;
        private String newGameplayStatus;
        private int newSocialPoints;
        private int newGameplayPoints;
    }
}
