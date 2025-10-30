package gg.modl.minecraft.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Interface for database access - allows platform-specific implementations
 * to provide either LiteBans API access or direct JDBC connections
 */
public interface DatabaseProvider {
    
    /**
     * Prepare a SQL statement for execution
     * Tokens like {bans}, {mutes}, {history} will be replaced with actual table names
     * 
     * @param query SQL query with optional table name tokens
     * @return PreparedStatement ready for execution
     * @throws SQLException if statement preparation fails
     */
    PreparedStatement prepareStatement(String query) throws SQLException;
    
    /**
     * Get the raw database connection (used for transaction management)
     * May return null if using LiteBans API wrapper
     * 
     * @return Connection or null
     * @throws SQLException if connection cannot be obtained
     */
    Connection getConnection() throws SQLException;
    
    /**
     * Close the database provider and release resources
     */
    void close();
    
    /**
     * Check if this provider is using LiteBans API
     * 
     * @return true if using LiteBans API, false if using direct JDBC
     */
    boolean isUsingLiteBansApi();
}

