package com.dbvein.metadata;

public record PrimaryKeyMetadata(
        String constraintName,
        String columnName,
        int ordinal
) {}
