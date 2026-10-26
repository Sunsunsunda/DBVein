package com.dbvein.datasource;

public record DataSourceRequest(
        String name,
        DatabaseType type,
        String host,
        Integer port,
        String databaseName,
        String serviceName,
        String username,
        String password,
        String role
) {}
