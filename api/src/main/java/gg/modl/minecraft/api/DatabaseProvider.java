package gg.modl.minecraft.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface DatabaseProvider {

    PreparedStatement prepareStatement(String query) throws SQLException;

    Connection getConnection() throws SQLException;

    void close();

    boolean isUsingLiteBansApi();
}

