package com.dbvein.metadata;

import com.dbvein.datasource.DataSourceEntity;
import com.dbvein.datasource.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

@Component
public class JdbcConnectionFactory {

    public Connection open(DataSourceEntity ds, String password) throws SQLException {
        return DriverManager.getConnection(buildJdbcUrl(ds), buildProperties(ds, password));
    }

    public String buildJdbcUrl(DataSourceEntity ds) {
        return switch (ds.getType()) {
            case ORACLE -> {
                if (ds.getServiceName() != null && !ds.getServiceName().isBlank()) {
                    yield String.format("jdbc:oracle:thin:@%s:%d/%s", ds.getHost(), ds.getPort(), ds.getServiceName());
                }
                yield String.format("jdbc:oracle:thin:@%s:%d:%s", ds.getHost(), ds.getPort(),
                        ds.getDatabaseName() != null ? ds.getDatabaseName() : "ORCL");
            }
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
        };
    }

    private Properties buildProperties(DataSourceEntity ds, String password) {
        Properties props = new Properties();
        props.setProperty("user", ds.getUsername());
        props.setProperty("password", password);
        if (ds.getType() == DatabaseType.ORACLE) {
            props.setProperty("oracle.jdbc.ReadTimeout", "60000");
        }
        return props;
    }
}
