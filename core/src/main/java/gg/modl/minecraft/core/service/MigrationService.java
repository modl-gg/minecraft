package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.StreamingJsonWriter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import gg.modl.minecraft.core.util.PluginLogger;

@RequiredArgsConstructor
public class MigrationService {
    private static final String HEADER_API_KEY = "X-API-Key", IMPORT_SOURCE = "litebans";
    private static final int HTTP_PAYLOAD_TOO_LARGE = 413, PROGRESS_LOG_INTERVAL = 100,
            BAN_TYPE_ORDINAL = 2, MUTE_TYPE_ORDINAL = 1;

    private final PluginLogger logger;
    private final ModlHttpClient httpClient;
    private final String apiUrl, apiKey;
    private final File dataFolder;
    private final DatabaseProvider databaseProvider;
    private final String defaultReason;

    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "modl-migration");
        t.setDaemon(true);
        return t;
    });

    public CompletableFuture<File> exportLiteBansData(String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            StreamingJsonWriter jsonWriter = null;
            try {
                logger.info("Starting LiteBans data export for task " + taskId);
                logger.info("Using " + (databaseProvider.isUsingLiteBansApi() ? "LiteBans API" : "direct JDBC"));
                updateMigrationProgress(taskId, "building_json", "Starting LiteBans export...", 0, null);

                File migrationFile = new File(dataFolder, "litebans-migration-" + taskId + ".json");
                jsonWriter = new StreamingJsonWriter(migrationFile, defaultReason);

                Set<String> playerUuids = getAllPlayerUuids();
                int totalPlayers = playerUuids.size();
                logger.info("Found " + totalPlayers + " unique players to migrate");
                updateMigrationProgress(taskId, "building_json", "Processing " + totalPlayers + " players...", 0, totalPlayers);

                int processed = 0;
                for (String uuid : playerUuids) {
                    try {
                        PlayerMigrationData playerData = extractPlayerData(uuid);
                        writePlayerToJson(jsonWriter, playerData);
                        processed++;
                        if (processed % PROGRESS_LOG_INTERVAL == 0 || processed == totalPlayers) {
                            updateMigrationProgress(taskId, "building_json",
                                String.format("Processed %d/%d players...", processed, totalPlayers),
                                processed, totalPlayers);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to process player " + uuid + ": " + e.getMessage());
                    }
                }

                jsonWriter.close();
                logger.info("Export completed. File: " + migrationFile.getAbsolutePath());
                return migrationFile;
            } catch (Exception e) {
                logger.severe("Error during LiteBans export: " + e.getMessage());
                logger.severe("Stack trace: " + getStackTrace(e));
                updateMigrationProgress(taskId, "failed", "Export failed: " + e.getMessage(), 0, null);
                throw new RuntimeException("Failed to export LiteBans data", e);
            } finally {
                if (jsonWriter != null) {
                    try { jsonWriter.close(); } catch (IOException e) {
                        logger.warning("Failed to close JSON writer: " + e.getMessage());
                    }
                }
            }
        }, migrationExecutor);
    }

    private Set<String> getAllPlayerUuids() throws SQLException {
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

    private PlayerMigrationData extractPlayerData(String uuid) throws SQLException {
        PlayerMigrationData data = new PlayerMigrationData();
        data.minecraftUuid = uuid;
        data.usernames = extractUsernames(uuid);
        data.ipList = extractIpAddresses(uuid);
        data.punishments = extractPunishments(uuid);

        return data;
    }

    private List<UsernameData> extractUsernames(String uuid) throws SQLException {
        List<UsernameData> usernames = new ArrayList<>();

        String query = "SELECT NAME, DATE FROM {history} WHERE UUID = ? ORDER BY DATE ASC";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("NAME");
                    Timestamp date = rs.getTimestamp("DATE");

                    if (name != null && !name.isEmpty()) {
                        UsernameData usernameData = new UsernameData();
                        usernameData.username = name;
                        if (date != null) usernameData.date = formatTimestamp(date);
                        else usernameData.date = formatMillisToIso(System.currentTimeMillis());
                        usernames.add(usernameData);
                    }
                }
            }
        }

        return usernames;
    }

    private List<IpData> extractIpAddresses(String uuid) throws SQLException {
        Map<String, IpData> ipMap = new HashMap<>();
        String query = "SELECT IP, DATE FROM {history} WHERE UUID = ? AND IP IS NOT NULL ORDER BY DATE ASC";

        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("IP");
                    if (ip == null || ip.isEmpty()) continue;

                    Timestamp date = rs.getTimestamp("DATE");
                    IpData ipData = ipMap.computeIfAbsent(ip, k -> createEmptyIpData(ip));
                    Timestamp loginDate = date != null ? date : new Timestamp(System.currentTimeMillis());
                    String loginTime = formatTimestamp(loginDate);

                    ipData.logins.add(loginTime);
                    if (ipData.firstLogin == null || loginDate.getTime() < parseIsoToMillis(ipData.firstLogin)) {
                        ipData.firstLogin = loginTime;
                    }
                }
            }
        }

        for (IpData ipData : ipMap.values()) {
            if (ipData.firstLogin != null) continue;
            ipData.firstLogin = ipData.logins.isEmpty()
                    ? formatMillisToIso(System.currentTimeMillis())
                    : ipData.logins.iterator().next();
        }
        return new ArrayList<>(ipMap.values());
    }

    private static IpData createEmptyIpData(String ip) {
        IpData data = new IpData();
        data.ipAddress = ip;
        data.proxy = false;
        data.hosting = false;
        data.logins = new LinkedHashSet<>();
        return data;
    }

    private List<PunishmentData> extractPunishments(String uuid) throws SQLException {
        List<PunishmentData> punishments = new ArrayList<>();
        extractPunishmentsFromTable(uuid, "{bans}", BAN_TYPE_ORDINAL, "BAN", punishments);
        extractPunishmentsFromTable(uuid, "{mutes}", MUTE_TYPE_ORDINAL, "MUTE", punishments);
        return punishments;
    }

    private void extractPunishmentsFromTable(String uuid, String tableToken,
                                            int typeOrdinal, String typeName, List<PunishmentData> punishments) throws SQLException {
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

    private PunishmentData buildPunishmentFromRow(ResultSet rs, int typeOrdinal, String typeName) throws SQLException {
        PunishmentData punishment = new PunishmentData();
        int litebansId = rs.getInt("ID");
        punishment.id = "litebans-" + typeName.toLowerCase() + "-" + litebansId;
        punishment.type = typeName;
        punishment.typeOrdinal = typeOrdinal;

        String reason = rs.getString("REASON");
        punishment.reason = (reason != null && !reason.isEmpty()) ? reason : defaultReason;

        long timeIssued = rs.getLong("TIME");
        if (timeIssued <= 0) timeIssued = System.currentTimeMillis();
        punishment.issued = formatMillisToIso(timeIssued);

        punishment.issuerName = rs.getString("BANNED_BY_NAME");
        if (punishment.issuerName == null || punishment.issuerName.isEmpty()) punishment.issuerName = Constants.DEFAULT_CONSOLE_NAME;

        long until = rs.getLong("UNTIL");
        punishment.duration = until > 0 ? until - timeIssued : 0L;
        punishment.started = punishment.issued;

        punishment.notes = new ArrayList<>();
        punishment.evidence = new ArrayList<>();
        punishment.attachedTicketIds = new ArrayList<>();

        Map<String, Object> reasonNote = new HashMap<>();
        reasonNote.put("text", punishment.reason);
        reasonNote.put("issuerName", punishment.issuerName);
        reasonNote.put("date", punishment.issued);
        punishment.notes.add(reasonNote);

        punishment.data = new HashMap<>();
        punishment.data.put("duration", punishment.duration);

        boolean active = rs.getBoolean("ACTIVE");
        String removedByUuid = rs.getString("REMOVED_BY_UUID");
        punishment.data.put("active", active && removedByUuid == null);
        punishment.data.put("importedFrom", IMPORT_SOURCE);
        punishment.data.put("importDate", Instant.now().toString());
        punishment.data.put("litebansId", litebansId);

        if (removedByUuid != null) {
            String removedByName = rs.getString("REMOVED_BY_NAME");
            punishment.data.put("pardonedBy", removedByName != null ? removedByName : "Unknown");
        }

        return punishment;
    }

    private void writePlayerToJson(StreamingJsonWriter writer, PlayerMigrationData playerData) throws IOException {
        StreamingJsonWriter.PlayerData jsonData = new StreamingJsonWriter.PlayerData(
            playerData.minecraftUuid,
            convertUsernames(playerData.usernames),
            Collections.emptyList(),
            convertIpList(playerData.ipList),
            convertPunishments(playerData.punishments),
            null
        );

        writer.writePlayer(jsonData);
    }

    private List<StreamingJsonWriter.UsernameEntry> convertUsernames(List<UsernameData> usernames) {
        List<StreamingJsonWriter.UsernameEntry> result = new ArrayList<>();
        for (UsernameData username : usernames) {
            result.add(new StreamingJsonWriter.UsernameEntry(username.username, username.date));
        }
        return result;
    }

    private List<StreamingJsonWriter.IpEntry> convertIpList(List<IpData> ipList) {
        List<StreamingJsonWriter.IpEntry> result = new ArrayList<>();
        for (IpData ip : ipList) {
            result.add(new StreamingJsonWriter.IpEntry(
                ip.ipAddress,
                ip.country,
                ip.region,
                ip.asn,
                ip.proxy != null ? ip.proxy : false,
                ip.hosting != null ? ip.hosting : false,
                ip.firstLogin,
                new ArrayList<>(ip.logins)
            ));
        }
        return result;
    }

    private List<StreamingJsonWriter.PunishmentEntry> convertPunishments(List<PunishmentData> punishments) {
        List<StreamingJsonWriter.PunishmentEntry> result = new ArrayList<>();
        for (PunishmentData p : punishments) {
            result.add(new StreamingJsonWriter.PunishmentEntry(
                p.id, p.type, p.typeOrdinal, p.reason, p.issued,
                p.issuerName, p.duration, p.started, p.data,
                p.notes, p.evidence, p.attachedTicketIds
            ));
        }
        return result;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private String formatMillisToIso(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private long parseIsoToMillis(String isoString) {
        return Instant.parse(isoString).toEpochMilli();
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static class PlayerMigrationData {
        String minecraftUuid;
        List<UsernameData> usernames;
        List<IpData> ipList;
        List<PunishmentData> punishments;
    }

    private static class UsernameData {
        String username, date;
    }

    private static class IpData {
        String ipAddress, country, region, asn, firstLogin;
        Boolean proxy, hosting;
        Set<String> logins;
    }

    private static class PunishmentData {
        String id, type, reason, issued, issuerName, started;
        Long duration;
        Map<String, Object> data;
        List<Map<String, Object>> notes;
        List<Object> evidence;
        List<String> attachedTicketIds;
        int typeOrdinal;
    }

    public CompletableFuture<Boolean> uploadMigrationFile(File jsonFile, String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (jsonFile == null || !jsonFile.exists()) {
                    logger.severe("Migration file does not exist");
                    return false;
                }

                double fileSizeMB = jsonFile.length() / 1024.0 / 1024.0;
                logger.info(String.format("Uploading file: %s (%.2f MB, %d bytes)",
                    jsonFile.getName(), fileSizeMB, jsonFile.length()));
                updateMigrationProgress(taskId, "uploading_json", "Uploading migration file to panel...", 0, null);

                boolean success = uploadFileMultipart(jsonFile);
                if (success) logger.info("File uploaded successfully");
                else logger.severe("File upload failed");
                return success;
            } catch (Exception e) {
                logger.severe("Error uploading file: " + e.getMessage());
                logger.severe("Stack trace: " + getStackTrace(e));
                updateMigrationProgress(taskId, "failed", "Upload failed: " + e.getMessage(), 0, null);
                return false;
            } finally {
                if (jsonFile != null && jsonFile.exists()) {
                    try {
                        Files.delete(jsonFile.toPath());
                        logger.info("Cleaned up local migration file");
                    } catch (IOException e) {
                        logger.warning("Failed to delete local file: " + e.getMessage());
                    }
                }
            }
        }, migrationExecutor);
    }

    private boolean uploadFileMultipart(File file) throws Exception {
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {

            org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder builder =
                org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder.create();
            builder.addBinaryBody("migrationFile", file,
                org.apache.hc.core5.http.ContentType.APPLICATION_JSON, file.getName());

            String uploadUrl = apiUrl + "/minecraft/migration/upload";
            org.apache.hc.client5.http.classic.methods.HttpPost httpPost =
                new org.apache.hc.client5.http.classic.methods.HttpPost(uploadUrl);
            httpPost.setHeader(HEADER_API_KEY, apiKey);
            httpPost.setEntity(builder.build());
            logger.info("Sending upload request to: " + uploadUrl);

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                String responseBody = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
                logger.info("Upload response: " + statusCode + " - " + responseBody);

                if (statusCode >= 200 && statusCode < 300) return true;
                if (statusCode == HTTP_PAYLOAD_TOO_LARGE) logger.severe("File too large: " + responseBody);
                else logger.severe("Upload failed with status " + statusCode + ": " + responseBody);
                return false;
            });
        }
    }

    private void updateMigrationProgress(String taskId, String status, String message,
                                         Integer recordsProcessed, Integer totalRecords) {
        try {
            httpClient.updateMigrationStatus(new MigrationStatusUpdateRequest(taskId, status, message, recordsProcessed, totalRecords));
            logger.info(String.format("Progress: %s - %s (%s/%s)", status, message, recordsProcessed, totalRecords));
        } catch (Exception e) {
            logger.warning("Failed to update progress: " + e.getMessage());
        }
    }

    public void shutdown() {
        migrationExecutor.shutdown();
    }
}
