package gg.modl.minecraft.spigot.boot;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.bridge.reporter.ProxyReportForwarder;
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
        return report -> {
            String tagsJoined = report.getTagsJoined();
            List<String> tags = tagsJoined == null || tagsJoined.isEmpty() ? listOf() : Arrays.asList(tagsJoined.split(","));
            CreateTicketRequest request = CreateTicketRequest.builder()
                    .creatorUuid(report.getCreatorUuid())
                    .type(report.getType())
                    .creatorName(report.getCreatorName())
                    .subject(report.getSubject())
                    .description(report.getDescription())
                    .reportedPlayerUuid(report.getReportedPlayerUuid())
                    .reportedPlayerName(report.getReportedPlayerName())
                    .priority(report.getPriority())
                    .createdServer(report.getCreatedServer())
                    .tags(tags)
                    .replayUrl(report.getReplayUrl())
                    .build();
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
        return ProxyReportForwarder.create(bridgeComponent::getBridgeClient);
    }
}
