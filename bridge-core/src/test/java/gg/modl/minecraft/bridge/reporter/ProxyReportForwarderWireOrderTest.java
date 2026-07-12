package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.bridge.protocol.BridgeProtocol;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyReportForwarderWireOrderTest {

    @Test
    void createReportWithReplayUrlPreservesWireOrder() throws IOException {
        TicketRequest request = representativeRequest("replay-url");

        List<String> wire = capturedWireSequence(request);

        assertEquals(Arrays.asList(
                "CREATE_REPORT",
                "v-creatorUuid",
                "v-creatorName",
                "v-type",
                "v-subject",
                "v-description",
                "v-reportedPlayerUuid",
                "v-reportedPlayerName",
                "tag1,tag2",
                "v-priority",
                "v-createdServer",
                "replay-url"
        ), wire);
    }

    @Test
    void createReportWithoutReplayUrlOmitsTrailingReplayField() throws IOException {
        TicketRequest request = representativeRequest(null);

        List<String> wire = capturedWireSequence(request);

        assertEquals(Arrays.asList(
                "CREATE_REPORT",
                "v-creatorUuid",
                "v-creatorName",
                "v-type",
                "v-subject",
                "v-description",
                "v-reportedPlayerUuid",
                "v-reportedPlayerName",
                "tag1,tag2",
                "v-priority",
                "v-createdServer"
        ), wire);
    }

    @Test
    void nullTagsAreEmittedAsEmptyStringOnWire() throws IOException {
        TicketRequest request = TicketRequest.builder()
                .creatorUuid("v-creatorUuid")
                .creatorName("v-creatorName")
                .type("v-type")
                .subject("v-subject")
                .description("v-description")
                .reportedPlayerUuid("v-reportedPlayerUuid")
                .reportedPlayerName("v-reportedPlayerName")
                .priority("v-priority")
                .createdServer("v-createdServer")
                .build();

        List<String> wire = capturedWireSequence(request);

        assertEquals("", wire.get(8));
    }

    private static TicketRequest representativeRequest(String replayUrl) {
        return TicketRequest.builder()
                .creatorUuid("v-creatorUuid")
                .creatorName("v-creatorName")
                .type("v-type")
                .subject("v-subject")
                .description("v-description")
                .reportedPlayerUuid("v-reportedPlayerUuid")
                .reportedPlayerName("v-reportedPlayerName")
                .tagsJoined("tag1,tag2")
                .priority("v-priority")
                .createdServer("v-createdServer")
                .replayUrl(replayUrl)
                .build();
    }

    private static List<String> capturedWireSequence(TicketRequest request) throws IOException {
        CapturingBridgeQueryClient client = new CapturingBridgeQueryClient();
        try {
            ProxyReportForwarder.create(() -> client).createTicket(request);
        } finally {
            client.shutdown();
        }
        return decodeFrame(BridgeProtocol.encode(client.action, client.args));
    }

    private static List<String> decodeFrame(byte[] frame) throws IOException {
        List<String> fields = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            while (true) {
                fields.add(in.readUTF());
            }
        } catch (EOFException end) {
            return fields;
        }
    }

    private static final class CapturingBridgeQueryClient extends BridgeQueryClient {
        private String action;
        private String[] args;

        private CapturingBridgeQueryClient() {
            super("localhost", 25599, "secret", "test-server",
                    PluginLogger.fromJul(Logger.getLogger("wire-order-test")), null, null);
        }

        @Override
        public void sendMessage(String action, String... args) {
            this.action = action;
            this.args = args;
        }
    }
}
