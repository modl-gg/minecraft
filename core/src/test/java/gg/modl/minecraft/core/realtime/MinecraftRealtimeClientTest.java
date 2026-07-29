package gg.modl.minecraft.core.realtime;

import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.proto.modl.v1.ErrorCode;
import gg.modl.proto.modl.v1.Topic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftRealtimeClientTest {
    @Test
    void canStartAllowsPermissionAndPunishmentTypeTopics() {
        StartupResponse response = response(
                Topic.TOPIC_MINECRAFT_PERMISSIONS.name(),
                Topic.TOPIC_MINECRAFT_PUNISHMENT_TYPES.name()
        );

        assertTrue(MinecraftRealtimeClient.canStart(true, response));
    }

    @Test
    void canStartDoesNotAllowPresenceEvenWhenStartupProvidesServerInstanceId() {
        StartupResponse response = responseWithInstance("instance-1", Topic.TOPIC_MINECRAFT_PRESENCE.name());

        assertFalse(MinecraftRealtimeClient.canStart(true, response));
    }

    @Test
    void canStartDoesNotAllowPresenceWithoutServerInstanceId() {
        StartupResponse response = response(Topic.TOPIC_MINECRAFT_PRESENCE.name());

        assertFalse(MinecraftRealtimeClient.canStart(true, response));
    }

    @Test
    void terminalBackendErrorCodesStopReconnectLoop() {
        assertTrue(MinecraftRealtimeClient.isTerminalBackendError(ErrorCode.ERROR_CODE_UNAUTHORIZED));
        assertTrue(MinecraftRealtimeClient.isTerminalBackendError(ErrorCode.ERROR_CODE_FORBIDDEN));
        assertTrue(MinecraftRealtimeClient.isTerminalBackendError(ErrorCode.ERROR_CODE_UNSUPPORTED_PROTOCOL));
        assertFalse(MinecraftRealtimeClient.isTerminalBackendError(ErrorCode.ERROR_CODE_RATE_LIMITED));
        assertFalse(MinecraftRealtimeClient.isTerminalBackendError(ErrorCode.ERROR_CODE_INVALID_MESSAGE));
    }

    @Test
    void terminalCloseCodesStopReconnectLoopButTransientClosesStillRetry() {
        assertFalse(MinecraftRealtimeClient.shouldReconnectAfterClose(1008));
        assertFalse(MinecraftRealtimeClient.shouldReconnectAfterClose(1013));
        assertTrue(MinecraftRealtimeClient.shouldReconnectAfterClose(1001));
        assertTrue(MinecraftRealtimeClient.shouldReconnectAfterClose(1012));
        assertTrue(MinecraftRealtimeClient.shouldReconnectAfterClose(1006));
    }

    @Test
    void advisedReconnectDelayIsCappedForDeployDrainAdvice() {
        assertEquals(7_000, MinecraftRealtimeClient.capAdvisedReconnectDelayMs(7_000));
        assertEquals(60_000, MinecraftRealtimeClient.capAdvisedReconnectDelayMs(120_000));
        assertEquals(0, MinecraftRealtimeClient.capAdvisedReconnectDelayMs(-1));
    }

    private StartupResponse response(String... topics) {
        return responseWithInstance(null, topics);
    }

    private StartupResponse responseWithInstance(String serverInstanceId, String... topics) {
        return new StartupResponse(null, null, serverInstanceId, true,
                "wss://api.modl.gg/v1/realtime/ws", 1,
                topics == null ? Collections.emptyList() : Arrays.asList(topics));
    }
}
