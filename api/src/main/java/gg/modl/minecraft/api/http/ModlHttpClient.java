package gg.modl.minecraft.api.http;

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
import gg.modl.minecraft.api.http.request.PlayerNoteCreateRequest;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client interface for communicating with the modl backend API.
 *
 * <p>All methods return {@link CompletableFuture} for asynchronous execution. Implementations
 * include V1 (panel-based), V2 (centralized api.modl.gg), and a fallback wrapper that
 * auto-upgrades from V1 to V2.</p>
 */
public interface ModlHttpClient {

    /**
     * Retrieves the full profile for a player, including punishment history and metadata.
     *
     * @param uuid the player's Minecraft UUID
     * @return a future containing the player's profile data
     */
    @NotNull
    CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid);

    /**
     * Retrieves accounts linked to the specified player (e.g., alt accounts detected by IP or other means).
     *
     * @param uuid the player's Minecraft UUID
     * @return a future containing the linked accounts data
     */
    @NotNull
    CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid);

    /**
     * Notifies the backend that a player has logged into the server.
     * The response includes active punishments, pending IP lookups, and other login-time data.
     *
     * @param request the login request containing player UUID, username, IP, and server info
     * @return a future containing the login response with active punishments and pending actions
     */
    @NotNull
    CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request);

    /**
     * Notifies the backend that a player has disconnected from the server.
     *
     * @param request the disconnect request containing player UUID and session information
     * @return a future that completes when the disconnect has been recorded
     */
    @NotNull
    CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request);

    /**
     * Creates a new ticket (e.g., report, appeal, or support request).
     *
     * @param request the ticket creation request containing type, subject, and details
     * @return a future containing the created ticket's response data
     */
    @NotNull
    CompletableFuture<CreateTicketResponse> createTicket(@NotNull CreateTicketRequest request);

    /**
     * Creates a ticket in an unfinished/draft state, allowing further data to be attached before finalization.
     *
     * @param request the ticket creation request containing partial ticket data
     * @return a future containing the created ticket's response data
     */
    @NotNull
    CompletableFuture<CreateTicketResponse> createUnfinishedTicket(@NotNull CreateTicketRequest request);

    /**
     * Creates a punishment for a player. This is a legacy method that returns no response body.
     *
     * @param request the punishment creation request containing player, type, duration, and issuer info
     * @return a future that completes when the punishment has been created
     * @deprecated Use {@link #createPunishmentWithResponse(PunishmentCreateRequest)} for proper return data
     */
    @NotNull
    CompletableFuture<Void> createPunishment(@NotNull CreatePunishmentRequest request);

    /**
     * Creates a note on a player's record. This is a legacy method that returns no response body.
     *
     * @param request the note creation request containing player UUID and note content
     * @return a future that completes when the note has been created
     * @deprecated Use {@link #createPlayerNoteWithResponse(PlayerNoteCreateRequest)} for proper return data
     */
    @NotNull
    CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request);

    /**
     * Creates a punishment for a player and returns the created punishment details.
     *
     * @param request the punishment creation request containing player, type, duration, and issuer info
     * @return a future containing the created punishment's response data
     */
    @NotNull
    CompletableFuture<PunishmentCreateResponse> createPunishmentWithResponse(@NotNull PunishmentCreateRequest request);

    /**
     * Retrieves a player's data by UUID.
     *
     * @param request the request containing the player's UUID
     * @return a future containing the player's data including punishments and notes
     */
    @NotNull
    CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request);

    /**
     * Retrieves a player's data by username.
     *
     * @param request the request containing the player's username
     * @return a future containing the player's name resolution data
     */
    @NotNull
    CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request);

    /**
     * Creates a note on a player's record and returns the created note details.
     *
     * @param request the note creation request containing player UUID and note content
     * @return a future containing the created note's response data
     */
    @NotNull
    CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull PlayerNoteCreateRequest request);

    /**
     * Performs a sync poll with the backend, retrieving pending punishments, modifications,
     * stat wipes, and notifications since the last sync.
     *
     * @param request the sync request containing acknowledgment data and current state
     * @return a future containing the sync response with pending actions
     */
    @NotNull
    CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request);

    /**
     * Acknowledges that a punishment has been executed by the plugin (e.g., player was kicked or muted).
     *
     * @param request the acknowledgment request containing the punishment ID
     * @return a future that completes when the acknowledgment has been recorded
     */
    @NotNull
    CompletableFuture<Void> acknowledgePunishment(@NotNull PunishmentAcknowledgeRequest request);

    /**
     * Acknowledges that notifications have been delivered to the staff member.
     *
     * @param request the acknowledgment request containing the notification IDs
     * @return a future that completes when the acknowledgment has been recorded
     */
    @NotNull
    CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request);

    /**
     * Retrieves the configured punishment types for this server, including names, ordinals, durations, and categories.
     *
     * @return a future containing the list of punishment types
     */
    @NotNull
    CompletableFuture<PunishmentTypesResponse> getPunishmentTypes();

    /**
     * Retrieves the permissions for the currently authenticated staff member.
     *
     * @return a future containing the staff member's permission set
     */
    @NotNull
    CompletableFuture<StaffPermissionsResponse> getStaffPermissions();

    /**
     * Looks up a player by username or UUID, returning basic identification data.
     *
     * @param request the lookup request containing the search query (username or UUID)
     * @return a future containing the lookup result
     */
    @NotNull
    CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request);

    /**
     * Pardons (revokes) a specific punishment by its ID.
     *
     * @param request the pardon request containing the punishment ID, pardoner, and reason
     * @return a future containing the pardon result
     */
    @NotNull
    CompletableFuture<PardonResponse> pardonPunishment(@NotNull PardonPunishmentRequest request);

    /**
     * Pardons all active punishments of a given type for a player.
     *
     * @param request the pardon request containing the player UUID, punishment type, pardoner, and reason
     * @return a future containing the pardon result
     */
    @NotNull
    CompletableFuture<PardonResponse> pardonPlayer(@NotNull PardonPlayerRequest request);

    /**
     * Updates the status of a LiteBans migration operation.
     *
     * @param request the migration status update containing progress and completion information
     * @return a future that completes when the status has been updated
     */
    @NotNull
    CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request);

    /**
     * Submits IP geolocation information for a player, as resolved by the plugin via ip-api.com.
     *
     * @param minecraftUUID the player's Minecraft UUID
     * @param ip the player's IP address
     * @param country the resolved country name, or null if unavailable
     * @param region the resolved region/state, or null if unavailable
     * @param asn the resolved ASN (autonomous system number), or null if unavailable
     * @param proxy whether the IP was detected as a proxy
     * @param hosting whether the IP was detected as a hosting/datacenter IP
     * @return a future that completes when the IP info has been submitted
     */
    @NotNull
    CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                          String country, String region, String asn, boolean proxy, boolean hosting);

    /**
     * Retrieves the list of currently online players as tracked by the backend.
     *
     * @return a future containing the online players data
     */
    @NotNull
    CompletableFuture<OnlinePlayersResponse> getOnlinePlayers();

    /**
     * Retrieves punishments issued within the specified number of hours.
     *
     * @param hours the lookback window in hours
     * @return a future containing the recent punishments
     */
    @NotNull
    CompletableFuture<RecentPunishmentsResponse> getRecentPunishments(int hours);

    /**
     * Retrieves reports filtered by status.
     *
     * @param status the report status filter (e.g., "open", "resolved"), or null for all
     * @return a future containing the matching reports
     */
    @NotNull
    CompletableFuture<ReportsResponse> getReports(String status);

    /**
     * Retrieves reports filed against a specific player, optionally filtered by status.
     *
     * @param playerUuid the UUID of the reported player
     * @param status the report status filter, or null for all
     * @return a future containing the matching reports for the player
     */
    @NotNull
    CompletableFuture<ReportsResponse> getPlayerReports(@NotNull UUID playerUuid, String status);

    /**
     * Dismisses a report without taking action.
     *
     * @param reportId the ID of the report to dismiss
     * @param dismissedBy the name of the staff member dismissing the report
     * @param reason the reason for dismissal
     * @return a future that completes when the report has been dismissed
     */
    @NotNull
    CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason);

    /**
     * Resolves a report, optionally linking it to a punishment that was issued.
     *
     * @param reportId the ID of the report to resolve
     * @param resolvedBy the name of the staff member resolving the report
     * @param resolution a description of the resolution
     * @param punishmentId the ID of the associated punishment, or null if none
     * @return a future that completes when the report has been resolved
     */
    @NotNull
    CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId);

    /**
     * Retrieves tickets filtered by status and/or type.
     *
     * @param status the ticket status filter, or null for all
     * @param type the ticket type filter, or null for all
     * @return a future containing the matching tickets
     */
    @NotNull
    CompletableFuture<TicketsResponse> getTickets(String status, String type);

    /**
     * Retrieves aggregated dashboard statistics including player counts, punishment counts, and ticket metrics.
     *
     * @return a future containing the dashboard statistics
     */
    @NotNull
    CompletableFuture<DashboardStatsResponse> getDashboardStats();

    /**
     * Previews what punishment would be applied to a player for a given type,
     * taking into account offense history and escalation thresholds.
     *
     * @param playerUuid the UUID of the player to preview punishment for
     * @param typeOrdinal the punishment type ordinal (0-17)
     * @return a future containing the punishment preview with suggested duration and severity
     */
    @NotNull
    CompletableFuture<PunishmentPreviewResponse> getPunishmentPreview(@NotNull UUID playerUuid, int typeOrdinal);

    /**
     * Adds a staff note to an existing punishment.
     *
     * @param request the request containing the punishment ID and note content
     * @return a future that completes when the note has been added
     */
    @NotNull
    CompletableFuture<Void> addPunishmentNote(@NotNull AddPunishmentNoteRequest request);

    /**
     * Adds evidence (e.g., a screenshot or link) to an existing punishment.
     *
     * @param request the request containing the punishment ID and evidence data
     * @return a future that completes when the evidence has been added
     */
    @NotNull
    CompletableFuture<Void> addPunishmentEvidence(@NotNull AddPunishmentEvidenceRequest request);

    /**
     * Changes the duration of an existing punishment.
     *
     * @param request the request containing the punishment ID and new duration
     * @return a future that completes when the duration has been updated
     */
    @NotNull
    CompletableFuture<Void> changePunishmentDuration(@NotNull ChangePunishmentDurationRequest request);

    /**
     * Toggles a boolean option on a punishment (e.g., shadow mute, silent execution).
     *
     * @param request the request containing the punishment ID and option to toggle
     * @return a future that completes when the option has been toggled
     */
    @NotNull
    CompletableFuture<Void> togglePunishmentOption(@NotNull TogglePunishmentOptionRequest request);

    /**
     * Claims a ticket, assigning the requesting staff member to it.
     *
     * @param request the claim request containing the ticket ID and claimer information
     * @return a future containing the claim result
     */
    @NotNull
    CompletableFuture<ClaimTicketResponse> claimTicket(@NotNull ClaimTicketRequest request);

    /**
     * Retrieves the full list of staff members for this server.
     *
     * @return a future containing the staff list with roles and status
     */
    @NotNull
    CompletableFuture<StaffListResponse> getStaffList();

    /**
     * Reports that a staff member has disconnected, including their session duration for tracking.
     *
     * @param minecraftUuid the disconnecting staff member's Minecraft UUID
     * @param sessionDurationMs the duration of the staff session in milliseconds
     * @return a future that completes when the disconnect has been recorded
     */
    @NotNull
    CompletableFuture<Void> reportStaffDisconnect(@NotNull String minecraftUuid, long sessionDurationMs);

    /**
     * Updates the role assigned to a staff member.
     *
     * @param staffId the ID of the staff member to update
     * @param roleName the name of the new role to assign
     * @return a future that completes when the role has been updated
     */
    @NotNull
    CompletableFuture<Void> updateStaffRole(@NotNull String staffId, @NotNull String roleName);

    /**
     * Retrieves all staff roles configured for this server.
     *
     * @return a future containing the list of roles with their permissions
     */
    @NotNull
    CompletableFuture<RolesListResponse> getRoles();

    /**
     * Updates the permission set for a staff role.
     *
     * @param roleId the ID of the role to update
     * @param permissions the new list of permission strings to assign to the role
     * @return a future that completes when the permissions have been updated
     */
    @NotNull
    CompletableFuture<Void> updateRolePermissions(@NotNull String roleId, @NotNull List<String> permissions);

    /**
     * Retrieves detailed information about a specific punishment, including modifications and evidence.
     *
     * @param punishmentId the ID of the punishment to look up
     * @return a future containing the full punishment details
     */
    @NotNull
    CompletableFuture<PunishmentDetailResponse> getPunishmentDetail(@NotNull String punishmentId);

    /**
     * Creates a pre-signed upload token for attaching evidence files to a punishment.
     *
     * @param punishmentId the ID of the punishment to attach evidence to
     * @param issuerName the name of the staff member uploading the evidence
     * @return a future containing the upload token and URL
     */
    @NotNull
    CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(@NotNull String punishmentId, @NotNull String issuerName);

    /**
     * Updates the server that a player is currently connected to (used on proxy platforms for tracking).
     *
     * @param minecraftUuid the player's Minecraft UUID
     * @param serverName the name of the server the player is now on
     * @return a future that completes when the server has been updated
     */
    @NotNull
    CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName);

    /**
     * Modifies the tickets linked to a punishment (e.g., linking or unlinking tickets).
     *
     * @param request the request containing the punishment ID and ticket modification details
     * @return a future that completes when the ticket links have been modified
     */
    @NotNull
    CompletableFuture<Void> modifyPunishmentTickets(@NotNull ModifyPunishmentTicketsRequest request);

    /**
     * Retrieves multiple tickets by their IDs in a single batch request.
     *
     * @param ticketIds the list of ticket IDs to fetch
     * @return a future containing the matching tickets
     */
    @NotNull
    CompletableFuture<TicketsResponse> getTicketsByIds(@NotNull List<String> ticketIds);

    /**
     * Acknowledges that a stat wipe has been executed on a game server.
     *
     * @param request the acknowledgment request containing the stat wipe ID and result
     * @return a future that completes when the acknowledgment has been recorded
     */
    @NotNull
    CompletableFuture<Void> acknowledgeStatWipe(@NotNull StatWipeAcknowledgeRequest request);

    /**
     * Generates a two-factor authentication token for a staff member logging in.
     *
     * @param minecraftUuid the staff member's Minecraft UUID
     * @param ip the IP address from which the staff member is connecting
     * @return a future containing the generated 2FA token data
     */
    @NotNull
    CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(@NotNull String minecraftUuid, @NotNull String ip);

    /**
     * Submits a batch of player chat messages for logging and AI moderation analysis.
     *
     * @param request the batch request containing the chat log entries
     * @return a future that completes when the logs have been submitted
     */
    @NotNull
    CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest request);

    /**
     * Submits a batch of player command executions for logging.
     *
     * @param request the batch request containing the command log entries
     * @return a future that completes when the logs have been submitted
     */
    @NotNull
    CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest request);

    /**
     * Retrieves recent chat messages for a specific player.
     *
     * @param playerUuid the player's Minecraft UUID as a string
     * @param limit the maximum number of chat log entries to return
     * @return a future containing the player's chat log entries
     */
    @NotNull
    CompletableFuture<ChatLogsResponse> getChatLogs(@NotNull String playerUuid, int limit);

    /**
     * Retrieves recent command executions for a specific player.
     *
     * @param playerUuid the player's Minecraft UUID as a string
     * @param limit the maximum number of command log entries to return
     * @return a future containing the player's command log entries
     */
    @NotNull
    CompletableFuture<CommandLogsResponse> getCommandLogs(@NotNull String playerUuid, int limit);
}