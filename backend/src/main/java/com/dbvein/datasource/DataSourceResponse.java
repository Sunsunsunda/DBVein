package com.dbvein.datasource;

public record DataSourceResponse(
        Long id,
        String name,
        DatabaseType type,
        String host,
        Integer port,
        String databaseName,
        String serviceName,
        String username,
        String role,
        String passwordMasked
) {}
