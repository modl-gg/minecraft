package gg.modl.minecraft.core.impl.http.proto;

import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.IPAddress;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.request.PlayerNoteCreateRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.PardonResponse;
import gg.modl.minecraft.api.http.response.PlayerGetResponse;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.api.http.response.PlayerNoteCreateResponse;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PaginatedNotesResponse;
import gg.modl.minecraft.api.http.response.PaginatedPunishmentsResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.proto.modl.v1.IPEntry;
import gg.modl.proto.modl.v1.NoteEntry;
import gg.modl.proto.modl.v1.PendingStatWipe;
import gg.modl.proto.modl.v1.PunishmentEvidence;
import gg.modl.proto.modl.v1.PunishmentListEntry;
import gg.modl.proto.modl.v1.PunishmentModification;
import gg.modl.proto.modl.v1.PunishmentNote;
import gg.modl.proto.modl.v1.PunishmentResponse;
import gg.modl.proto.modl.v1.ReportEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps player-domain DTOs to/from their proto V3 counterparts. The inverse of the backend
 * {@code MinecraftPlayerProtoMapper}: domain&rarr;proto for requests, proto&rarr;domain for responses.
 */
public final class PlayerProtoMapper {

    private PlayerProtoMapper() {
    }

    // ---- Requests (domain -> proto) ----

