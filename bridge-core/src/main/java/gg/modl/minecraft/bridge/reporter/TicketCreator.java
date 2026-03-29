package gg.modl.minecraft.bridge.reporter;

@FunctionalInterface
public interface TicketCreator {
    void createTicket(String creatorUuid, String creatorName, String type,
                      String subject, String description,
                      String reportedPlayerUuid, String reportedPlayerName,
                      String tagsJoined, String priority, String createdServer,
                      String replayUrl);

    default void createTicket(String creatorUuid, String creatorName, String type,
                              String subject, String description,
                              String reportedPlayerUuid, String reportedPlayerName,
                              String tagsJoined, String priority, String createdServer) {
        createTicket(creatorUuid, creatorName, type, subject, description,
                reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, null);
    }
}
