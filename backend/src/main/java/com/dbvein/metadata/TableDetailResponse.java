package com.dbvein.metadata;

import java.util.List;

public record TableDetailResponse(TableMetaEntity table, List<ColumnMetaEntity> columns) {}
