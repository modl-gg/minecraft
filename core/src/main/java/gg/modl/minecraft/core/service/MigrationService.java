package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import gg.modl.minecraft.core.migration.StreamingJsonWriter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import gg.modl.minecraft.core.util.PluginLogger;

public class MigrationService {
    private static final int PROGRESS_LOG_INTERVAL = 100;

    private final PluginLogger logger;
    private final ModlHttpClient httpClient;
    private final File dataFolder;
    private final DatabaseProvider databaseProvider;
    private final String defaultReason;
    private final LiteBansMigrationRepository repository;

    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "modl-migration");
        t.setDaemon(true);
        return t;
    });

    public MigrationService(PluginLogger logger, ModlHttpClient httpClient, File dataFolder,
                            DatabaseProvider databaseProvider, String defaultReason) {
        this.logger = logger;
        this.httpClient = httpClient;
        this.dataFolder = dataFolder;
        this.databaseProvider = databaseProvider;
        this.defaultReason = defaultReason;
        this.repository = new LiteBansMigrationRepository(databaseProvider, defaultReason);
    }

    public CompletableFuture<File> exportLiteBansData(String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            StreamingJsonWriter jsonWriter = null;
            try {
                updateMigrationProgress(taskId, "building_json", "Starting LiteBans export...", 0, null);

                File migrationFile = new File(dataFolder, "litebans-migration-" + taskId + ".json");
                jsonWriter = new StreamingJsonWriter(migrationFile, defaultReason);

                Set<String> playerUuids = repository.getAllPlayerUuids();
                int totalPlayers = playerUuids.size();
                updateMigrationProgress(taskId, "building_json", "Processing " + totalPlayers + " players...", 0, totalPlayers);

                int processed = 0;
                for (String uuid : playerUuids) {
                    try {
                        LiteBansMigrationRepository.PlayerRecord player = repository.extractPlayerData(uuid);
                        jsonWriter.writePlayer(LiteBansMigrationMapper.toPlayerData(player));
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
                closeDatabaseProvider();
            }
        }, migrationExecutor);
    }

    public CompletableFuture<Boolean> uploadMigrationFile(File jsonFile, String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (jsonFile == null || !jsonFile.exists()) {
                    logger.severe("Migration file does not exist");
                    return false;
                }

                updateMigrationProgress(taskId, "uploading_json", "Uploading migration file to panel...", 0, null);

                boolean success = httpClient.uploadMigrationFile(jsonFile).join();
                if (!success) logger.severe("File upload failed");
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
                    } catch (IOException e) {
                        logger.warning("Failed to delete local file: " + e.getMessage());
                    }
                }
            }
        }, migrationExecutor);
    }

    private void updateMigrationProgress(String taskId, String status, String message,
                                         Integer recordsProcessed, Integer totalRecords) {
        try {
            httpClient.updateMigrationStatus(new MigrationStatusUpdateRequest(taskId, status, message, recordsProcessed, totalRecords));
        } catch (Exception e) {
            logger.warning("Failed to update progress: " + e.getMessage());
        }
    }

    private void closeDatabaseProvider() {
        if (databaseProvider == null) return;
        try {
            databaseProvider.close();
        } catch (Exception e) {
            logger.warning("Failed to close migration database provider: " + e.getMessage());
        }
    }

    public void shutdown() {
        migrationExecutor.shutdown();
        closeDatabaseProvider();
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
