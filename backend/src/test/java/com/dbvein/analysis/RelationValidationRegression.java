package com.dbvein.analysis;

import com.dbvein.metadata.ValueExistenceQuery;
import java.sql.*;
import java.util.*;

/** Standalone regression harness; run with SQLite JDBC on the classpath and -ea. */
public class RelationValidationRegression {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            conn.createStatement().execute("CREATE TABLE parent (id TEXT PRIMARY KEY)");
            try (PreparedStatement insert = conn.prepareStatement("INSERT INTO parent VALUES (?)")) {
                for (int i = 0; i < 6000; i++) {
                    insert.setString(1, "key-" + i); insert.addBatch();
                }
                insert.executeBatch();
            }
            Set<String> sample = new HashSet<>();
            // Entirely outside the first 2000 parent values: old sample-overlap algorithm reports 0%.
            for (int i = 3000; i < 5000; i++) sample.add("key-" + i);
            Set<String> hits = ValueExistenceQuery.find(conn, "`parent`", "`id`", sample);
            check(hits.size() == 2000, "Full-parent lookup must match disjoint samples, across four batches");
            sample.add("missing"); sample.add("x' OR 1=1 --");
            hits = ValueExistenceQuery.find(conn, "`parent`", "`id`", sample);
            check(hits.size() == 2000, "Missing values and SQL text must stay unmatched");
            check(ValueExistenceQuery.find(conn, "parent", "id", Set.of()).isEmpty(), "Empty input");
            check(RelationValueMatchService.selectedInclusion(true, false, 0.1, 1) == 1, "PK source uses reverse inclusion");
            check(RelationValueMatchService.selectedInclusion(false, true, 0.1, 1) == 0.1, "Reverse coverage cannot hide orphan references");
            check(RelationValueMatchService.selectedInclusion(false, false, 0.1, 0.9) == 0.9, "Unknown direction keeps best evidence");
        }
        System.out.println("PASS: full-column lookup, batching, unmatched values, parameter binding and PK direction");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
