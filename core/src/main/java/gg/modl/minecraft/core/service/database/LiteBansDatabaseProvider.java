package gg.modl.minecraft.core.service.database;

import gg.modl.minecraft.api.DatabaseProvider;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * LiteBans API-based database provider
 * Uses reflection to access LiteBans API to avoid compile-time dependency
 */
public class LiteBansDatabaseProvider implements DatabaseProvider {
    private final Object databaseInstance;
    private final Method prepareStatementMethod;
    
    public LiteBansDatabaseProvider() throws Exception {
        // Use reflection to access LiteBans Database.get()
        Class<?> databaseClass = Class.forName("litebans.api.Database");
        Method getMethod = databaseClass.getMethod("get");
        this.databaseInstance = getMethod.invoke(null);
        this.prepareStatementMethod = databaseClass.getMethod("prepareStatement", String.class);
    }
    
    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        try {
            // LiteBans API automatically handles table name tokens like {bans}, {mutes}, etc.
            return (PreparedStatement) prepareStatementMethod.invoke(databaseInstance, query);
        } catch (Exception e) {
            throw new SQLException("Failed to prepare statement using LiteBans API", e);
        }
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        // LiteBans API doesn't expose the raw connection
        // Return null - not needed for LiteBans API usage
        return null;
    }
    
    @Override
    public void close() {
        // LiteBans manages its own connection pool, nothing to close
    }
    
    @Override
    public boolean isUsingLiteBansApi() {
        return true;
    }
}

