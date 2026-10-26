package com.dbvein.analysis;

public record RelationRequest(
        Long sourceColumnId,
        Long targetColumnId,
        String relationType,
        String remark,
        /** When false/null, weak value match blocks create. */
        Boolean force,
        Integer sampleSize,
        Double minInclusion
) {
    public RelationRequest(Long sourceColumnId, Long targetColumnId, String relationType, String remark) {
        this(sourceColumnId, targetColumnId, relationType, remark, null, null, null);
    }
}
