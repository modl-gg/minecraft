package gg.modl.minecraft.api.http.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing punishment preview calculations.
 */
@Data @NoArgsConstructor
public class PunishmentPreviewResponse {
    private String message, socialStatus, gameplayStatus, offenderStatus, category;
    private SeverityPreview lenient, regular, aggravated, singleSeverity;
    private boolean success, singleSeverityPunishment, permanentUntilUsernameChange, permanentUntilSkinChange,
            canBeAltBlocking, canBeStatWiping;
    private int status, socialPoints, gameplayPoints;

    public boolean isSuccess() {
        return success || (status >= 200 && status < 300);
    }

    @Data @NoArgsConstructor
    public static class SeverityPreview {
        private String severity, durationFormatted, punishmentType, newSocialStatus, newGameplayStatus;
        private boolean permanent;
        private int points, newSocialPoints, newGameplayPoints;
        private long durationMs;
    }
}
