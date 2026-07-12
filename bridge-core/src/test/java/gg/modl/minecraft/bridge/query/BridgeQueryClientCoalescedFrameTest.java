package gg.modl.minecraft.bridge.query;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.bridge.protocol.BridgeProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BridgeQueryClientCoalescedFrameTest {

    private static final byte AUTH_SUCCESS = 0x01;
    private static final String PANEL_URL_VALUE = "https://panel.example.test/path";

    @Test
    void authSuccessCoalescedWithPartialFrameBuffersUntilRemainderArrives() throws Exception {
        CapturingMessageHandler messageHandler = new CapturingMessageHandler();
        ChannelHandler handler = newClientHandler(messageHandler);

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("handler", handler);

        byte[] frame = lengthPrefixed(BridgeProtocol.encode(BridgeAction.PANEL_URL, PANEL_URL_VALUE));
        int split = BridgeProtocol.LENGTH_FIELD_LENGTH + (frame.length - BridgeProtocol.LENGTH_FIELD_LENGTH) / 2;

        ByteBuf authPlusPartialFrame = Unpooled.buffer();
        authPlusPartialFrame.writeByte(AUTH_SUCCESS);
        authPlusPartialFrame.writeBytes(Arrays.copyOfRange(frame, 0, split));
        channel.writeInbound(authPlusPartialFrame);

        assertNull(messageHandler.panelUrl,
                "a partial length-prefixed frame re-fed after AUTH_SUCCESS must not dispatch until complete");

        ByteBuf frameRemainder = Unpooled.buffer();
        frameRemainder.writeBytes(Arrays.copyOfRange(frame, split, frame.length));
        channel.writeInbound(frameRemainder);

        assertEquals(PANEL_URL_VALUE, messageHandler.panelUrl,
                "the completed frame must dispatch once the coalesced remainder is buffered and reassembled");

        channel.finishAndReleaseAll();
    }

    private static byte[] lengthPrefixed(byte[] payload) {
        byte[] frame = new byte[BridgeProtocol.LENGTH_FIELD_LENGTH + payload.length];
        frame[0] = (byte) (payload.length >>> 24);
        frame[1] = (byte) (payload.length >>> 16);
        frame[2] = (byte) (payload.length >>> 8);
        frame[3] = (byte) payload.length;
        System.arraycopy(payload, 0, frame, BridgeProtocol.LENGTH_FIELD_LENGTH, payload.length);
        return frame;
    }

    private static ChannelHandler newClientHandler(BridgeMessageHandler messageHandler) throws Exception {
        BridgeQueryClient client = new BridgeQueryClient(
                "localhost", 25590, "secret", "test-server",
                Logger.getLogger("bridge-query-client-test"), new NoOpScheduler(), messageHandler);
        Class<?> handlerClass = Class.forName(BridgeQueryClient.class.getName() + "$BridgeClientHandler");
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(BridgeQueryClient.class);
        constructor.setAccessible(true);
        return (ChannelHandler) constructor.newInstance(client);
    }

    private static final class CapturingMessageHandler implements BridgeMessageHandler {
        private volatile String panelUrl;

        @Override
        public void onPanelUrl(String panelUrl) {
            this.panelUrl = panelUrl;
        }

        @Override public void onFreeze(String targetUuid, String staffUuid) {}
        @Override public void onUnfreeze(String targetUuid) {}
        @Override public void onStaffModeEnter(String staffUuid, String staffName) {}
        @Override public void onStaffModeExit(String staffUuid, String staffName) {}
        @Override public void onVanishEnter(String staffUuid, String staffName) {}
        @Override public void onVanishExit(String staffUuid, String staffName) {}
        @Override public void onTargetRequest(String staffUuid, String targetUuid) {}
        @Override public void onStatWipe(String username, String uuid, String punishmentId) {}
        @Override public void onCaptureReplay(String targetUuid, String targetName) {}
    }

    private static final class NoOpScheduler implements BridgeScheduler {
        @Override public void runOnMainThread(Runnable task) {}
        @Override public void runForPlayer(UUID playerUuid, Runnable task) {}
        @Override public void runLater(Runnable task, long delayTicks) {}
        @Override public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {}
        @Override public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
            return () -> {};
        }
        @Override public void cancelTask(BridgeTask task) {}
    }
}
