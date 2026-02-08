package gg.modl.minecraft.core.impl.http;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.*;
import gg.modl.minecraft.api.http.response.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * HTTP client implementation that tries V2 API first, then falls back to V1 if V2 fails.
 *
 * Flow:
 * 1. Try V2 API (api.modl.gg or api.modl.top based on testing flag)
 * 2. If V2 fails/errors, automatically fall back to V1 API ({panel-url}/api)
 *
 * Once a fallback occurs, subsequent requests continue using V1 to avoid repeated failures.
 */
public class ModlHttpClientWithFallback implements ModlHttpClient {
    private static final Logger logger = Logger.getLogger(ModlHttpClientWithFallback.class.getName());

    @NotNull
    private final ModlHttpClient v2Client;
    @NotNull
    private final ModlHttpClient v1Client;

    // Track if we've fallen back to V1 to avoid repeated V2 failures
    private final AtomicBoolean useFallback = new AtomicBoolean(false);

    /**
     * Creates a fallback-enabled HTTP client.
     *
     * @param v2Client The V2 API client (api.modl.gg or api.modl.top)
     * @param v1Client The V1 API client ({panel-url}/api)
     */
    public ModlHttpClientWithFallback(@NotNull ModlHttpClient v2Client, @NotNull ModlHttpClient v1Client) {
        this.v2Client = v2Client;
        this.v1Client = v1Client;
    }

