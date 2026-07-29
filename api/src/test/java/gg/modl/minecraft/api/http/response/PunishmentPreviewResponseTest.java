package gg.modl.minecraft.api.http.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentPreviewResponseTest {
    @Test
    void successfulHttpStatusReportsSuccessEvenWhenSuccessFlagAbsent() {
        PunishmentPreviewResponse response = PunishmentPreviewResponse.builder().status(200).build();

        assertTrue(response.isSuccess());
    }

    @Test
    void successFlagReportsSuccessEvenWithoutHttpStatus() {
        PunishmentPreviewResponse response = PunishmentPreviewResponse.builder().success(true).build();

        assertTrue(response.isSuccess());
    }

    @Test
    void unsuccessfulStatusAndFlagReportsFailure() {
        PunishmentPreviewResponse response = PunishmentPreviewResponse.builder().status(503).build();

        assertFalse(response.isSuccess());
    }
}
