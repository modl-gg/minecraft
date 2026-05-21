package gg.modl.minecraft.core;

import gg.modl.minecraft.core.service.database.DatabaseConfig;
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

    private final PluginLogger logger = new PluginLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
    };

    @Test
    void loadDatabaseConfig_rejectsSentinelPlaceholders() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", PluginLoader.LITEBANS_HOST_PLACEHOLDER);
        database.put("username", PluginLoader.LITEBANS_USERNAME_PLACEHOLDER);
        database.put("password", PluginLoader.LITEBANS_PASSWORD_PLACEHOLDER);
        database.put("database", PluginLoader.LITEBANS_DATABASE_PLACEHOLDER);

        assertNull(PluginLoader.loadDatabaseConfig(buildConfig(database), tempDir, logger));
    }

    @Test
    void loadDatabaseConfig_rejectsMissingMigrationBlock() {
        assertNull(PluginLoader.loadDatabaseConfig(new HashMap<>(), tempDir, logger));
    }

    @Test
    void loadDatabaseConfig_rejectsMissingHostUsernameOrPassword() {
        Map<String, Object> withoutHost = new LinkedHashMap<>();
        withoutHost.put("username", "real-user");
        withoutHost.put("password", "real-pass");
        assertNull(PluginLoader.loadDatabaseConfig(buildConfig(withoutHost), tempDir, logger));

        Map<String, Object> withoutUsername = new LinkedHashMap<>();
        withoutUsername.put("host", "db.example.com");
        withoutUsername.put("password", "real-pass");
        assertNull(PluginLoader.loadDatabaseConfig(buildConfig(withoutUsername), tempDir, logger));

        Map<String, Object> withoutPassword = new LinkedHashMap<>();
        withoutPassword.put("host", "db.example.com");
        withoutPassword.put("username", "real-user");
        assertNull(PluginLoader.loadDatabaseConfig(buildConfig(withoutPassword), tempDir, logger));
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

        DatabaseConfig config = PluginLoader.loadDatabaseConfig(buildConfig(database), tempDir, logger);

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
