package com.dbvein.metadata;

public record ColumnProfileResponse(
        String columnName,
        int sampleSize,
        long sampledRows,
        long nullCount,
        long nonNullCount,
        long distinctCount,
        Double nullRate
) {}
