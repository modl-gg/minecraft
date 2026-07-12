package gg.modl.minecraft.core.support;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.AddPunishmentEvidenceRequest;
import gg.modl.minecraft.api.http.request.AddPunishmentNoteRequest;
import gg.modl.minecraft.api.http.request.ChangePunishmentDurationRequest;
import gg.modl.minecraft.api.http.request.ChatLogBatchRequest;
import gg.modl.minecraft.api.http.request.ClaimTicketRequest;
import gg.modl.minecraft.api.http.request.CommandLogBatchRequest;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import gg.modl.minecraft.api.http.request.ModifyPunishmentTicketsRequest;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerGetRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.request.StatWipeAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.request.TogglePunishmentOptionRequest;
import gg.modl.minecraft.api.http.response.ChatLogsResponse;
import gg.modl.minecraft.api.http.response.ClaimTicketResponse;
import gg.modl.minecraft.api.http.response.CommandLogsResponse;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.api.http.response.DashboardStatsResponse;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.PaginatedNotesResponse;
import gg.modl.minecraft.api.http.response.PaginatedPunishmentsResponse;
import gg.modl.minecraft.api.http.response.PardonResponse;
import gg.modl.minecraft.api.http.response.PlayerGetResponse;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.api.http.response.PlayerNoteCreateResponse;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PunishmentCreateResponse;
import gg.modl.minecraft.api.http.response.PunishmentDetailResponse;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.RecentPunishmentsResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.Staff2faTokenResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.api.http.response.StaffPermissionsResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FakeModlHttpClient implements ModlHttpClient {
    protected UnsupportedOperationException notStubbed(String method) {
        return new UnsupportedOperationException(method);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(UUID uuid) {
        throw notStubbed("getPlayerProfile");
    }

    @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(UUID uuid) {
        throw notStubbed("getLinkedAccounts");
    }

    @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(UUID uuid, int page, int limit) {
        throw notStubbed("getLinkedAccounts");
    }

    @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(PlayerLoginRequest request) {
        throw notStubbed("playerLogin");
    }

    @Override
    public CompletableFuture<Void> playerDisconnect(PlayerDisconnectRequest request) {
        throw notStubbed("playerDisconnect");
    }

    @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(PlayerGetRequest request) {
        throw notStubbed("getPlayer");
    }

    @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(PlayerNameRequest request) {
        throw notStubbed("getPlayer");
    }

    @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(PlayerLookupRequest request) {
        throw notStubbed("lookupPlayer");
    }

    @Override
    public CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(PlayerLookupRequest request) {
        throw notStubbed("lookupPlayerProfile");
    }

    @Override
    public CompletableFuture<Void> createPlayerNote(CreatePlayerNoteRequest request) {
        throw notStubbed("createPlayerNote");
    }

    @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(CreatePlayerNoteRequest request) {
        throw notStubbed("createPlayerNoteWithResponse");
    }

    @Override
    public CompletableFuture<PaginatedNotesResponse> getPlayerNotes(UUID uuid, int page, int limit) {
        throw notStubbed("getPlayerNotes");
    }

    @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        throw notStubbed("getOnlinePlayers");
    }

    @Override
    public CompletableFuture<Void> updatePlayerServer(String minecraftUuid, String serverName) {
        throw notStubbed("updatePlayerServer");
    }

    @Override
    public CompletableFuture<Void> submitIpInfo(String minecraftUUID, String ip, String country, String region, String asn, boolean proxy, boolean hosting) {
        throw notStubbed("submitIpInfo");
    }

    @Override
    public CompletableFuture<Void> acknowledgeNotifications(NotificationAcknowledgeRequest request) {
        throw notStubbed("acknowledgeNotifications");
    }

    @Override
    public CompletableFuture<Void> createPunishment(CreatePunishmentRequest request) {
        throw notStubbed("createPunishment");
    }

    @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(PunishmentCreateRequest request) {
        throw notStubbed("createPunishmentWithResponse");
    }

    @Override
    public CompletableFuture<Void> acknowledgePunishment(PunishmentAcknowledgeRequest request) {
        throw notStubbed("acknowledgePunishment");
    }

    @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        throw notStubbed("getPunishmentTypes");
    }

    @Override
    public CompletableFuture<PardonResponse> pardonPunishment(PardonPunishmentRequest request) {
        throw notStubbed("pardonPunishment");
    }

    @Override
    public CompletableFuture<PardonResponse> pardonPlayer(PardonPlayerRequest request) {
        throw notStubbed("pardonPlayer");
    }

    @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        throw notStubbed("getRecentPunishments");
    }

    @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(UUID playerUuid, int typeOrdinal) {
        throw notStubbed("getPunishmentPreview");
    }

    @Override
    public CompletableFuture<Void> addPunishmentNote(AddPunishmentNoteRequest request) {
        throw notStubbed("addPunishmentNote");
    }

    @Override
    public CompletableFuture<Void> addPunishmentEvidence(AddPunishmentEvidenceRequest request) {
        throw notStubbed("addPunishmentEvidence");
    }

    @Override
    public CompletableFuture<Void> changePunishmentDuration(ChangePunishmentDurationRequest request) {
        throw notStubbed("changePunishmentDuration");
    }

    @Override
    public CompletableFuture<Void> togglePunishmentOption(TogglePunishmentOptionRequest request) {
        throw notStubbed("togglePunishmentOption");
    }

    @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(String punishmentId) {
        throw notStubbed("getPunishmentDetail");
    }

    @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(String punishmentId, String issuerName) {
        throw notStubbed("createEvidenceUploadToken");
    }

    @Override
    public CompletableFuture<Void> modifyPunishmentTickets(ModifyPunishmentTicketsRequest request) {
        throw notStubbed("modifyPunishmentTickets");
    }

    @Override
    public CompletableFuture<PaginatedPunishmentsResponse> getPlayerPunishments(UUID uuid, int page, int limit) {
        throw notStubbed("getPlayerPunishments");
    }

    @Override
    public CompletableFuture<Void> acknowledgeStatWipe(StatWipeAcknowledgeRequest request) {
        throw notStubbed("acknowledgeStatWipe");
    }

    @Override
    public CompletableFuture<CreateTicketResponse> createTicket(CreateTicketRequest request) {
        throw notStubbed("createTicket");
    }

    @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(CreateTicketRequest request) {
        throw notStubbed("createUnfinishedTicket");
    }

    @Override
    public CompletableFuture<TicketsResponse> getTickets(String status, String type) {
        throw notStubbed("getTickets");
    }

    @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(ClaimTicketRequest request) {
        throw notStubbed("claimTicket");
    }

    @Override
    public CompletableFuture<TicketsResponse> getTicketsByIds(List<String> ticketIds) {
        throw notStubbed("getTicketsByIds");
    }

    @Override
    public CompletableFuture<ReportsResponse> getReports(String status) {
        throw notStubbed("getReports");
    }

    @Override
    public CompletableFuture<ReportsResponse> getPlayerReports(UUID playerUuid, String status) {
        throw notStubbed("getPlayerReports");
    }

    @Override
    public CompletableFuture<Void> dismissReport(String reportId, String dismissedBy, String reason) {
        throw notStubbed("dismissReport");
    }

    @Override
    public CompletableFuture<Void> resolveReport(String reportId, String resolvedBy, String resolution, String punishmentId) {
        throw notStubbed("resolveReport");
    }

    @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        throw notStubbed("getStaffPermissions");
    }

    @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        throw notStubbed("getStaffList");
    }

    @Override
    public CompletableFuture<Void> reportStaffDisconnect(String minecraftUuid, long sessionDurationMs) {
        throw notStubbed("reportStaffDisconnect");
    }

    @Override
    public CompletableFuture<Void> updateStaffRole(String staffId, String roleName, String actingStaffId) {
        throw notStubbed("updateStaffRole");
    }

    @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        throw notStubbed("getRoles");
    }

    @Override
    public CompletableFuture<Void> updateRolePermissions(String roleId, List<String> permissions, String actingStaffId) {
        throw notStubbed("updateRolePermissions");
    }

    @Override
    public CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(String minecraftUuid, String ip) {
        throw notStubbed("generateStaff2faToken");
    }

    @Override
    public CompletableFuture<Void> submitChatLogs(ChatLogBatchRequest request) {
        throw notStubbed("submitChatLogs");
    }

    @Override
    public CompletableFuture<Void> submitCommandLogs(CommandLogBatchRequest request) {
        throw notStubbed("submitCommandLogs");
    }

    @Override
    public CompletableFuture<ChatLogsResponse> getChatLogs(String playerUuid, int limit) {
        throw notStubbed("getChatLogs");
    }

    @Override
    public CompletableFuture<CommandLogsResponse> getCommandLogs(String playerUuid, int limit) {
        throw notStubbed("getCommandLogs");
    }

    @Override
    public CompletableFuture<SyncResponse> sync(SyncRequest request) {
        throw notStubbed("sync");
    }

    @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        throw notStubbed("getDashboardStats");
    }

    @Override
    public CompletableFuture<Void> updateMigrationStatus(MigrationStatusUpdateRequest request) {
        throw notStubbed("updateMigrationStatus");
    }

    @Override
    public CompletableFuture<Boolean> uploadMigrationFile(File file) {
        throw notStubbed("uploadMigrationFile");
    }

}
