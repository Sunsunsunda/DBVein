package com.dbvein.metadata;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PreviewSupport {
    private PreviewSupport() {}

    static TablePreviewResponse fromResultSet(ResultSet rs, int page, int size) throws Exception {
        return fromResultSet(rs, page, size, (String[]) null);
    }

    static TablePreviewResponse fromResultSet(ResultSet rs, int page, int size, String... skipColumns) throws Exception {
        Set<String> skip = skipColumns == null
                ? Set.of()
                : Set.of(java.util.Arrays.stream(skipColumns)
                        .map(s -> s.toUpperCase(Locale.ROOT))
                        .toArray(String[]::new));
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<Integer> indexes = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            String label = meta.getColumnLabel(i);
            if (skip.contains(label.toUpperCase(Locale.ROOT))) {
                continue;
            }
            indexes.add(i);
            columns.add(label);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean hasMore = false;
        while (rs.next()) {
            if (rows.size() >= size) {
                hasMore = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < indexes.size(); i++) {
                Object val = rs.getObject(indexes.get(i));
                row.put(columns.get(i), val == null ? null : String.valueOf(val));
            }
            rows.add(row);
        }
        return new TablePreviewResponse(columns, rows, page, size, hasMore);
    }
}
