package gg.modl.minecraft.core.impl.http.proto;

import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.ClaimTicketResponse;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.proto.modl.v1.MinecraftTicketListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps ticket-domain DTOs to/from their proto V3 counterparts. Inverse of the backend
 * {@code MinecraftTicketProtoMapper} (domain&rarr;proto requests, proto&rarr;domain responses).
 * Report responses reuse {@link PlayerProtoMapper#toReportsResponse}.
 */
public final class TicketProtoMapper {

    private TicketProtoMapper() {
    }

    // ---- Requests (domain -> proto) ----

    public static gg.modl.proto.modl.v1.MinecraftCreateTicketRequest toProto(CreateTicketRequest request) {
        gg.modl.proto.modl.v1.MinecraftCreateTicketRequest.Builder builder =
            gg.modl.proto.modl.v1.MinecraftCreateTicketRequest.newBuilder()
                .setCreatorUuid(request.getCreatorUuid())
                .setType(request.getType());

        if (request.getCreatorName() != null) builder.setCreatorName(request.getCreatorName());
        if (request.getSubject() != null) builder.setSubject(request.getSubject());
        if (request.getDescription() != null) builder.setDescription(request.getDescription());
        if (request.getReportedPlayerUuid() != null) builder.setReportedPlayerUuid(request.getReportedPlayerUuid());
        if (request.getReportedPlayerName() != null) builder.setReportedPlayerName(request.getReportedPlayerName());
        if (request.getPriority() != null) builder.setPriority(request.getPriority());
        if (request.getCreatedServer() != null) builder.setCreatedServer(request.getCreatedServer());
        if (request.getReplayUrl() != null) builder.setReplayUrl(request.getReplayUrl());
        if (request.getChatMessages() != null) builder.addAllChatMessages(request.getChatMessages());
        if (request.getTags() != null) builder.addAllTags(request.getTags());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.MinecraftClaimTicketRequest toProto(ClaimTicketRequest request) {
        gg.modl.proto.modl.v1.MinecraftClaimTicketRequest.Builder builder =
            gg.modl.proto.modl.v1.MinecraftClaimTicketRequest.newBuilder();
        if (request.getPlayerUuid() != null) builder.setPlayerUuid(request.getPlayerUuid());
        if (request.getPlayerName() != null) builder.setPlayerName(request.getPlayerName());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.MinecraftTicketsByIdsRequest toTicketsByIdsRequest(List<String> ticketIds) {
        return gg.modl.proto.modl.v1.MinecraftTicketsByIdsRequest.newBuilder()
            .addAllIds(ticketIds)
            .build();
    }

    public static gg.modl.proto.modl.v1.DismissReportRequest toDismissReportRequest(String dismissedBy, String reason) {
        gg.modl.proto.modl.v1.DismissReportRequest.Builder builder =
            gg.modl.proto.modl.v1.DismissReportRequest.newBuilder();
        if (dismissedBy != null) builder.setDismissedBy(dismissedBy);
        if (reason != null) builder.setReason(reason);
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.ResolveReportRequest toResolveReportRequest(
        String resolvedBy, String resolution, String punishmentId) {
        gg.modl.proto.modl.v1.ResolveReportRequest.Builder builder =
            gg.modl.proto.modl.v1.ResolveReportRequest.newBuilder();
        if (resolvedBy != null) builder.setResolvedBy(resolvedBy);
        if (resolution != null) builder.setResolution(resolution);
        if (punishmentId != null) builder.setPunishmentId(punishmentId);
        return builder.build();
    }

    // ---- Responses (proto -> domain) ----

    public static CreateTicketResponse toCreateTicketResponse(gg.modl.proto.modl.v1.MinecraftCreateTicketResponse proto) {
        CreateTicketResponse response = new CreateTicketResponse();
        response.setTicketId(ProtoConversions.emptyToNull(proto.getTicketId()));
        response.setMessage(proto.getMessage());
        response.setSuccess(proto.getSuccess());
        return response;
    }

    public static ClaimTicketResponse toClaimTicketResponse(gg.modl.proto.modl.v1.ClaimTicketResponse proto) {
        return new ClaimTicketResponse(
            proto.getMessage(),
            ProtoConversions.emptyToNull(proto.getTicketId()),
            ProtoConversions.emptyToNull(proto.getSubject()),
            proto.getStatus(),
            proto.getSuccess());
    }

    public static TicketsResponse toTicketsResponse(gg.modl.proto.modl.v1.TicketsResponse proto) {
        List<TicketsResponse.Ticket> tickets = new ArrayList<>();
        proto.getTicketsList().forEach(t -> tickets.add(toTicket(t)));
        return new TicketsResponse(tickets, proto.getStatus());
    }

    private static TicketsResponse.Ticket toTicket(MinecraftTicketListItem proto) {
        TicketsResponse.Ticket ticket = new TicketsResponse.Ticket();
        ticket.setId(proto.getId());
        ticket.setType(proto.getType());
        ticket.setCategory(proto.getCategory());
        ticket.setSubject(proto.getSubject());
        ticket.setStatus(proto.getStatus());
        ticket.setPlayerName(proto.getPlayerName());
        ticket.setPlayerUuid(proto.getPlayerUuid());
        ticket.setPriority(proto.getPriority());
        ticket.setFirstReplyContent(proto.hasFirstReplyContent() ? proto.getFirstReplyContent() : null);
        ticket.setAssignedTo(new ArrayList<>(proto.getAssignedToList()));
        ticket.setCreatedAt(ProtoConversions.dateFromMillis(proto.getCreatedAt()));
        ticket.setUpdatedAt(proto.hasUpdatedAt() ? ProtoConversions.dateFromMillis(proto.getUpdatedAt()) : null);
        ticket.setHasStaffResponse(proto.getHasStaffResponse());
        ticket.setLocked(proto.getLocked());
        ticket.setReplyCount(proto.getReplyCount());
        return ticket;
    }
}
