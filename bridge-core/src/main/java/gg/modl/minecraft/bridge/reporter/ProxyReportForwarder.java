package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.query.BridgeQueryClient;

import java.util.function.Supplier;

/**
 * Builds a {@link TicketCreator} that forwards anti-cheat auto-reports to the proxy as a
 * CREATE_REPORT bridge message. Used by BRIDGE_ONLY-mode backends (Fabric) so they have a
 * non-null TicketCreator, mirroring the Spigot BRIDGE_ONLY wiring exactly (arg order and
 * replay-present/absent branch).
 */
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