    public static gg.modl.proto.modl.v1.PlayerLoginRequest toProto(PlayerLoginRequest request) {
        gg.modl.proto.modl.v1.PlayerLoginRequest.Builder builder = gg.modl.proto.modl.v1.PlayerLoginRequest.newBuilder()
            .setMinecraftUuid(request.getMinecraftUuid())
            .setUsername(request.getUsername());

        if (request.getIpAddress() != null) builder.setIpAddress(request.getIpAddress());
        if (request.getSkinHash() != null) builder.setSkinHash(request.getSkinHash());
        if (request.getServerName() != null) builder.setServerName(request.getServerName());
        if (request.getServerInstanceId() != null) builder.setServerInstanceId(request.getServerInstanceId());
        if (request.getIpInfo() != null) builder.setIpInfo(ProtoConversions.mapToStruct(request.getIpInfo()));
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.PlayerDisconnectRequest toProto(PlayerDisconnectRequest request) {
        gg.modl.proto.modl.v1.PlayerDisconnectRequest.Builder builder =
            gg.modl.proto.modl.v1.PlayerDisconnectRequest.newBuilder()
                .setMinecraftUuid(request.getMinecraftUuid())
                .setSessionDurationMs(request.getSessionDurationMs());
        if (request.getServerInstanceId() != null) builder.setServerInstanceId(request.getServerInstanceId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.UpdatePlayerServerRequest toUpdateServerRequest(
        String minecraftUuid, String serverName, String serverInstanceId) {
        gg.modl.proto.modl.v1.UpdatePlayerServerRequest.Builder builder =
            gg.modl.proto.modl.v1.UpdatePlayerServerRequest.newBuilder()
                .setMinecraftUuid(minecraftUuid)
                .setServerName(serverName);
        if (serverInstanceId != null) builder.setServerInstanceId(serverInstanceId);
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.SubmitPlayerIpInfoRequest toSubmitIpInfoRequest(
        String minecraftUuid, String ip, String country, String region, String asn, boolean proxy, boolean hosting) {
        gg.modl.proto.modl.v1.SubmitPlayerIpInfoRequest.Builder builder =
            gg.modl.proto.modl.v1.SubmitPlayerIpInfoRequest.newBuilder()
                .setMinecraftUuid(minecraftUuid)
                .setIp(ip)
                .setProxy(proxy)
                .setHosting(hosting);
        if (country != null) builder.setCountry(country);
        if (region != null) builder.setRegion(region);
        if (asn != null) builder.setAsn(asn);
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.CreatePlayerNoteRequest toProto(CreatePlayerNoteRequest request) {
        gg.modl.proto.modl.v1.CreatePlayerNoteRequest.Builder builder =
            gg.modl.proto.modl.v1.CreatePlayerNoteRequest.newBuilder()
                .setText(request.getText());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.CreatePlayerNoteRequest toProto(PlayerNoteCreateRequest request) {
        gg.modl.proto.modl.v1.CreatePlayerNoteRequest.Builder builder =
            gg.modl.proto.modl.v1.CreatePlayerNoteRequest.newBuilder()
                .setText(request.getText());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.PardonPlayerRequest toProto(PardonPlayerRequest request) {
        gg.modl.proto.modl.v1.PardonPlayerRequest.Builder builder =
            gg.modl.proto.modl.v1.PardonPlayerRequest.newBuilder()
                .setPlayerName(request.getPlayerName());
        if (request.getIssuerName() != null) builder.setIssuerName(request.getIssuerName());
        if (request.getIssuerId() != null) builder.setIssuerId(request.getIssuerId());
        if (request.getPunishmentType() != null) builder.setPunishmentType(request.getPunishmentType());
        if (request.getReason() != null) builder.setReason(request.getReason());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.PlayerLookupRequest toProto(PlayerLookupRequest request) {
        return gg.modl.proto.modl.v1.PlayerLookupRequest.newBuilder()
            .setQuery(request.getQuery())
            .setQueryMojang(request.isQueryMojang())
            .build();
    }

    public static gg.modl.proto.modl.v1.PunishmentAcknowledgeRequest toProto(PunishmentAcknowledgeRequest request) {
        gg.modl.proto.modl.v1.PunishmentAcknowledgeRequest.Builder builder =
            gg.modl.proto.modl.v1.PunishmentAcknowledgeRequest.newBuilder()
                .setPunishmentId(request.getPunishmentId())
                .setPlayerUuid(request.getPlayerUuid())
                .setExecutedAt(request.getExecutedAt())
                .setSuccess(request.isSuccess());
        if (request.getErrorMessage() != null) builder.setErrorMessage(request.getErrorMessage());
        return builder.build();
    }

    public static gg.modl.proto.modl.v1.AcknowledgeNotificationsRequest toProto(NotificationAcknowledgeRequest request) {
        return gg.modl.proto.modl.v1.AcknowledgeNotificationsRequest.newBuilder()
            .setPlayerUuid(request.getPlayerUuid())
            .setAcknowledgedAt(request.getAcknowledgedAt())
            .addAllNotificationIds(request.getNotificationIds())
            .build();
    }

    // ---- Responses (proto -> domain) ----

    public static PlayerLoginResponse toLoginResponse(gg.modl.proto.modl.v1.PlayerLoginResponse proto) {
        List<SimplePunishment> punishments = new ArrayList<>();
        proto.getActivePunishmentsList().forEach(p -> punishments.add(toSimplePunishment(p)));

        List<Map<String, Object>> notifications = new ArrayList<>();
        proto.getPendingNotificationsList().forEach(s -> notifications.add(ProtoConversions.structToMap(s)));

        List<SyncResponse.PendingStatWipe> statWipes = new ArrayList<>();
        proto.getPendingStatWipesList().forEach(w -> statWipes.add(toPendingStatWipe(w)));

        return new PlayerLoginResponse(
            punishments,
            notifications,
            new ArrayList<>(proto.getPendingIpLookupsList()),
            statWipes,
            proto.getStatus());
    }

    public static SimplePunishment toSimplePunishment(gg.modl.proto.modl.v1.SimplePunishment proto) {
        return new SimplePunishment(
            proto.getType(),
            proto.hasCategory() ? proto.getCategory() : null,
            proto.hasExpiration() ? proto.getExpiration() : null,
            proto.getDescription(),
            proto.getId(),
            proto.hasIssuerName() ? proto.getIssuerName() : null,
            proto.hasIssuedAt() ? proto.getIssuedAt() : null,
            proto.hasPlayerDescription() ? proto.getPlayerDescription() : null,
            proto.getStarted(),
            proto.getOrdinal());
    }

    private static SyncResponse.PendingStatWipe toPendingStatWipe(PendingStatWipe proto) {
        return new SyncResponse.PendingStatWipe(
            proto.getMinecraftUuid(),
            proto.getUsername(),
            proto.getPunishmentId());
    }

    public static OnlinePlayersResponse toOnlinePlayersResponse(gg.modl.proto.modl.v1.OnlinePlayersResponse proto) {
        List<OnlinePlayersResponse.OnlinePlayer> players = new ArrayList<>();
        proto.getPlayersList().forEach(p -> players.add(new OnlinePlayersResponse.OnlinePlayer(
            p.getUuid(),
            p.getUsername(),
            ProtoConversions.parseDate(p.getJoinedAt()),
            p.getTotalPlaytimeMs())));
        return new OnlinePlayersResponse(players, proto.getStatus());
    }

    public static PlayerProfileResponse toPlayerProfileResponse(gg.modl.proto.modl.v1.PlayerProfileResponse proto) {
        PlayerProfileResponse response = new PlayerProfileResponse(toAccount(proto.getProfile()));
        response.setStatus(proto.getStatus());
        if (proto.hasPunishmentCount()) response.setPunishmentCount(proto.getPunishmentCount());
        if (proto.hasNoteCount()) response.setNoteCount(proto.getNoteCount());
        return response;
    }

    public static PlayerGetResponse toPlayerGetResponse(gg.modl.proto.modl.v1.PlayerGetResponse proto) {
        return new PlayerGetResponse(proto.getMessage(), toAccount(proto.getPlayer()), proto.getStatus());
    }

    public static PlayerNameResponse toPlayerNameResponse(gg.modl.proto.modl.v1.PlayerNameResponse proto) {
        return new PlayerNameResponse(proto.getMessage(), toAccount(proto.getPlayer()), proto.getStatus());
    }

    public static PlayerNoteCreateResponse toPlayerNoteCreateResponse(gg.modl.proto.modl.v1.PlayerNoteCreateResponse proto) {
        return new PlayerNoteCreateResponse(proto.getMessage(), proto.getStatus());
    }

    public static PlayerLookupResponse toPlayerLookupResponse(gg.modl.proto.modl.v1.PlayerLookupResponse proto) {
        return new PlayerLookupResponse(proto.getMessage(), toLookupData(proto.getData()), proto.getStatus());
    }

    public static LinkedAccountsResponse toLinkedAccountsResponse(gg.modl.proto.modl.v1.LinkedAccountsResponse proto) {
        List<Account> accounts = new ArrayList<>();
        proto.getLinkedAccountsList().forEach(a -> accounts.add(toAccount(a)));
        LinkedAccountsResponse response = new LinkedAccountsResponse(accounts);
        response.setStatus(proto.getStatus());
        response.setPage(proto.getPage());
        response.setHasMore(proto.getHasMore());
        if (proto.hasTotalCount()) response.setTotalCount(proto.getTotalCount());
        return response;
    }

    public static PaginatedPunishmentsResponse toPaginatedPunishmentsResponse(
        gg.modl.proto.modl.v1.PaginatedPunishmentsResponse proto) {
        PaginatedPunishmentsResponse response = new PaginatedPunishmentsResponse();
        List<Punishment> punishments = new ArrayList<>();
        proto.getPunishmentsList().forEach(p -> punishments.add(toPunishment(p)));
        response.setPunishments(punishments);
        response.setTotalCount(proto.getTotalCount());
        response.setPage(proto.getPage());
        response.setHasMore(proto.getHasMore());
        response.setStatus(proto.getStatus());
        return response;
    }

    public static PaginatedNotesResponse toPaginatedNotesResponse(gg.modl.proto.modl.v1.PaginatedNotesResponse proto) {
        PaginatedNotesResponse response = new PaginatedNotesResponse();
        List<Note> notes = new ArrayList<>();
        proto.getNotesList().forEach(n -> notes.add(toNote(n)));
        response.setNotes(notes);
        response.setTotalCount(proto.getTotalCount());
        response.setPage(proto.getPage());
        response.setHasMore(proto.getHasMore());
        response.setStatus(proto.getStatus());
        return response;
    }

    public static ReportsResponse toReportsResponse(gg.modl.proto.modl.v1.ReportsResponse proto) {
        List<ReportsResponse.Report> reports = new ArrayList<>();
        proto.getReportsList().forEach(r -> reports.add(toReport(r)));
        return new ReportsResponse(reports, proto.getStatus());
    }

    public static PardonResponse toPardonResponse(gg.modl.proto.modl.v1.PardonResponse proto) {
        return new PardonResponse(proto.getMessage(), proto.getStatus(), proto.getPardonedCount(), proto.getSuccess());
    }

    // ---- Account graph ----

    private static Account toAccount(gg.modl.proto.modl.v1.Account proto) {
        List<Account.Username> usernames = new ArrayList<>();
        proto.getUsernamesList().forEach(u -> usernames.add(
            new Account.Username(u.getUsername(), ProtoConversions.parseDate(u.getDate()))));

        List<Note> notes = new ArrayList<>();
        proto.getNotesList().forEach(n -> notes.add(toNote(n)));

        List<IPAddress> ipList = new ArrayList<>();
        proto.getIpAddressesList().forEach(ip -> ipList.add(toIpAddress(ip)));

        List<Punishment> punishments = new ArrayList<>();
        proto.getPunishmentsList().forEach(p -> punishments.add(toPunishment(p)));

        List<Map<String, Object>> notifications = new ArrayList<>();
        proto.getPendingNotificationsList().forEach(s -> notifications.add(ProtoConversions.structToMap(s)));

        Map<String, Object> data = proto.hasData() ? ProtoConversions.structToMap(proto.getData()) : null;

        return new Account(
            proto.getId(),
            parseUuid(proto.getMinecraftUuid()),
            usernames,
            notes,
            ipList,
            punishments,
            notifications,
            data);
    }

    private static IPAddress toIpAddress(IPEntry proto) {
        List<Date> logins = new ArrayList<>();
        proto.getLoginsList().forEach(login -> {
            Date parsed = ProtoConversions.parseDate(login);
            if (parsed != null) logins.add(parsed);
        });
        return new IPAddress(
            proto.hasIpAddress() ? proto.getIpAddress() : null,
            proto.hasCountry() ? proto.getCountry() : null,
            proto.hasRegion() ? proto.getRegion() : null,
            proto.hasAsn() ? proto.getAsn() : null,
            ProtoConversions.parseDate(proto.getFirstLogin()),
            logins,
            proto.getProxy(),
            proto.getHosting());
    }

    private static Note toNote(NoteEntry proto) {
        return new Note(
            proto.getText(),
            ProtoConversions.parseDate(proto.getDate()),
            ProtoConversions.emptyToNull(proto.getIssuerName()),
            ProtoConversions.emptyToNull(proto.getIssuerId()));
    }

    /**
     * Reconstructs the legacy {@link Punishment} shape from the proto {@code PunishmentResponse}.
     * The domain {@code Punishment} reads {@code active}/{@code status}/{@code expires}/{@code reason}
     * from its {@code dataMap}, so the flat proto fields are folded back into that map to preserve
     * the behaviour the V2 JSON client produced.
     */
    private static Punishment toPunishment(PunishmentResponse proto) {
        Punishment punishment = new Punishment();
        punishment.setId(proto.getId());
        punishment.setIssuerName(proto.getIssuerName());
        punishment.setIssued(ProtoConversions.dateFromMillis(proto.getIssued()));
        if (proto.hasStarted()) punishment.setStarted(ProtoConversions.dateFromMillis(proto.getStarted()));
        punishment.setTypeOrdinal(proto.getTypeOrdinal());
        punishment.setAttachedTicketIds(new ArrayList<>(proto.getAttachedTicketIdsList()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active", proto.getActive());
        data.put("isAppealable", proto.getIsAppealable());
        if (proto.hasReason()) data.put("reason", proto.getReason());
        if (proto.hasSeverity()) data.put("severity", proto.getSeverity());
        if (proto.hasStatus()) data.put("status", proto.getStatus());
        if (proto.hasExpires()) data.put("expires", proto.getExpires());
        if (proto.hasAltBlocking()) data.put("altBlocking", proto.getAltBlocking());
        if (proto.hasStatWiping()) data.put("statWiping", proto.getStatWiping());
        if (proto.hasEffectiveCategory()) data.put("effectiveCategory", proto.getEffectiveCategory());
        if (proto.hasPlayerUuid()) data.put("playerUuid", proto.getPlayerUuid());
        if (proto.hasPlayerUsername()) data.put("playerUsername", proto.getPlayerUsername());
        if (!proto.getType().isEmpty()) data.put("typeName", proto.getType());
        punishment.setDataMap(data);

        return punishment;
    }

    /**
     * Maps the paginated {@code PunishmentListEntry} (which carries explicit modifications/notes/evidence
     * lists and a {@code data} struct) into the legacy {@link Punishment} domain shape.
     */
    private static Punishment toPunishment(PunishmentListEntry proto) {
        Punishment punishment = new Punishment();
        punishment.setId(proto.getId());
        punishment.setIssuerName(proto.getIssuerName());
        punishment.setIssued(ProtoConversions.dateFromMillis(proto.getIssued()));
        if (proto.hasStarted()) punishment.setStarted(ProtoConversions.dateFromMillis(proto.getStarted()));
        if (proto.hasTypeOrdinal()) punishment.setTypeOrdinal(proto.getTypeOrdinal());

        List<Modification> modifications = new ArrayList<>();
        proto.getModificationsList().forEach(m -> modifications.add(toModification(m)));
        punishment.setModifications(modifications);

        List<Note> notes = new ArrayList<>();
        proto.getNotesList().forEach(n -> notes.add(toNote(n)));
        punishment.setNotes(notes);

        List<Evidence> evidence = new ArrayList<>();
        proto.getEvidenceList().forEach(e -> evidence.add(toEvidence(e)));
        punishment.setEvidence(evidence);

        punishment.setAttachedTicketIds(new ArrayList<>(proto.getAttachedTicketIdsList()));

        Map<String, Object> data = proto.hasData()
            ? new LinkedHashMap<>(ProtoConversions.structToMap(proto.getData()))
            : new LinkedHashMap<>();
        if (!proto.getType().isEmpty()) data.putIfAbsent("typeName", proto.getType());
        punishment.setDataMap(data);

        return punishment;
    }

    public static Modification toModification(PunishmentModification proto) {
        Modification.Type type = parseModificationType(proto.getType());
        return new Modification(
            type,
            proto.hasIssuerName() ? proto.getIssuerName() : null,
            ProtoConversions.dateFromMillis(proto.getDate()),
            proto.hasEffectiveDuration() ? proto.getEffectiveDuration() : null);
    }

    private static Note toNote(PunishmentNote proto) {
        return new Note(
            proto.getText(),
            ProtoConversions.dateFromMillis(proto.getDate()),
            proto.hasIssuerName() ? proto.getIssuerName() : null,
            proto.hasIssuerId() ? proto.getIssuerId() : null);
    }

    public static Evidence toEvidence(PunishmentEvidence proto) {
        return new Evidence(
            proto.hasText() ? proto.getText() : null,
            proto.hasUrl() ? proto.getUrl() : null,
            proto.getType(),
            proto.hasUploadedBy() ? proto.getUploadedBy() : null,
            ProtoConversions.dateFromMillis(proto.getUploadedAt()),
            proto.hasFileName() ? proto.getFileName() : null,
            proto.hasFileType() ? proto.getFileType() : null,
            proto.hasFileSize() ? proto.getFileSize() : null);
    }

    private static Modification.Type parseModificationType(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Modification.Type.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ReportsResponse.Report toReport(ReportEntry proto) {
        List<Object> chatMessages = new ArrayList<>();
        proto.getChatMessagesList().forEach(s -> chatMessages.add(ProtoConversions.structToMap(s)));
        return new ReportsResponse.Report(
            proto.getId(),
            proto.getType(),
            proto.getCategory(),
            proto.getReporterName(),
            proto.getReporterUuid(),
            proto.getReportedPlayerUuid(),
            proto.getReportedPlayerName(),
            proto.getSubject(),
            proto.getContent(),
            proto.getStatus(),
            proto.getPriority(),
            ProtoConversions.dateFromMillis(proto.getCreatedAt()),
            new ArrayList<>(proto.getAssignedToList()),
            chatMessages);
    }

    private static PlayerLookupResponse.PlayerData toLookupData(gg.modl.proto.modl.v1.PlayerLookupResponse.PlayerLookupData proto) {
        List<PlayerLookupResponse.RecentPunishment> recentPunishments = new ArrayList<>();
        proto.getRecentPunishmentsList().forEach(p -> recentPunishments.add(new PlayerLookupResponse.RecentPunishment(
            p.getId(), p.getType(), p.getIssuer(), p.getIssuedAt(), p.getExpiresAt(), p.getIsActive())));

        List<PlayerLookupResponse.RecentTicket> recentTickets = new ArrayList<>();
        proto.getRecentTicketsList().forEach(t -> recentTickets.add(new PlayerLookupResponse.RecentTicket(
            t.getId(), t.getTitle(), t.getCategory(), t.getStatus(), t.getCreatedAt(), t.getLastUpdated())));

        gg.modl.proto.modl.v1.PlayerLookupResponse.PlayerLookupPunishmentStats stats = proto.getPunishmentStats();
        PlayerLookupResponse.PunishmentStats punishmentStats = new PlayerLookupResponse.PunishmentStats(
            stats.getStatus(),
            stats.getTotalPunishments(),
            stats.getActivePunishments(),
            stats.getBans(),
            stats.getMutes(),
            stats.getKicks(),
            stats.getWarnings(),
            stats.getPoints());

        return new PlayerLookupResponse.PlayerData(
            proto.getMinecraftUuid(),
            proto.getCurrentUsername(),
            proto.getFirstSeen(),
            proto.getLastSeen(),
            proto.getCurrentServer(),
            proto.getIpAddress(),
            proto.getCountry(),
            proto.getProfileUrl(),
            proto.getPunishmentsUrl(),
            proto.getTicketsUrl(),
            new ArrayList<>(proto.getPreviousUsernamesList()),
            punishmentStats,
            recentPunishments,
            recentTickets,
            proto.getIsOnline());
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
