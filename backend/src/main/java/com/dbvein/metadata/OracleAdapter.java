package com.dbvein.metadata;

import com.dbvein.datasource.DataSourceEntity;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class OracleAdapter implements DatabaseAdapter {

    private final JdbcConnectionFactory connectionFactory;

    public OracleAdapter(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public boolean testConnection(DataSourceEntity ds) {
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted())) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean testConnection(DataSourceEntity ds, String password) {
        try (Connection conn = connectionFactory.open(ds, password)) {
            return conn.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    public void testConnectionOrThrow(DataSourceEntity ds, String password) {
        try (Connection conn = connectionFactory.open(ds, password)) {
            if (!conn.isValid(5)) {
                throw new IllegalStateException("Oracle isValid returned false");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSchemas(DataSourceEntity ds) {
        String sql = """
                SELECT DISTINCT OWNER FROM ALL_TABLES
                WHERE OWNER NOT IN ('SYS','SYSTEM','MDSYS','XDB','CTXSYS','ORDSYS','OUTLN','DBSNMP','WMSYS','APPQOSSYS','ORDDATA','OLAPSYS','SI_INFORMTN_SCHEMA')
                ORDER BY OWNER
                """;
        return queryStrings(ds, sql);
    }

    @Override
    public List<TableMetadata> getTables(DataSourceEntity ds, String schema) {
        String sql = """
                SELECT t.TABLE_NAME, tc.COMMENTS, t.NUM_ROWS
                FROM ALL_TABLES t
                LEFT JOIN ALL_TAB_COMMENTS tc ON tc.OWNER = t.OWNER AND tc.TABLE_NAME = t.TABLE_NAME AND tc.TABLE_TYPE = 'TABLE'
                WHERE t.OWNER = ?
                ORDER BY t.TABLE_NAME
                """;
        List<TableMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TableMetadata(schema.toUpperCase(), rs.getString(1), rs.getString(2),
                            rs.getObject(3) != null ? rs.getLong(3) : null));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Oracle tables: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<ColumnMetadata> getColumns(DataSourceEntity ds, String schema, String table) {
        String sql = """
                SELECT c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, c.DATA_SCALE,
                       c.NULLABLE, c.DATA_DEFAULT, c.COLUMN_ID, cc.COMMENTS
                FROM ALL_TAB_COLUMNS c
                LEFT JOIN ALL_COL_COMMENTS cc ON cc.OWNER = c.OWNER AND cc.TABLE_NAME = c.TABLE_NAME AND cc.COLUMN_NAME = c.COLUMN_NAME
                WHERE c.OWNER = ? AND c.TABLE_NAME = ?
                ORDER BY c.COLUMN_ID
                """;
        List<ColumnMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema.toUpperCase());
            ps.setString(2, table.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ColumnMetadata(
                            rs.getString(1), rs.getString(2), rs.getObject(3) != null ? rs.getInt(3) : null,
                            rs.getObject(4) != null ? rs.getInt(4) : null, rs.getObject(5) != null ? rs.getInt(5) : null,
                            "Y".equalsIgnoreCase(rs.getString(6)), rs.getString(7),
                            rs.getInt(8), rs.getString(9)
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Oracle columns: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<PrimaryKeyMetadata> getPrimaryKeys(DataSourceEntity ds, String schema, String table) {
        String sql = """
                SELECT c.CONSTRAINT_NAME, cc.COLUMN_NAME, cc.POSITION
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'P'
                ORDER BY cc.POSITION
                """;
        List<PrimaryKeyMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema.toUpperCase());
            ps.setString(2, table.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PrimaryKeyMetadata(rs.getString(1), rs.getString(2), rs.getInt(3)));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Oracle primary keys: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<ForeignKeyMetadata> getForeignKeys(DataSourceEntity ds, String schema) {
        String sql = """
                SELECT c.CONSTRAINT_NAME, c.OWNER, c.TABLE_NAME, cc.COLUMN_NAME,
                       r.OWNER AS R_OWNER, r.TABLE_NAME AS R_TABLE, rcc.COLUMN_NAME AS R_COLUMN
                FROM ALL_CONSTRAINTS c
                JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                JOIN ALL_CONSTRAINTS rc ON c.R_OWNER = rc.OWNER AND c.R_CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                JOIN ALL_CONS_COLUMNS rcc ON rc.OWNER = rcc.OWNER AND rc.CONSTRAINT_NAME = rcc.CONSTRAINT_NAME AND cc.POSITION = rcc.POSITION
                JOIN ALL_TABLES r ON r.OWNER = rc.OWNER AND r.TABLE_NAME = rc.TABLE_NAME
                WHERE c.CONSTRAINT_TYPE = 'R' AND c.OWNER = ?
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, cc.POSITION
                """;
        List<ForeignKeyMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ForeignKeyMetadata(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7)
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Oracle foreign keys: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public TablePreviewResponse previewTable(DataSourceEntity ds, String schema, String table, int offset, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        int safeOffset = Math.max(offset, 0);
        int end = safeOffset + safeLimit + 1;
        String full = SqlIdent.oracle(schema) + "." + SqlIdent.oracle(table);
        // Compatible with Oracle 11g+ (avoid OFFSET/FETCH which older listeners reject)
        String sql = """
                SELECT * FROM (
                  SELECT q.*, ROWNUM AS DBVEIN_RN FROM (
                    SELECT * FROM %s
                  ) q WHERE ROWNUM <= %d
                ) WHERE DBVEIN_RN > %d
                """.formatted(full, end, safeOffset);
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return PreviewSupport.fromResultSet(rs, safeOffset / safeLimit + 1, safeLimit, "DBVEIN_RN");
        } catch (Exception e) {
            throw new IllegalStateException("Oracle preview failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ColumnProfileResponse profileColumn(DataSourceEntity ds, String schema, String table, String column, int sampleSize) {
        int size = Math.min(Math.max(sampleSize, 100), 50000);
        String full = SqlIdent.oracle(schema) + "." + SqlIdent.oracle(table);
        String col = SqlIdent.oracle(column);
        String sql = """
                SELECT COUNT(*) AS CNT,
                       COUNT(C) AS NON_NULL_CNT,
                       COUNT(DISTINCT C) AS DISTINCT_CNT
                FROM (
                  SELECT %s AS C FROM %s WHERE ROWNUM <= %d
                )
                """.formatted(col, full, size);
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            long sampled = rs.getLong(1);
            long nonNull = rs.getLong(2);
            long distinct = rs.getLong(3);
            long nullCount = sampled - nonNull;
            Double rate = sampled == 0 ? null : (double) nullCount / sampled;
            return new ColumnProfileResponse(column, size, sampled, nullCount, nonNull, distinct, rate);
        } catch (Exception e) {
            throw new IllegalStateException("Oracle column profile failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long countSampleRows(DataSourceEntity ds, String schema, String table, int sampleSize) {
        int size = Math.min(Math.max(sampleSize, 100), 50000);
        String full = SqlIdent.oracle(schema) + "." + SqlIdent.oracle(table);
        String sql = "SELECT COUNT(*) FROM (SELECT 1 AS X FROM " + full + " WHERE ROWNUM <= " + size + ")";
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("Oracle sample count failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> sampleDistinctValues(DataSourceEntity ds, String schema, String table, String column, int limit) {
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted())) {
            return sampleDistinctValues(conn, schema, table, column, limit);
        } catch (Exception e) {
            throw new IllegalStateException("Oracle sample distinct failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> sampleDistinctValues(Connection conn, String schema, String table, String column, int limit) {
        int size = Math.min(Math.max(limit, 10), 10000);
        // Scan at most `size` rows — connection cost dominates; no need for size*20.
        String full = SqlIdent.oracle(schema) + "." + SqlIdent.oracle(table);
        String col = SqlIdent.oracle(column);
        String sql = """
                SELECT DISTINCT C FROM (
                  SELECT %s AS C FROM %s WHERE %s IS NOT NULL AND ROWNUM <= %d
                )
                """.formatted(col, full, col, size);
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) {
                    result.add(v.trim());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Oracle sample distinct failed: " + e.getMessage(), e);
        }
        return result;
    }

    private List<String> queryStrings(DataSourceEntity ds, String sql) {
        List<String> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Oracle query failed: " + e.getMessage(), e);
        }
        return result;
    }
}
