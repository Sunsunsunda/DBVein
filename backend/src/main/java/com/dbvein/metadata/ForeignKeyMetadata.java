package com.dbvein.metadata;

public record ForeignKeyMetadata(
        String constraintName,
        String sourceSchema,
        String sourceTable,
        String sourceColumn,
        String targetSchema,
        String targetTable,
        String targetColumn
) {}
