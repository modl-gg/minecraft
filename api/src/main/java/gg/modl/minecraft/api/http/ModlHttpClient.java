package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.*;
import gg.modl.minecraft.api.http.response.*;
import gg.modl.minecraft.api.http.request.*;
import gg.modl.minecraft.api.http.response.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ModlHttpClient {
    @NotNull
    CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid);

    @NotNull
    CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid);

    @NotNull
    CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request);

    @NotNull
    CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request);

    @NotNull
    CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request);

    @NotNull
    CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request);

    // Legacy methods for backward compatibility
    @NotNull
    CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request);

    @NotNull
    CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request);

    // New methods with proper return types
    @NotNull
    CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request);

    @NotNull
    CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request);

    @NotNull
    CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request);

    @NotNull
    CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request);

    // Sync and acknowledgment methods
    @NotNull
    CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request);

    @NotNull
    CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request);

    @NotNull
    CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request);

    @NotNull
    CompletableFuture<PunishmentTypesResponse> getPunishmentTypes();

    @NotNull
    CompletableFuture<StaffPermissionsResponse> getStaffPermissions();

    @NotNull
    CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request);

    // Pardon methods
    @NotNull
    CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request);

    @NotNull
    CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request);

    @NotNull
    CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request);

    @NotNull
    CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                          String country, String region, String asn, boolean proxy, boolean hosting);

    // Staff menu endpoints
    @NotNull
    CompletableFuture<OnlinePlayersResponse> getOnlinePlayers();

    @NotNull
    CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours);

    @NotNull
    CompletableFuture<ReportsResponse> getReports(String status);

    @NotNull
    CompletableFuture<ReportsResponse> getPlayerReports(@NotNull UUID playerUuid, String status);

    @NotNull
    CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason);

    @NotNull
    CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId);

    @NotNull
    CompletableFuture<TicketsResponse> getTickets(String status, String type);

    @NotNull
    CompletableFuture<DashboardStatsResponse> getDashboardStats();

    // Punishment preview
    @NotNull
    CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal);

    // Punishment modification methods
    @NotNull
    CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request);

    @NotNull
    CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request);

    @NotNull
    CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request);

    @NotNull
    CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request);

    // Ticket claim method
    @NotNull
    CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request);

    // Staff management endpoints
    @NotNull
    CompletableFuture<StaffListResponse> getStaffList();

    @NotNull
    CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName);

    // Role management endpoints
    @NotNull
    CompletableFuture<RolesListResponse> getRoles();

    @NotNull
    CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions);

    // Punishment detail lookup
    @NotNull
    CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId);

    // Evidence upload token
    @NotNull
    CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName);
}