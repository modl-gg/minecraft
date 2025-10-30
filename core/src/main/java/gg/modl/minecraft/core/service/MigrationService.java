package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import gg.modl.minecraft.core.util.StreamingJsonWriter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class MigrationService {
    private final Logger logger;
    private final ModlHttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final File dataFolder;
    private final DatabaseProvider databaseProvider;

    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "modl-migration");
        t.setDaemon(true);
        return t;
    });

    /**
     * Export LiteBans data to JSON file
     */
    public CompletableFuture<File> exportLiteBansData(String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            StreamingJsonWriter jsonWriter = null;
            
            try {
                logger.info("[Migration] Starting LiteBans data export for task " + taskId);
                logger.info("[Migration] Using " + (databaseProvider.isUsingLiteBansApi() ? "LiteBans API" : "direct JDBC"));
                updateMigrationProgress(taskId, "building_json", "Starting LiteBans export...", 0, null);

                // Create output file
                File migrationFile = new File(dataFolder, "litebans-migration-" + taskId + ".json");
                jsonWriter = new StreamingJsonWriter(migrationFile);

                // Get all unique player UUIDs
                Set<String> playerUuids = getAllPlayerUuids();
                int totalPlayers = playerUuids.size();
                
                logger.info("[Migration] Found " + totalPlayers + " unique players to migrate");
                updateMigrationProgress(taskId, "building_json", 
                    "Processing " + totalPlayers + " players...", 0, totalPlayers);

                // Process players in batches
                int processed = 0;
                for (String uuid : playerUuids) {
                    try {
                        PlayerMigrationData playerData = extractPlayerData(uuid);
                        if (playerData != null) {
                            writePlayerToJson(jsonWriter, playerData);
                        }
                        processed++;

                        // Update progress every 100 players
                        if (processed % 100 == 0 || processed == totalPlayers) {
                            updateMigrationProgress(taskId, "building_json",
                                String.format("Processed %d/%d players...", processed, totalPlayers),
                                processed, totalPlayers);
                        }
                    } catch (Exception e) {
                        logger.warning("[Migration] Failed to process player " + uuid + ": " + e.getMessage());
                    }
                }

                jsonWriter.close();
                logger.info("[Migration] Export completed successfully. File: " + migrationFile.getAbsolutePath());
                
                return migrationFile;

            } catch (Exception e) {
                logger.severe("[Migration] Error during LiteBans export: " + e.getMessage());
                e.printStackTrace();
                updateMigrationProgress(taskId, "failed",
                        "Export failed: " + e.getMessage(), 0, null);
                throw new RuntimeException("Failed to export LiteBans data", e);
            } finally {
                // Clean up resources
                if (jsonWriter != null) {
                    try {
                        jsonWriter.close();
                    } catch (IOException e) {
                        logger.warning("[Migration] Failed to close JSON writer: " + e.getMessage());
                    }
                }
            }
        }, migrationExecutor);
    }

    /**
     * Get all unique player UUIDs from LiteBans tables
     */
    private Set<String> getAllPlayerUuids() throws SQLException {
        Set<String> uuids = new LinkedHashSet<>();
        
        String query = "SELECT DISTINCT UUID FROM {bans} " +
                      "UNION " +
                      "SELECT DISTINCT UUID FROM {mutes}";
        
        try (PreparedStatement stmt = databaseProvider.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("UUID");
                if (uuid != null && !uuid.isEmpty() && !uuid.equalsIgnoreCase("CONSOLE")) {
                    uuids.add(uuid);
                }
            }
        }
        
        return uuids;
    }

    /**
     * Extract all data for a single player
     */
    private PlayerMigrationData extractPlayerData(String uuid) throws SQLException {
        PlayerMigrationData data = new PlayerMigrationData();
        data.minecraftUuid = uuid;
        data.usernames = extractUsernames(uuid);
        data.ipList = extractIpAddresses(uuid);
        data.punishments = extractPunishments(uuid);
        
        return data;
    }

    /**
     * Extract usernames from history table
     */
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
                        usernameData.date = formatTimestamp(date);
                        usernames.add(usernameData);
                    }
                }
            }
        }
        
        return usernames;
    }

    /**
     * Extract IP addresses from bans and mutes tables
     */
    private List<IpData> extractIpAddresses(String uuid) throws SQLException {
        Map<String, IpData> ipMap = new HashMap<>();
        
        // Query both bans and mutes for IPs
        extractIpsFromTable(uuid, "{bans}", ipMap);
        extractIpsFromTable(uuid, "{mutes}", ipMap);
        
        return new ArrayList<>(ipMap.values());
    }

    private void extractIpsFromTable(String uuid, String tableToken, Map<String, IpData> ipMap) throws SQLException {
        String query = "SELECT IP, TIME FROM " + tableToken + " WHERE UUID = ? AND IP IS NOT NULL";
        
        try (PreparedStatement stmt = databaseProvider.prepareStatement(query)) {
            stmt.setString(1, uuid);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("IP");
                    long time = rs.getLong("TIME");
                    
                    if (ip != null && !ip.isEmpty()) {
                        IpData ipData = ipMap.computeIfAbsent(ip, k -> {
                            IpData data = new IpData();
                            data.ipAddress = ip;
                            data.logins = new ArrayList<>();
                            data.firstLogin = null;
                            return data;
                        });
                        
                        String loginTime = formatMillisToIso(time);
                        if (!ipData.logins.contains(loginTime)) {
                            ipData.logins.add(loginTime);
                        }
                        
                        if (ipData.firstLogin == null) {
                            ipData.firstLogin = loginTime;
                        } else if (time < parseIsoToMillis(ipData.firstLogin)) {
                            ipData.firstLogin = loginTime;
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract punishments from bans and mutes tables
     */
    private List<PunishmentData> extractPunishments(String uuid) throws SQLException {
        List<PunishmentData> punishments = new ArrayList<>();
        
        // Extract bans (type_ordinal = 2)
        extractPunishmentsFromTable(uuid, "{bans}", 2, "BAN", punishments);
        
        // Extract mutes (type_ordinal = 1)
        extractPunishmentsFromTable(uuid, "{mutes}", 1, "MUTE", punishments);
        
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
                    PunishmentData punishment = new PunishmentData();
                    
                    int litebansId = rs.getInt("ID");
                    punishment.id = "litebans-" + typeName.toLowerCase() + "-" + litebansId;
                    punishment.type = typeName;
                    punishment.typeOrdinal = typeOrdinal;
                    punishment.reason = rs.getString("REASON");
                    
                    long timeIssued = rs.getLong("TIME");
                    punishment.issued = formatMillisToIso(timeIssued);
                    
                    // Handle issuer (could be UUID or "Console")
                    punishment.issuerName = rs.getString("BANNED_BY_NAME");
                    if (punishment.issuerName == null || punishment.issuerName.isEmpty()) {
                        punishment.issuerName = "Console";
                    }
                    
                    // Calculate duration
                    long until = rs.getLong("UNTIL");
                    if (until > 0 && until != -1) {
                        punishment.duration = until - timeIssued;
                    }
                    
                    // Set started time (for active punishments)
                    punishment.started = punishment.issued;
                    
                    // Build data object
                    punishment.data = new HashMap<>();
                    
                    // Determine if active
                    boolean active = rs.getBoolean("ACTIVE");
                    String removedByUuid = rs.getString("REMOVED_BY_UUID");
                    punishment.data.put("active", active && removedByUuid == null);
                    
                    // Add migration metadata
                    punishment.data.put("importedFrom", "litebans");
                    punishment.data.put("importDate", Instant.now().toString());
                    punishment.data.put("litebansId", litebansId);
                    
                    // Add pardon info if pardoned
                    if (removedByUuid != null) {
                        punishment.data.put("pardonedBy", rs.getString("REMOVED_BY_NAME"));
                    }
                    
                    punishments.add(punishment);
                }
            }
        }
    }

    /**
     * Write player data to JSON using streaming writer
     */
    private void writePlayerToJson(StreamingJsonWriter writer, PlayerMigrationData playerData) throws IOException {
        StreamingJsonWriter.PlayerData jsonData = new StreamingJsonWriter.PlayerData(
            playerData.minecraftUuid,
            convertUsernames(playerData.usernames),
            Collections.emptyList(), // Notes
            convertIpList(playerData.ipList),
            convertPunishments(playerData.punishments),
            null // Additional data
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
            result.add(new StreamingJsonWriter.IpEntry(ip.ipAddress, ip.country, ip.firstLogin, ip.logins));
        }
        return result;
    }

    private List<StreamingJsonWriter.PunishmentEntry> convertPunishments(List<PunishmentData> punishments) {
        List<StreamingJsonWriter.PunishmentEntry> result = new ArrayList<>();
        for (PunishmentData p : punishments) {
            result.add(new StreamingJsonWriter.PunishmentEntry(
                p.id, p.type, p.typeOrdinal, p.reason, p.issued, 
                p.issuerName, p.duration, p.started, p.data
            ));
        }
        return result;
    }

    /**
     * Format timestamp to ISO 8601 string
     */
    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return null;
        return timestamp.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * Format milliseconds to ISO 8601 string
     */
    private String formatMillisToIso(long millis) {
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * Parse ISO string to milliseconds
     */
    private long parseIsoToMillis(String isoString) {
        return Instant.parse(isoString).toEpochMilli();
    }

    // Inner classes for data holding
    private static class PlayerMigrationData {
        String minecraftUuid;
        List<UsernameData> usernames;
        List<IpData> ipList;
        List<PunishmentData> punishments;
    }

    private static class UsernameData {
        String username;
        String date;
    }

    private static class IpData {
        String ipAddress;
        String country;
        String firstLogin;
        List<String> logins;
    }

    private static class PunishmentData {
        String id;
        String type;
        int typeOrdinal;
        String reason;
        String issued;
        String issuerName;
        Long duration;
        String started;
        Map<String, Object> data;
    }

    /**
     * Upload migration file to MODL panel
     */
    public CompletableFuture<Boolean> uploadMigrationFile(File jsonFile, String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (jsonFile == null || !jsonFile.exists()) {
                    logger.severe("[Migration] Migration file does not exist");
                    return false;
                }

                // Check file size
                long fileSize = jsonFile.length();
                double fileSizeMB = fileSize / 1024.0 / 1024.0;
                logger.info(String.format("[Migration] Uploading file: %s (%.2f MB, %d bytes)", 
                    jsonFile.getName(), fileSizeMB, fileSize));

                // Update progress
                updateMigrationProgress(taskId, "uploading_json",
                        "Uploading migration file to panel...", 0, null);

                // Upload using Apache HttpClient5
                boolean success = uploadFileMultipart(jsonFile);
                
                if (success) {
                    logger.info("[Migration] File uploaded successfully");
                } else {
                    logger.severe("[Migration] File upload failed");
                }
                
                return success;

            } catch (Exception e) {
                logger.severe("[Migration] Error uploading file: " + e.getMessage());
                e.printStackTrace();
                updateMigrationProgress(taskId, "failed",
                        "Upload failed: " + e.getMessage(), 0, null);
                return false;
            } finally {
                // Clean up local file after upload attempt
                if (jsonFile != null && jsonFile.exists()) {
                    try {
                        Files.delete(jsonFile.toPath());
                        logger.info("[Migration] Cleaned up local migration file");
                    } catch (IOException e) {
                        logger.warning("[Migration] Failed to delete local file: " + e.getMessage());
                    }
                }
            }
        }, migrationExecutor);
    }

    /**
     * Upload file using multipart/form-data
     */
    private boolean uploadFileMultipart(File file) throws Exception {
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = 
                org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            
            // Build multipart entity
            org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder builder = 
                org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder.create();
            
            builder.addBinaryBody(
                "migrationFile",
                file,
                org.apache.hc.core5.http.ContentType.APPLICATION_JSON,
                file.getName()
            );
            
            org.apache.hc.core5.http.HttpEntity multipartEntity = builder.build();
            
            // Create POST request
            String uploadUrl = apiUrl + "/minecraft/migration/upload";
            org.apache.hc.client5.http.classic.methods.HttpPost httpPost = 
                new org.apache.hc.client5.http.classic.methods.HttpPost(uploadUrl);
            
            httpPost.setHeader("X-API-Key", apiKey);
            httpPost.setEntity(multipartEntity);
            
            logger.info("[Migration] Sending upload request to: " + uploadUrl);
            
            // Execute request and handle response
            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                String responseBody = org.apache.hc.core5.http.io.entity.EntityUtils.toString(response.getEntity());
                
                logger.info("[Migration] Upload response: " + statusCode + " - " + responseBody);
                
                if (statusCode >= 200 && statusCode < 300) {
                    return true;
                } else if (statusCode == 413) {
                    logger.severe("[Migration] File too large: " + responseBody);
                    return false;
                } else {
                    logger.severe("[Migration] Upload failed with status " + statusCode + ": " + responseBody);
                    return false;
                }
            });
        }
    }

    /**
     * Update migration progress via HTTP callback to panel
     */
    private void updateMigrationProgress(String taskId, String status, String message,
                                         Integer recordsProcessed, Integer totalRecords) {
        try {
            httpClient.updateMigrationStatus(new MigrationStatusUpdateRequest(taskId, status, message, recordsProcessed, totalRecords));
            logger.info(String.format("[Migration] Progress update: %s - %s (records: %s/%s)",
                    status, message, recordsProcessed, totalRecords));
        } catch (Exception e) {
            logger.warning("[Migration] Failed to update progress: " + e.getMessage());
        }
    }

    /**
     * Shutdown the migration executor
     */
    public void shutdown() {
        migrationExecutor.shutdown();
    }
}

