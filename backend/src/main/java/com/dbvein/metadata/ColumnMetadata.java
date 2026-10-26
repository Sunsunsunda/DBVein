package com.dbvein.metadata;

public record ColumnMetadata(
        String columnName,
        String dataType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        int ordinal,
        String comment
) {}
