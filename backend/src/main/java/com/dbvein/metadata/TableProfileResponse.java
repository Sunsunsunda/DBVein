package com.dbvein.metadata;

public record TableProfileResponse(
        String schemaName,
        String tableName,
        Long estimatedRowCount,
        Long liveSampleCount,
        int sampleSize,
        int columnCount
) {}
