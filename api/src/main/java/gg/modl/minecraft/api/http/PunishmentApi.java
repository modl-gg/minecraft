package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.api.http.response.PaginatedPunishmentsResponse;
import gg.modl.minecraft.api.http.response.PardonResponse;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentApi {

    @NotNull CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request);

    @NotNull CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request);

    @NotNull CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request);

    @NotNull CompletableFuture<PunishmentTypesResponse> getPunishmentTypes();

    @NotNull CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request);

    @NotNull CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request);

    @NotNull CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours);

    @NotNull CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal);

    @NotNull CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request);

    @NotNull CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request);

    @NotNull CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request);

    @NotNull CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request);

    @NotNull CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId);

    @NotNull CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName);

    @NotNull CompletableFuture<Void> modifyPunishmentTickets(@NotNull ModifyPunishmentTicketsRequest request);

    @NotNull CompletableFuture<PaginatedPunishmentsResponse> getPlayerPunishments(@NotNull UUID uuid, int page, int limit);

    @NotNull CompletableFuture<Void> acknowledgeStatWipe(@NotNull StatWipeAcknowledgeRequest request);
}
