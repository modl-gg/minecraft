package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class PunishmentPreviewResponse extends StatusResponse {
    private String message, socialStatus, gameplayStatus, offenderStatus, category;
    private SeverityPreview lenient, regular, aggravated, singleSeverity;
    private boolean success, singleSeverityPunishment, permanentUntilUsernameChange, permanentUntilSkinChange,
            canBeAltBlocking, canBeStatWiping;
    private int status, socialPoints, gameplayPoints;

    @Override
    public boolean isSuccess() {
        return success || super.isSuccess();
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SeverityPreview {
        private String severity, durationFormatted, punishmentType, newSocialStatus, newGameplayStatus;
        private boolean permanent;
        private int points, newSocialPoints, newGameplayPoints;
        private long durationMs;
    }
}
