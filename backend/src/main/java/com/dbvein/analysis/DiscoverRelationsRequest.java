package com.dbvein.analysis;

public record DiscoverRelationsRequest(
        /** canvas = only tables on canvas; snapshot = all tables in active snapshot */
        String scope,
        Integer minConfidence,
        Integer maxResults,
        /** Distinct values sampled per column for inclusion checks (default 2000). */
        Integer sampleSize,
        /** Minimum child-in-parent inclusion ratio 0..1 (default 0.85). */
        Double minInclusion
) {
    public DiscoverRelationsRequest(String scope, Integer minConfidence, Integer maxResults) {
        this(scope, minConfidence, maxResults, null, null);
    }
}
