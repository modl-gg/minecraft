package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.util.Constants;
import lombok.Builder;
import lombok.Value;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LiteBansMigrationRepository {
    private static final String IMPORT_SOURCE = "litebans";
    private static final int BAN_TYPE_ORDINAL = 2, MUTE_TYPE_ORDINAL = 1;

    private final DatabaseProvider databaseProvider;
    private final String defaultReason;

    LiteBansMigrationRepository(DatabaseProvider databaseProvider, String defaultReason) {
        this.databaseProvider = databaseProvider;
        this.defaultReason = defaultReason;
    }

    Set<String> getAllPlayerUuids() throws SQLException {
        Set<String> uuids = new LinkedHashSet<>();
        String query = "SELECT DISTINCT UUID FROM {history}";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("UUID");
                if (uuid != null && !uuid.isEmpty() && !uuid.equalsIgnoreCase("CONSOLE")) uuids.add(uuid);
            }
        }

        return uuids;
    }

    PlayerRecord extractPlayerData(String uuid) throws SQLException {
        return PlayerRecord.builder()
                .minecraftUuid(uuid)
                .usernames(extractUsernames(uuid))
                .ipList(extractIpAddresses(uuid))
                .punishments(extractPunishments(uuid))
                .build();
    }

    private List<UsernameRecord> extractUsernames(String uuid) throws SQLException {
        List<UsernameRecord> usernames = new ArrayList<>();
        String query = "SELECT NAME, DATE FROM {history} WHERE UUID = ? ORDER BY DATE ASC";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("NAME");
                    Timestamp date = rs.getTimestamp("DATE");
                    if (name != null && !name.isEmpty()) {
                        String isoDate = date != null ? formatTimestamp(date) : formatMillisToIso(System.currentTimeMillis());
                        usernames.add(new UsernameRecord(name, isoDate));
                    }
                }
            }
        }

        return usernames;
    }

    private List<IpRecord> extractIpAddresses(String uuid) throws SQLException {
        Map<String, IpAccumulator> ipMap = new HashMap<>();
        String query = "SELECT IP, DATE FROM {history} WHERE UUID = ? AND IP IS NOT NULL ORDER BY DATE ASC";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("IP");
                    if (ip == null || ip.isEmpty()) continue;

                    Timestamp date = rs.getTimestamp("DATE");
                    IpAccumulator acc = ipMap.computeIfAbsent(ip, IpAccumulator::new);
                    Timestamp loginDate = date != null ? date : new Timestamp(System.currentTimeMillis());
                    String loginTime = formatTimestamp(loginDate);

                    acc.logins.add(loginTime);
                    if (acc.firstLogin == null || loginDate.getTime() < parseIsoToMillis(acc.firstLogin)) {
                        acc.firstLogin = loginTime;
                    }
                }
            }
        }

        List<IpRecord> ipList = new ArrayList<>();
        for (IpAccumulator acc : ipMap.values()) {
            String firstLogin = acc.firstLogin != null
                    ? acc.firstLogin
                    : (acc.logins.isEmpty() ? formatMillisToIso(System.currentTimeMillis()) : acc.logins.iterator().next());
            ipList.add(new IpRecord(acc.ipAddress, null, null, null, false, false, firstLogin, acc.logins));
        }
        return ipList;
    }

    private List<PunishmentRecord> extractPunishments(String uuid) throws SQLException {
        List<PunishmentRecord> punishments = new ArrayList<>();
        extractPunishmentsFromTable(uuid, "{bans}", BAN_TYPE_ORDINAL, "BAN", punishments);
        extractPunishmentsFromTable(uuid, "{mutes}", MUTE_TYPE_ORDINAL, "MUTE", punishments);
        return punishments;
    }

    private void extractPunishmentsFromTable(String uuid, String tableToken, int typeOrdinal,
                                             String typeName, List<PunishmentRecord> punishments) throws SQLException {
        String query = "SELECT ID, REASON, BANNED_BY_UUID, BANNED_BY_NAME, TIME, UNTIL, " +
                      "ACTIVE, REMOVED_BY_UUID, REMOVED_BY_NAME FROM " + tableToken +
                      " WHERE UUID = ? ORDER BY TIME ASC";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    punishments.add(buildPunishmentFromRow(rs, typeOrdinal, typeName));
                }
            }
        }
    }

    private PunishmentRecord buildPunishmentFromRow(ResultSet rs, int typeOrdinal, String typeName) throws SQLException {
        int litebansId = rs.getInt("ID");
        String id = "litebans-" + typeName.toLowerCase() + "-" + litebansId;

        String reason = resolvePunishmentReason(rs.getString("REASON"));

        long timeIssued = rs.getLong("TIME");
        if (timeIssued <= 0) timeIssued = System.currentTimeMillis();
        String issued = formatMillisToIso(timeIssued);

        String issuerName = rs.getString("BANNED_BY_NAME");
        if (issuerName == null || issuerName.isEmpty()) issuerName = Constants.DEFAULT_CONSOLE_NAME;

        long until = rs.getLong("UNTIL");
        long duration = until > 0 ? until - timeIssued : 0L;

        List<Map<String, Object>> notes = new ArrayList<>();
        Map<String, Object> reasonNote = new HashMap<>();
        reasonNote.put("text", reason);
        reasonNote.put("issuerName", issuerName);
        reasonNote.put("date", issued);
        notes.add(reasonNote);

        boolean active = rs.getBoolean("ACTIVE");
        String removedByUuid = rs.getString("REMOVED_BY_UUID");

        Map<String, Object> data = new HashMap<>();
        data.put("duration", duration);
        data.put("active", active && removedByUuid == null);
        data.put("importedFrom", IMPORT_SOURCE);
        data.put("importDate", Instant.now().toString());
        data.put("litebansId", litebansId);
        if (removedByUuid != null) {
            String removedByName = rs.getString("REMOVED_BY_NAME");
            data.put("pardonedBy", removedByName != null ? removedByName : "Unknown");
        }

        return PunishmentRecord.builder()
                .id(id)
                .type(typeName)
                .typeOrdinal(typeOrdinal)
                .reason(reason)
                .issued(issued)
                .issuerName(issuerName)
                .duration(duration)
                .started(issued)
                .data(data)
                .notes(notes)
                .evidence(new ArrayList<>())
                .attachedTicketIds(new ArrayList<>())
                .build();
    }

    private String resolvePunishmentReason(String reason) {
        return (reason != null && !reason.isEmpty()) ? reason : defaultReason;
    }

    private static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static String formatMillisToIso(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static long parseIsoToMillis(String isoString) {
        return Instant.parse(isoString).toEpochMilli();
    }

    private static final class IpAccumulator {
        private final String ipAddress;
        private final Set<String> logins = new LinkedHashSet<>();
        private String firstLogin;

        private IpAccumulator(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }

    @Value
    @Builder
    static class PlayerRecord {
        String minecraftUuid;
        List<UsernameRecord> usernames;
        List<IpRecord> ipList;
        List<PunishmentRecord> punishments;
    }

    @Value
    static class UsernameRecord {
        String username, date;
    }

    @Value
    static class IpRecord {
        String ipAddress, country, region, asn;
        boolean proxy, hosting;
        String firstLogin;
        Set<String> logins;
    }

    @Value
    @Builder
    static class PunishmentRecord {
        String id, type, reason, issued, issuerName, started;
        int typeOrdinal;
        long duration;
        Map<String, Object> data;
        List<Map<String, Object>> notes;
        List<Object> evidence;
        List<String> attachedTicketIds;
    }
}
