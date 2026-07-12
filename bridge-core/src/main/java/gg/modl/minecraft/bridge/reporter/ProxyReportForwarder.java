package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.query.BridgeQueryClient;

import java.util.function.Supplier;

public final class ProxyReportForwarder {

    private ProxyReportForwarder() {
    }

    public static TicketCreator create(Supplier<BridgeQueryClient> clientSupplier) {
        return (creatorUuid, creatorName, type, subject, description,
                reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
            BridgeQueryClient client = clientSupplier.get();
            if (client == null) return;
            String tags = tagsJoined != null ? tagsJoined : "";
            if (replayUrl != null && !replayUrl.isEmpty()) {
                client.sendMessage("CREATE_REPORT",
                        creatorUuid, creatorName, type, subject, description,
                        reportedPlayerUuid, reportedPlayerName, tags,
                        priority, createdServer, replayUrl);
            } else {
                client.sendMessage("CREATE_REPORT",
                        creatorUuid, creatorName, type, subject, description,
                        reportedPlayerUuid, reportedPlayerName, tags,
                        priority, createdServer);
            }
        };
    }
}
