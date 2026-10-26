package com.dbvein.metadata;

import java.util.List;
import java.util.Map;

public record TablePreviewResponse(
        List<String> columns,
        List<Map<String, Object>> rows,
        int page,
        int size,
        boolean hasMore
) {}