    /**
     * Helper method to execute a request with automatic fallback.
     * Tries V2 first, falls back to V1 on failure.
     */
    private <T> CompletableFuture<T> withFallback(
            java.util.function.Supplier<CompletableFuture<T>> v2Call,
            java.util.function.Supplier<CompletableFuture<T>> v1Call,
            String operationName) {

        // If we've already fallen back to V1, skip V2 entirely
        if (useFallback.get()) {
            return v1Call.get();
        }

        return v2Call.get()
                .handle((result, ex) -> {
                    if (ex != null) {
                        // V2 failed - log and mark for fallback
                        logger.warning(String.format("[%s] V2 API failed: %s - falling back to V1",
                                operationName, ex.getMessage()));
                        useFallback.set(true);
                        return v1Call.get();
                    }
                    return CompletableFuture.completedFuture(result);
                })
                .thenCompose(future -> future);
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid) {
        return withFallback(
                () -> v2Client.getPlayerProfile(uuid),
                () -> v1Client.getPlayerProfile(uuid),
                "getPlayerProfile"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid) {
        return withFallback(
                () -> v2Client.getLinkedAccounts(uuid),
                () -> v1Client.getLinkedAccounts(uuid),
                "getLinkedAccounts"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request) {
        return withFallback(
                () -> v2Client.playerLogin(request),
                () -> v1Client.playerLogin(request),
                "playerLogin"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request) {
        return withFallback(
                () -> v2Client.playerDisconnect(request),
                () -> v1Client.playerDisconnect(request),
                "playerDisconnect"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request) {
        return withFallback(
                () -> v2Client.createTicket(request),
                () -> v1Client.createTicket(request),
                "createTicket"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request) {
        return withFallback(
                () -> v2Client.createUnfinishedTicket(request),
                () -> v1Client.createUnfinishedTicket(request),
                "createUnfinishedTicket"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request) {
        return withFallback(
                () -> v2Client.createPunishment(request),
                () -> v1Client.createPunishment(request),
                "createPunishment"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request) {
        return withFallback(
                () -> v2Client.createPlayerNote(request),
                () -> v1Client.createPlayerNote(request),
                "createPlayerNote"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request) {
        return withFallback(
                () -> v2Client.createPunishmentWithResponse(request),
                () -> v1Client.createPunishmentWithResponse(request),
                "createPunishmentWithResponse"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request) {
        return withFallback(
                () -> v2Client.getPlayer(request),
                () -> v1Client.getPlayer(request),
                "getPlayer"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request) {
        return withFallback(
                () -> v2Client.getPlayer(request),
                () -> v1Client.getPlayer(request),
                "getPlayerByName"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request) {
        return withFallback(
                () -> v2Client.createPlayerNoteWithResponse(request),
                () -> v1Client.createPlayerNoteWithResponse(request),
                "createPlayerNoteWithResponse"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request) {
        return withFallback(
                () -> v2Client.sync(request),
                () -> v1Client.sync(request),
                "sync"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request) {
        return withFallback(
                () -> v2Client.acknowledgePunishment(request),
                () -> v1Client.acknowledgePunishment(request),
                "acknowledgePunishment"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request) {
        return withFallback(
                () -> v2Client.acknowledgeNotifications(request),
                () -> v1Client.acknowledgeNotifications(request),
                "acknowledgeNotifications"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentTypesResponse> getPunishmentTypes() {
        return withFallback(
                () -> v2Client.getPunishmentTypes(),
                () -> v1Client.getPunishmentTypes(),
                "getPunishmentTypes"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<StaffPermissionsResponse> getStaffPermissions() {
        return withFallback(
                () -> v2Client.getStaffPermissions(),
                () -> v1Client.getStaffPermissions(),
                "getStaffPermissions"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request) {
        return withFallback(
                () -> v2Client.lookupPlayer(request),
                () -> v1Client.lookupPlayer(request),
                "lookupPlayer"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request) {
        return withFallback(
                () -> v2Client.pardonPunishment(request),
                () -> v1Client.pardonPunishment(request),
                "pardonPunishment"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request) {
        return withFallback(
                () -> v2Client.pardonPlayer(request),
                () -> v1Client.pardonPlayer(request),
                "pardonPlayer"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request) {
        return withFallback(
                () -> v2Client.updateMigrationStatus(request),
                () -> v1Client.updateMigrationStatus(request),
                "updateMigrationStatus"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                                 String country, String region, String asn, boolean proxy, boolean hosting) {
        // Always use V2 directly - V1 does not support this endpoint
        return v2Client.submitIpInfo(minecraftUUID, ip, country, region, asn, proxy, hosting);
    }

    @NotNull
    @Override
    public CompletableFuture<OnlinePlayersResponse> getOnlinePlayers() {
        return withFallback(
                () -> v2Client.getOnlinePlayers(),
                () -> v1Client.getOnlinePlayers(),
                "getOnlinePlayers"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours) {
        return withFallback(
                () -> v2Client.getRecentPunishments(hours),
                () -> v1Client.getRecentPunishments(hours),
                "getRecentPunishments"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<ReportsResponse> getReports(String status) {
        return withFallback(
                () -> v2Client.getReports(status),
                () -> v1Client.getReports(status),
                "getReports"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<ReportsResponse> getPlayerReports(@NotNull java.util.UUID playerUuid, String status) {
        return withFallback(
                () -> v2Client.getPlayerReports(playerUuid, status),
                () -> v1Client.getPlayerReports(playerUuid, status),
                "getPlayerReports"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason) {
        return withFallback(
                () -> v2Client.dismissReport(reportId, dismissedBy, reason),
                () -> v1Client.dismissReport(reportId, dismissedBy, reason),
                "dismissReport"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId) {
        return withFallback(
                () -> v2Client.resolveReport(reportId, resolvedBy, resolution, punishmentId),
                () -> v1Client.resolveReport(reportId, resolvedBy, resolution, punishmentId),
                "resolveReport"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<TicketsResponse> getTickets(String status, String type) {
        return withFallback(
                () -> v2Client.getTickets(status, type),
                () -> v1Client.getTickets(status, type),
                "getTickets"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<DashboardStatsResponse> getDashboardStats() {
        return withFallback(
                () -> v2Client.getDashboardStats(),
                () -> v1Client.getDashboardStats(),
                "getDashboardStats"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal) {
        return withFallback(
                () -> v2Client.getPunishmentPreview(playerUuid, typeOrdinal),
                () -> v1Client.getPunishmentPreview(playerUuid, typeOrdinal),
                "getPunishmentPreview"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request) {
        return withFallback(
                () -> v2Client.addPunishmentNote(request),
                () -> v1Client.addPunishmentNote(request),
                "addPunishmentNote"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request) {
        return withFallback(
                () -> v2Client.addPunishmentEvidence(request),
                () -> v1Client.addPunishmentEvidence(request),
                "addPunishmentEvidence"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request) {
        return withFallback(
                () -> v2Client.changePunishmentDuration(request),
                () -> v1Client.changePunishmentDuration(request),
                "changePunishmentDuration"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request) {
        return withFallback(
                () -> v2Client.togglePunishmentOption(request),
                () -> v1Client.togglePunishmentOption(request),
                "togglePunishmentOption"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request) {
        return withFallback(
                () -> v2Client.claimTicket(request),
                () -> v1Client.claimTicket(request),
                "claimTicket"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<StaffListResponse> getStaffList() {
        return withFallback(
                () -> v2Client.getStaffList(),
                () -> v1Client.getStaffList(),
                "getStaffList"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName) {
        return withFallback(
                () -> v2Client.updateStaffRole(staffId, roleName),
                () -> v1Client.updateStaffRole(staffId, roleName),
                "updateStaffRole"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<RolesListResponse> getRoles() {
        return withFallback(
                () -> v2Client.getRoles(),
                () -> v1Client.getRoles(),
                "getRoles"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions) {
        return withFallback(
                () -> v2Client.updateRolePermissions(roleId, permissions),
                () -> v1Client.updateRolePermissions(roleId, permissions),
                "updateRolePermissions"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId) {
        return withFallback(
                () -> v2Client.getPunishmentDetail(punishmentId),
                () -> v1Client.getPunishmentDetail(punishmentId),
                "getPunishmentDetail"
        );
    }

    @NotNull
    @Override
    public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName) {
        return withFallback(
                () -> v2Client.createEvidenceUploadToken(punishmentId, issuerName),
                () -> v1Client.createEvidenceUploadToken(punishmentId, issuerName),
                "createEvidenceUploadToken"
        );
    }

    /**
     * Check if the client has fallen back to V1 API.
     * @return true if currently using V1 fallback
     */
    public boolean isUsingFallback() {
        return useFallback.get();
    }

    /**
     * Reset fallback state to try V2 again on next request.
     * Useful for periodic retry of V2 API.
     */
    public void resetFallback() {
        useFallback.set(false);
        logger.info("Fallback state reset - will try V2 API on next request");
    }
}
