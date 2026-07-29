package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.MigrationService;
import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.service.database.JdbcDatabaseProvider;
import gg.modl.minecraft.core.util.PluginLogger;

import java.io.File;

class MigrationServiceFactory {
    private final Platform platform;
    private final HttpClientHolder httpClientHolder;
    private final DatabaseConfig databaseConfig;
    private final File dataFolder;
    private final LocaleManager localeManager;
    private final PluginLogger logger;

    MigrationServiceFactory(Platform platform, HttpClientHolder httpClientHolder, DatabaseConfig databaseConfig,
                            File dataFolder, LocaleManager localeManager, PluginLogger logger) {
        this.platform = platform;
        this.httpClientHolder = httpClientHolder;
        this.databaseConfig = databaseConfig;
        this.dataFolder = dataFolder;
        this.localeManager = localeManager;
        this.logger = logger;
    }

    MigrationService create() throws Exception {
        DatabaseProvider databaseProvider = platform.createLiteBansDatabaseProvider();
        if (databaseProvider == null) {
            if (databaseConfig == null) {
                logger.warning("LiteBans migration is not configured (database block missing or contains sentinel placeholders); skipping migration");
                return null;
            }
            databaseProvider = new JdbcDatabaseProvider(databaseConfig, logger);
        }
        return new MigrationService(logger, httpClientHolder.getClient(), dataFolder, databaseProvider,
                localeManager.getMessage("config.default_reason"));
    }
}
