package gg.modl.minecraft.core.service.database;

import gg.modl.minecraft.api.DatabaseProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import gg.modl.minecraft.core.util.PluginLogger;

/**
 * Direct JDBC database provider, used when LiteBans API is not available.
 * Replaces LiteBans table tokens ({bans}, {mutes}, etc.) with prefixed table names.
 */
public class JdbcDatabaseProvider implements DatabaseProvider {
    private static final String[][] TABLE_TOKENS = {
        {"{bans}", "bans"}, {"{mutes}", "mutes"}, {"{warnings}", "warnings"},
        {"{kicks}", "kicks"}, {"{history}", "history"}, {"{servers}", "servers"}
    };

    private final DatabaseConfig config;
    private final PluginLogger logger;
    private Connection connection;

    public JdbcDatabaseProvider(DatabaseConfig config, PluginLogger logger) throws SQLException {
        this.config = config;
        this.logger = logger;
        this.connection = establishConnection();
    }

    private Connection establishConnection() throws SQLException {
        try {
            Class.forName(config.getDriverClass());
            logger.info("Connecting to database: " + config.getJdbcUrl());
            return DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found: " + config.getDriverClass(), e);
        }
    }

    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        String processedQuery = replaceTableTokens(query);
        if (connection == null || connection.isClosed()) {
            logger.warning("Database connection was closed, reconnecting...");
            connection = establishConnection();
        }
        return connection.prepareStatement(processedQuery);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) connection = establishConnection();
        return connection;
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
            logger.info("Database connection closed");
        } catch (SQLException e) {
            logger.warning("Failed to close database connection: " + e.getMessage());
        }
    }

    @Override
    public boolean isUsingLiteBansApi() {
        return false;
    }

    private String replaceTableTokens(String query) {
        String result = query;
        for (String[] token : TABLE_TOKENS) {
            result = result.replace(token[0], config.getTablePrefix() + token[1]);
        }
        return result;
    }
}
