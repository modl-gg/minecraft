package gg.modl.minecraft.core.service.database;

import gg.modl.minecraft.api.DatabaseProvider;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Uses reflection to access LiteBans API, avoiding compile-time dependency.
 * LiteBans handles table name token replacement ({bans}, {mutes}, etc.).
 */
public class LiteBansDatabaseProvider implements DatabaseProvider {
    private static final String LITEBANS_DATABASE_CLASS = "litebans.api.Database";

    private final Object databaseInstance;
    private final Method prepareStatementMethod;

    public LiteBansDatabaseProvider() throws Exception {
        Class<?> databaseClass = Class.forName(LITEBANS_DATABASE_CLASS);
        this.databaseInstance = databaseClass.getMethod("get").invoke(null);
        this.prepareStatementMethod = databaseClass.getMethod("prepareStatement", String.class);
    }

    @Override
    public PreparedStatement prepareStatement(String query) throws SQLException {
        try {
            return (PreparedStatement) prepareStatementMethod.invoke(databaseInstance, query);
        } catch (Exception e) {
            throw new SQLException("Failed to prepare statement using LiteBans API", e);
        }
    }

    @Override
    public Connection getConnection() {
        return null; // LiteBans API doesn't expose raw connections
    }

    @Override
    public void close() {
        // LiteBans manages its own connection pool
    }

    @Override
    public boolean isUsingLiteBansApi() {
        return true;
    }
}
