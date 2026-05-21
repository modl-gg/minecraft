package io.github._4drian3d.signedvelocity.velocity.listener;

import io.github._4drian3d.signedvelocity.shared.types.QueueType;
import io.github._4drian3d.signedvelocity.shared.types.ResultType;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

final class SignedVelocityPayloadValidator {
    private SignedVelocityPayloadValidator() {
    }

    enum ValidationResult {
        VALID,
        MALFORMED
    }

    static boolean isValid(final byte[] data) {
        return validate(data) == ValidationResult.VALID;
    }

    static ValidationResult validate(final byte[] data) {
        final DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
        try {
            UUID.fromString(input.readUTF());
            QueueType.getOrThrow(input.readUTF());
            final ResultType resultType = ResultType.getOrThrow(input.readUTF());
            if (resultType == ResultType.MODIFY) {
                input.readUTF();
            }
            return input.available() == 0 ? ValidationResult.VALID : ValidationResult.MALFORMED;
        } catch (final IllegalArgumentException | IOException exception) {
            return ValidationResult.MALFORMED;
        }
    }

    static boolean shouldDisconnectPlayerOriginatedPayload(final byte[] data) {
        return validate(data) == ValidationResult.VALID;
    }
}
