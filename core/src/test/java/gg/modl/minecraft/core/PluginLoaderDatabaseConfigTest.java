package gg.modl.minecraft.core;

import gg.modl.minecraft.core.service.database.DatabaseConfig;
import gg.modl.minecraft.core.support.RecordingPluginLogger;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PluginLoaderDatabaseConfigTest {

    @TempDir
    Path tempDir;

    private final PluginLogger logger = new RecordingPluginLogger();

    @Test
    void loadDatabaseConfig_rejectsSentinelPlaceholders() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", PluginConfiguration.LITEBANS_HOST_PLACEHOLDER);
        database.put("username", PluginConfiguration.LITEBANS_USERNAME_PLACEHOLDER);
        database.put("password", PluginConfiguration.LITEBANS_PASSWORD_PLACEHOLDER);
        database.put("database", PluginConfiguration.LITEBANS_DATABASE_PLACEHOLDER);

        assertNull(PluginConfiguration.loadDatabaseConfig(buildConfig(database), tempDir, logger));
    }

    @Test
    void loadDatabaseConfig_rejectsMissingMigrationBlock() {
        assertNull(PluginConfiguration.loadDatabaseConfig(new HashMap<>(), tempDir, logger));
    }

    @Test
    void loadDatabaseConfig_rejectsMissingHostUsernameOrPassword() {
        Map<String, Object> withoutHost = new LinkedHashMap<>();
        withoutHost.put("username", "real-user");
        withoutHost.put("password", "real-pass");
        assertNull(PluginConfiguration.loadDatabaseConfig(buildConfig(withoutHost), tempDir, logger));

        Map<String, Object> withoutUsername = new LinkedHashMap<>();
        withoutUsername.put("host", "db.example.com");
        withoutUsername.put("password", "real-pass");
        assertNull(PluginConfiguration.loadDatabaseConfig(buildConfig(withoutUsername), tempDir, logger));

        Map<String, Object> withoutPassword = new LinkedHashMap<>();
        withoutPassword.put("host", "db.example.com");
        withoutPassword.put("username", "real-user");
        assertNull(PluginConfiguration.loadDatabaseConfig(buildConfig(withoutPassword), tempDir, logger));
    }

    @Test
    void loadDatabaseConfig_returnsConfigWhenAllRequiredFieldsAreReal() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", "db.example.com");
        database.put("username", "real-user");
        database.put("password", "real-pass");
        database.put("database", "real_db");
        database.put("type", "mysql");
        database.put("port", 3307);
        database.put("table_prefix", "lb_");

        DatabaseConfig config = PluginConfiguration.loadDatabaseConfig(buildConfig(database), tempDir, logger);

        assertNotNull(config);
        assertEquals("db.example.com", config.getHost());
        assertEquals("real-user", config.getUsername());
        assertEquals("real-pass", config.getPassword());
        assertEquals("real_db", config.getDatabase());
        assertEquals(3307, config.getPort());
        assertEquals("lb_", config.getTablePrefix());
    }

    private static Map<String, Object> buildConfig(Map<String, Object> database) {
        Map<String, Object> litebans = new LinkedHashMap<>();
        litebans.put("database", database);
        Map<String, Object> migration = new LinkedHashMap<>();
        migration.put("litebans", litebans);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("migration", migration);
        return root;
    }
}
