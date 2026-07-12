package gg.modl.minecraft.bridge.reporter;

@FunctionalInterface
public interface TicketCreator {
    void createTicket(TicketRequest request);
}
