package com.dbvein.analysis;

/**
 * Sampled value-match evidence between two columns (read-only on source DB).
 */
public record ValueMatchResult(
        Long sourceTableId,
        Long sourceColumnId,
        Long targetTableId,
        Long targetColumnId,
        String sourceTableName,
        String sourceColumnName,
        String targetTableName,
        String targetColumnName,
        int sampleSize,
        int sourceDistinct,
        int targetDistinct,
        /** Fraction of source sample values that exist in the entire target column (0..1). */
        double sourceInTarget,
        /** Fraction of target sample values that exist in the entire source column (0..1). */
        double targetInSource,
        /** Selected child-to-parent inclusion used for pass/fail; best direction when neither side is a unique PK. */
        double bestInclusion,
        /** Suggested child→parent label, e.g. "BLD.REGIONNO → REGION.REGIONNO". */
        String suggestedDirection,
        boolean passed,
        double threshold,
        String summary
) {}
