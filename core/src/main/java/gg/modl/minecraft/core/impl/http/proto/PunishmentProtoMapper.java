package gg.modl.minecraft.core.impl.http.proto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps punishment-domain DTOs to/from their proto V3 counterparts. Mirrors the inline mappers in the
 * backend {@code MinecraftPunishmentV3Controller}, inverted (domain&rarr;proto requests, proto&rarr;domain responses).
 */
public final class PunishmentProtoMapper {

    private static final Gson GSON = new Gson();
    private static final Type STRING_OBJECT_MAP = new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
    }.getType();

    private PunishmentProtoMapper() {
    }

    // ---- Requests (domain -> proto) ----

    public static gg.modl.proto.modl.v1.CreatePunishmentRequest toProto(CreatePunishmentRequest request) {
        gg.modl.proto.modl.v1.CreatePunishmentRequest.Builder builder =
            gg.modl.proto.modl.v1.CreatePunishmentRequest.newBuilder()
                .setTargetUuid(request.getTargetUuid())
                .setTypeOrdinal(request.getTypeOrdinal())
                .setDuration(request.getDuration());

        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getReason() != null) builder.setReason(request.getReason());
        if (request.getNotes() != null) builder.addAllNotes(request.getNotes());
        if (request.getAttachedTicketIds() != null) builder.addAllAttachedTicketIds(request.getAttachedTicketIds());
        if (request.getData() != null) builder.setData(ProtoConversions.mapToStruct(jsonObjectToMap(request.getData())));
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.CreatePunishmentRequest toProto(PunishmentCreateRequest request) {
        gg.modl.proto.modl.v1.CreatePunishmentRequest.Builder builder =
            gg.modl.proto.modl.v1.CreatePunishmentRequest.newBuilder()
                .setTargetUuid(request.getTargetUuid())
                .setTypeOrdinal(request.getTypeOrdinal());

        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getReason() != null) builder.setReason(request.getReason());
        if (request.getSeverity() != null) builder.setSeverity(request.getSeverity());
        if (request.getStatus() != null) builder.setStatus(request.getStatus());
        if (request.getDuration() != null) builder.setDuration(request.getDuration());
        if (request.getNotes() != null) builder.addAllNotes(request.getNotes());
        if (request.getAttachedTicketIds() != null) builder.addAllAttachedTicketIds(request.getAttachedTicketIds());
        if (request.getData() != null) builder.setData(ProtoConversions.mapToStruct(request.getData()));
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.AddPunishmentNoteRequest toProto(AddPunishmentNoteRequest request) {
        gg.modl.proto.modl.v1.AddPunishmentNoteRequest.Builder builder =
            gg.modl.proto.modl.v1.AddPunishmentNoteRequest.newBuilder()
                .setNote(request.getNote());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.AddPunishmentEvidenceRequest toProto(AddPunishmentEvidenceRequest request) {
        gg.modl.proto.modl.v1.AddPunishmentEvidenceRequest.Builder builder =
            gg.modl.proto.modl.v1.AddPunishmentEvidenceRequest.newBuilder()
                .setEvidenceUrl(request.getEvidenceUrl());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.ChangePunishmentDurationRequest toProto(ChangePunishmentDurationRequest request) {
        gg.modl.proto.modl.v1.ChangePunishmentDurationRequest.Builder builder =
            gg.modl.proto.modl.v1.ChangePunishmentDurationRequest.newBuilder();
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getNewDuration() != null) builder.setNewDuration(request.getNewDuration());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.TogglePunishmentOptionRequest toProto(TogglePunishmentOptionRequest request) {
        gg.modl.proto.modl.v1.TogglePunishmentOptionRequest.Builder builder =
            gg.modl.proto.modl.v1.TogglePunishmentOptionRequest.newBuilder()
                .setOption(request.getOption())
                .setEnabled(request.isEnabled());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.ModifyPunishmentTicketsRequest toProto(ModifyPunishmentTicketsRequest request) {
        gg.modl.proto.modl.v1.ModifyPunishmentTicketsRequest.Builder builder =
            gg.modl.proto.modl.v1.ModifyPunishmentTicketsRequest.newBuilder()
                .setModifyAssociatedTickets(request.isModifyAssociatedTickets());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getAddTicketIds() != null) builder.addAllAddTicketIds(request.getAddTicketIds());
        if (request.getRemoveTicketIds() != null) builder.addAllRemoveTicketIds(request.getRemoveTicketIds());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.PardonPunishmentRequest toProto(PardonPunishmentRequest request) {
        gg.modl.proto.modl.v1.PardonPunishmentRequest.Builder builder =
            gg.modl.proto.modl.v1.PardonPunishmentRequest.newBuilder();
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getReason() != null) builder.setReason(request.getReason());
        if (request.getExpectedType() != null) builder.setExpectedType(request.getExpectedType());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.StatWipeAcknowledgeRequest toProto(StatWipeAcknowledgeRequest request) {
        gg.modl.proto.modl.v1.StatWipeAcknowledgeRequest.Builder builder =
            gg.modl.proto.modl.v1.StatWipeAcknowledgeRequest.newBuilder()
                .setPunishmentId(request.getPunishmentId())
                .setSuccess(request.isSuccess());
        if (request.getServerName() != null) builder.setServerName(request.getServerName());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.CreateEvidenceUploadTokenRequest toUploadTokenRequest(String issuerName) {
        gg.modl.proto.modl.v1.CreateEvidenceUploadTokenRequest.Builder builder =
            gg.modl.proto.modl.v1.CreateEvidenceUploadTokenRequest.newBuilder();
        if (issuerName != null) builder.setIssuerName(issuerName);
        return builder.build();
    }

    // ---- Responses (proto -> domain) ----

    public static PunishmentCreateResponse toPunishmentCreateResponse(gg.modl.proto.modl.v1.PunishmentCreateResponse proto) {
        return new PunishmentCreateResponse(proto.getMessage(), proto.getPunishmentId(), proto.getStatus());
    }

    public static EvidenceUploadTokenResponse toUploadTokenResponse(gg.modl.proto.modl.v1.EvidenceUploadTokenResponse proto) {
        EvidenceUploadTokenResponse response = new EvidenceUploadTokenResponse();
        response.setToken(ProtoConversions.emptyToNull(proto.getToken()));
        response.setStatus(proto.getStatus());
        return response;
    }

    public static PunishmentPreviewResponse toPreviewResponse(gg.modl.proto.modl.v1.PunishmentPreviewResponse proto) {
        PunishmentPreviewResponse response = new PunishmentPreviewResponse();
        response.setMessage(proto.getMessage());
        response.setSocialStatus(proto.getSocialStatus());
        response.setGameplayStatus(proto.getGameplayStatus());
        response.setOffenderStatus(proto.getOffenderStatus());
        response.setCategory(proto.getCategory());
        response.setSuccess(proto.getSuccess());
        response.setSingleSeverityPunishment(proto.getSingleSeverityPunishment());
        response.setPermanentUntilUsernameChange(proto.getPermanentUntilUsernameChange());
        response.setPermanentUntilSkinChange(proto.getPermanentUntilSkinChange());
        response.setCanBeAltBlocking(proto.getCanBeAltBlocking());
        response.setCanBeStatWiping(proto.getCanBeStatWiping());
        response.setStatus(proto.getStatus());
        response.setSocialPoints(proto.getSocialPoints());
        response.setGameplayPoints(proto.getGameplayPoints());

        if (proto.hasLenient()) response.setLenient(toSeverityPreview(proto.getLenient()));
        if (proto.hasRegular()) response.setRegular(toSeverityPreview(proto.getRegular()));
        if (proto.hasAggravated()) response.setAggravated(toSeverityPreview(proto.getAggravated()));
        if (proto.hasSingleSeverity()) response.setSingleSeverity(toSeverityPreview(proto.getSingleSeverity()));
        return response;
    }

    private static PunishmentPreviewResponse.SeverityPreview toSeverityPreview(
        gg.modl.proto.modl.v1.PunishmentPreviewResponse.SeverityPreview proto) {
        PunishmentPreviewResponse.SeverityPreview preview = new PunishmentPreviewResponse.SeverityPreview();
        preview.setSeverity(proto.getSeverity());
        preview.setDurationFormatted(proto.getDurationFormatted());
        preview.setPunishmentType(proto.getPunishmentType());
        preview.setNewSocialStatus(proto.getNewSocialStatus());
        preview.setNewGameplayStatus(proto.getNewGameplayStatus());
        preview.setPermanent(proto.getPermanent());
        preview.setPoints(proto.getPoints());
        preview.setNewSocialPoints(proto.getNewSocialPoints());
        preview.setNewGameplayPoints(proto.getNewGameplayPoints());
        preview.setDurationMs(proto.getDurationMs());
        return preview;
    }

    public static PunishmentDetailResponse toDetailResponse(gg.modl.proto.modl.v1.PunishmentDetailResponse proto) {
        PunishmentDetailResponse response = new PunishmentDetailResponse();
        response.setStatus(proto.getStatus());

        gg.modl.proto.modl.v1.PunishmentDetailResponse.PunishmentDetailEntry entry = proto.getPunishment();
        PunishmentDetailResponse.PunishmentDetail detail = new PunishmentDetailResponse.PunishmentDetail();
        detail.setId(entry.getId());
        detail.setPlayerUuid(entry.getPlayerUuid());
        detail.setPlayerName(entry.getPlayerName());
        detail.setIssuerName(entry.getIssuerName());
        detail.setIssued(entry.getIssued());
        detail.setStarted(entry.getStarted());
        detail.setType(entry.getType());
        detail.setTypeOrdinal(entry.getTypeOrdinal());
        detail.setData(ProtoConversions.structToMap(entry.getData()));

        List<Object> modifications = new ArrayList<>();
        entry.getModificationsList().forEach(s -> modifications.add(ProtoConversions.structToMap(s)));
        detail.setModifications(modifications);

        List<Object> notes = new ArrayList<>();
        entry.getNotesList().forEach(s -> notes.add(ProtoConversions.structToMap(s)));
        detail.setNotes(notes);

        List<Object> evidence = new ArrayList<>();
        entry.getEvidenceList().forEach(s -> evidence.add(ProtoConversions.structToMap(s)));
        detail.setEvidence(evidence);

        response.setPunishment(detail);
        return response;
    }

    public static RecentPunishmentsResponse toRecentResponse(gg.modl.proto.modl.v1.RecentPunishmentsResponse proto) {
        List<RecentPunishmentsResponse.RecentPunishment> punishments = new ArrayList<>();
        proto.getPunishmentsList().forEach(p -> punishments.add(toRecentPunishment(p)));
        return new RecentPunishmentsResponse(punishments, proto.getStatus());
    }

    private static RecentPunishmentsResponse.RecentPunishment toRecentPunishment(
        gg.modl.proto.modl.v1.RecentPunishmentsResponse.RecentPunishment proto) {
        RecentPunishmentsResponse.RecentPunishment punishment = new RecentPunishmentsResponse.RecentPunishment();
        punishment.setPlayerName(proto.getPlayerName());
        punishment.setPlayerUuid(proto.getPlayerUuid());
        punishment.setId(proto.getId());
        punishment.setIssuerName(proto.getIssuerName());
        punishment.setIssued(ProtoConversions.dateFromMillis(proto.getIssued()));
        if (proto.hasStarted()) punishment.setStarted(ProtoConversions.dateFromMillis(proto.getStarted()));
        punishment.setType(proto.getType());
        if (proto.hasTypeOrdinal()) punishment.setTypeOrdinal(proto.getTypeOrdinal());

        List<Modification> modifications = new ArrayList<>();
        proto.getModificationsList().forEach(m -> modifications.add(PlayerProtoMapper.toModification(m)));
        punishment.setModifications(modifications);

        List<Note> notes = new ArrayList<>();
        proto.getNotesList().forEach(n -> notes.add(new Note(
            n.getText(),
            ProtoConversions.dateFromMillis(n.getDate()),
            n.hasIssuerName() ? n.getIssuerName() : null,
            n.hasIssuerId() ? n.getIssuerId() : null)));
        punishment.setNotes(notes);

        List<Evidence> evidence = new ArrayList<>();
        proto.getEvidenceList().forEach(e -> evidence.add(PlayerProtoMapper.toEvidence(e)));
        punishment.setEvidence(evidence);

        punishment.setAttachedTicketIds(new ArrayList<>(proto.getAttachedTicketIdsList()));
        if (proto.hasData()) punishment.setData(ProtoConversions.structToMap(proto.getData()));
        return punishment;
    }

    private static Map<String, Object> jsonObjectToMap(JsonObject json) {
        return GSON.fromJson(json, STRING_OBJECT_MAP);
    }
}
