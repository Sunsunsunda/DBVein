package com.dbvein.metadata;

import com.dbvein.datasource.DataSourceEntity;
import com.dbvein.datasource.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class DatabaseAdapterRegistry {

    private final OracleAdapter oracleAdapter;
    private final MySqlAdapter mySqlAdapter;

    public DatabaseAdapterRegistry(OracleAdapter oracleAdapter, MySqlAdapter mySqlAdapter) {
        this.oracleAdapter = oracleAdapter;
        this.mySqlAdapter = mySqlAdapter;
    }

    public DatabaseAdapter get(DataSourceEntity ds) {
        return switch (ds.getType()) {
            case ORACLE -> oracleAdapter;
            case MYSQL -> mySqlAdapter;
        };
    }

    public boolean testConnection(DataSourceEntity ds, String plainPassword) {
        DataSourceEntity probe = copyForProbe(ds, plainPassword);
        return switch (ds.getType()) {
            case ORACLE -> oracleAdapter.testConnection(probe, plainPassword);
            case MYSQL -> mySqlAdapter.testConnection(probe, plainPassword);
        };
    }

    public void testConnectionOrThrow(DataSourceEntity ds, String plainPassword) {
        DataSourceEntity probe = copyForProbe(ds, plainPassword);
        switch (ds.getType()) {
            case ORACLE -> oracleAdapter.testConnectionOrThrow(probe, plainPassword);
            case MYSQL -> mySqlAdapter.testConnectionOrThrow(probe, plainPassword);
        }
    }

    private DataSourceEntity copyForProbe(DataSourceEntity ds, String plainPassword) {
        DataSourceEntity probe = new DataSourceEntity();
        probe.setType(ds.getType());
        probe.setHost(ds.getHost());
        probe.setPort(ds.getPort());
        probe.setDatabaseName(ds.getDatabaseName());
        probe.setServiceName(ds.getServiceName());
        probe.setUsername(ds.getUsername());
        probe.setPasswordEncrypted(plainPassword);
        return probe;
    }
}
