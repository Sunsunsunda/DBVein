package com.dbvein.metadata;

import java.util.List;

public record PullSnapshotRequest(String label, List<String> schemas) {}
