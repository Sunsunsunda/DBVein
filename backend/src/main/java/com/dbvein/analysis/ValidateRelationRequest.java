package com.dbvein.analysis;

public record ValidateRelationRequest(
        Long sourceColumnId,
        Long targetColumnId,
        Integer sampleSize,
        Double minInclusion
) {}
