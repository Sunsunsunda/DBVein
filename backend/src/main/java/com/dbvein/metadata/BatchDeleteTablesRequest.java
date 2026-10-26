package com.dbvein.metadata;

import java.util.List;

public record BatchDeleteTablesRequest(List<Long> tableIds) {}
