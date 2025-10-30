package gg.modl.minecraft.core.service.database;

import gg.modl.minecraft.api.DatabaseProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * JDBC-based database provider for direct database access
 * Used when LiteBans API is not available
 */
public class JdbcDatabaseProvider implements DatabaseProvider {
    private final DatabaseConfig config;
    private final Logger logger;
    private Connection connection;
    
    public JdbcDatabaseProvider(DatabaseConfig config, Logger logger) throws SQLException {
        this.config = config;
        this.logger = logger;
        this.connection = establishConnection();
    }
    
    private Connection establishConnection() throws SQLException {
        try {
            Class.forName(config.getDriverClass());
            
            String jdbcUrl = config.getJdbcUrl();
            logger.info("[Migration] Connecting to database: " + jdbcUrl);
            
            return DriverManager.getConnection(
                jdbcUrl,
                config.getUsername(),
                config.getPassword()
            );
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found: " + config.getDriverClass(), e);
        }
    }
    
    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        // Replace LiteBans table tokens with actual table names
        String processedQuery = query
            .replace("{bans}", config.getTablePrefix() + "bans")
            .replace("{mutes}", config.getTablePrefix() + "mutes")
            .replace("{warnings}", config.getTablePrefix() + "warnings")
            .replace("{kicks}", config.getTablePrefix() + "kicks")
            .replace("{history}", config.getTablePrefix() + "history")
            .replace("{servers}", config.getTablePrefix() + "servers");
        
        // Check if connection is still valid
        if (connection == null || connection.isClosed()) {
            logger.warning("[Migration] Database connection was closed, reconnecting...");
            connection = establishConnection();
        }
        
        return connection.prepareStatement(processedQuery);
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = establishConnection();
        }
        return connection;
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("[Migration] Database connection closed");
            } catch (SQLException e) {
                logger.warning("[Migration] Failed to close database connection: " + e.getMessage());
            }
        }
    }
    
    @Override
    public boolean isUsingLiteBansApi() {
        return false;
    }
}

