package com.dbvein.analysis;

import java.util.List;

public record DiscoverRelationsResponse(
        int scannedTables,
        int candidates,
        int created,
        int skippedExisting,
        List<RelationView> relations
) {}
