package gg.modl.minecraft.core.service.database;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
public class DatabaseConfig {
    String host;
    String database, username, password;
    DatabaseType type;
    String tablePrefix;
    int port;

    @RequiredArgsConstructor
    public enum DatabaseType {
        MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true"),
        MARIADB("mariadb", "org.mariadb.jdbc.Driver", "jdbc:mariadb://%s:%d/%s"),
        POSTGRESQL("postgresql", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s"),
        H2("h2", "org.h2.Driver", "jdbc:h2:file:./%s");

        private final String configKey;
        @Getter private final String driverClass;
        private final String urlFormat;

        public String buildJdbcUrl(String host, int port, String database) {
            if (this == H2) return String.format(urlFormat, database);
            return String.format(urlFormat, host, port, database);
        }

        public static DatabaseType fromString(String type) {
            for (DatabaseType dbType : values()) {
                if (dbType.configKey.equalsIgnoreCase(type)) return dbType;
            }
            throw new IllegalArgumentException("Unknown database type: " + type);
        }
    }

    public String getJdbcUrl() {
        return type.buildJdbcUrl(host, port, database);
    }

    public String getDriverClass() {
        return type.getDriverClass();
    }
}
