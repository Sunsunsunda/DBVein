package com.dbvein.analysis;

public record RelationView(
        Long id,
        Long sourceTableId,
        Long sourceColumnId,
        Long targetTableId,
        Long targetColumnId,
        String sourceTableName,
        String sourceColumnName,
        String targetTableName,
        String targetColumnName,
        String relationType,
        String sourceKind,
        String status,
        Integer confidence,
        String remark
) {}
