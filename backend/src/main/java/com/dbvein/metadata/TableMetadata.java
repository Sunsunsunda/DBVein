package com.dbvein.metadata;

public record TableMetadata(
        String schemaName,
        String tableName,
        String comment,
        Long rowCount
) {}
