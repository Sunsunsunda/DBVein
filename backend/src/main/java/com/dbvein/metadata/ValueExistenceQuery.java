package com.dbvein.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** SQL identifiers must already be validated and quoted. Values are always bound parameters. */
public final class ValueExistenceQuery {
    private ValueExistenceQuery() {}

    public static Set<String> find(Connection conn, String table, String column, Set<String> values) throws SQLException {
        List<String> input = new ArrayList<>(values);
        Set<String> matched = new HashSet<>();
        // Below Oracle's 1000-expression IN limit, and bounded for MySQL too.
        for (int offset = 0; offset < input.size(); offset += 500) {
            List<String> batch = input.subList(offset, Math.min(input.size(), offset + 500));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + " IN (" + placeholders + ")";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setQueryTimeout(60);
                for (int i = 0; i < batch.size(); i++) statement.setString(i + 1, batch.get(i));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String value = result.getString(1);
                        if (value != null && values.contains(value.trim())) matched.add(value.trim());
                    }
                }
            }
        }
        return matched;
    }
}
