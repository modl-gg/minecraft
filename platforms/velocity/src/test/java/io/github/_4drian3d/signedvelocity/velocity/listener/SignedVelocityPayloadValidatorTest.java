package io.github._4drian3d.signedvelocity.velocity.listener;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SignedVelocityPayloadValidatorTest {
    private static final UUID PLAYER_ID = UUID.fromString("1736067e-57cd-4e59-a98d-9578f7fe91a2");

    @Test
    void acceptsAllowedResultPayload() throws IOException {
        assertTrue(SignedVelocityPayloadValidator.isValid(payload("CHAT_RESULT", "ALLOWED")));
    }

    @Test
    void acceptsCancelResultPayload() throws IOException {
        assertTrue(SignedVelocityPayloadValidator.isValid(payload("COMMAND_RESULT", "CANCEL")));
    }

    @Test
    void acceptsModifyResultPayloadWithModifiedString() throws IOException {
        assertTrue(SignedVelocityPayloadValidator.isValid(payload("CHAT_RESULT", "MODIFY", "changed message")));
    }

    @Test
    void rejectsMalformedPayloads() throws IOException {
        assertFalse(SignedVelocityPayloadValidator.isValid(new byte[]{1, 2, 3}));
        assertFalse(SignedVelocityPayloadValidator.isValid(payload("NOT_A_QUEUE", "ALLOWED")));
        assertFalse(SignedVelocityPayloadValidator.isValid(payload("CHAT_RESULT", "NOT_A_RESULT")));
        assertFalse(SignedVelocityPayloadValidator.isValid(payload("COMMAND_RESULT", "MODIFY")));
    }

    @Test
    void ignoresPlayerOriginatedProtectedChannelWhenPayloadIsMalformed() {
        assertFalse(SignedVelocityPayloadValidator.shouldDisconnectPlayerOriginatedPayload(new byte[]{1, 2, 3}));
    }

    @Test
    void disconnectsPlayerOriginatedProtectedChannelWhenPayloadIsValid() throws IOException {
        assertTrue(SignedVelocityPayloadValidator.shouldDisconnectPlayerOriginatedPayload(payload("CHAT_RESULT", "ALLOWED")));
    }

    @Test
    void rejectsPayloadsWithTrailingData() throws IOException {
        assertFalse(SignedVelocityPayloadValidator.isValid(payload("CHAT_RESULT", "ALLOWED", "unexpected")));
    }

    private static byte[] payload(final String queue, final String result, final String... additionalValues) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(PLAYER_ID.toString());
        output.writeUTF(queue);
        output.writeUTF(result);
        for (final String value : additionalValues) {
            output.writeUTF(value);
        }
        output.flush();
        return bytes.toByteArray();
    }
}
