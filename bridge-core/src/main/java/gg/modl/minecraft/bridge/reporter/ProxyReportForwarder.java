package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.query.BridgeQueryClient;

import java.util.function.Supplier;

public final class ProxyReportForwarder {

    private ProxyReportForwarder() {
    }

    public static TicketCreator create(Supplier<BridgeQueryClient> clientSupplier) {
        return request -> {
            BridgeQueryClient client = clientSupplier.get();
            if (client == null) return;
            String tags = request.getTagsJoined() != null ? request.getTagsJoined() : "";
            String replayUrl = request.getReplayUrl();
            if (replayUrl != null && !replayUrl.isEmpty()) {
                client.sendMessage("CREATE_REPORT",
                        request.getCreatorUuid(), request.getCreatorName(), request.getType(),
                        request.getSubject(), request.getDescription(),
                        request.getReportedPlayerUuid(), request.getReportedPlayerName(), tags,
                        request.getPriority(), request.getCreatedServer(), replayUrl);
            } else {
                client.sendMessage("CREATE_REPORT",
                        request.getCreatorUuid(), request.getCreatorName(), request.getType(),
                        request.getSubject(), request.getDescription(),
                        request.getReportedPlayerUuid(), request.getReportedPlayerName(), tags,
                        request.getPriority(), request.getCreatedServer());
            }
        };
    }
}
