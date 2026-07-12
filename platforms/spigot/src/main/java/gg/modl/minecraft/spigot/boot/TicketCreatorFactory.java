package gg.modl.minecraft.spigot.boot;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.spigot.bridge.BridgeComponent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static gg.modl.minecraft.core.util.Java8Collections.listOf;

public final class TicketCreatorFactory {

    private TicketCreatorFactory() {}

    public static TicketCreator standalone(JavaPlugin plugin, Supplier<ModlHttpClient> httpClientSupplier) {
        return (creatorUuid, creatorName, type, subject, description,
                reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
            List<String> tags = tagsJoined == null || tagsJoined.isEmpty() ? listOf() : Arrays.asList(tagsJoined.split(","));
            CreateTicketRequest request = new CreateTicketRequest(
                    creatorUuid, type, creatorName, subject, description,
                    reportedPlayerUuid, reportedPlayerName, priority, createdServer,
                    null, tags, replayUrl
            );
            httpClientSupplier.get().createTicket(request).thenAccept(response -> {
                if (response.isSuccess()) {
                    plugin.getLogger().info("[bridge] Report ticket created: " + response.getTicketId());
                } else {
                    plugin.getLogger().warning("[bridge] Failed to create report ticket: " + response.getMessage());
                }
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("[bridge] Error creating report ticket: " + throwable.getMessage());
                return null;
            });
        };
    }

    public static TicketCreator bridgeOnly(BridgeComponent bridgeComponent) {
        return (creatorUuid, creatorName, type, subject, description,
                reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
            if (bridgeComponent.getBridgeClient() == null) return;

            String tags = tagsJoined != null ? tagsJoined : "";
            if (replayUrl != null && !replayUrl.isEmpty()) {
                bridgeComponent.getBridgeClient().sendMessage("CREATE_REPORT",
                        creatorUuid, creatorName, type, subject, description,
                        reportedPlayerUuid, reportedPlayerName, tags, priority, createdServer, replayUrl);
            } else {
                bridgeComponent.getBridgeClient().sendMessage("CREATE_REPORT",
                        creatorUuid, creatorName, type, subject, description,
                        reportedPlayerUuid, reportedPlayerName, tags, priority, createdServer);
            }
        };
    }
}
