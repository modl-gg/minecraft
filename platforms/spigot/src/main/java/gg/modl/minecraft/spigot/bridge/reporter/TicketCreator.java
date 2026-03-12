package gg.modl.minecraft.spigot.bridge.reporter;

@FunctionalInterface
public interface TicketCreator {
    void createTicket(String creatorUuid, String creatorName, String type,
                      String subject, String description,
                      String reportedPlayerUuid, String reportedPlayerName,
                      String tagsJoined, String priority, String createdServer);
}
