package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.ClaimTicketResponse;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TicketApi {

    @NotNull CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request);

    @NotNull CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request);

    @NotNull CompletableFuture<TicketsResponse> getTickets(String status, String type);

    @NotNull CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request);

    @NotNull CompletableFuture<TicketsResponse> getTicketsByIds(@NotNull List<String> ticketIds);
}
