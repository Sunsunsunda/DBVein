package com.dbvein.metadata;

import com.dbvein.datasource.DataSourceEntity;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class MySqlAdapter implements DatabaseAdapter {

    private final JdbcConnectionFactory connectionFactory;

    public MySqlAdapter(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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
                throw new IllegalStateException("MySQL isValid returned false");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection(DataSourceEntity ds) {
        return testConnection(ds, ds.getPasswordEncrypted());
    }

    @Override
    public List<String> getSchemas(DataSourceEntity ds) {
        String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys') ORDER BY SCHEMA_NAME";
        return queryStrings(ds, sql);
    }

    @Override
    public List<TableMetadata> getTables(DataSourceEntity ds, String schema) {
        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        List<TableMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TableMetadata(schema, rs.getString(1), rs.getString(2),
                            rs.getObject(3) != null ? rs.getLong(3) : null));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MySQL tables: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<ColumnMetadata> getColumns(DataSourceEntity ds, String schema, String table) {
        String sql = """
                SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE,
                       IS_NULLABLE, COLUMN_DEFAULT, ORDINAL_POSITION, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;
        List<ColumnMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ColumnMetadata(
                            rs.getString(1), rs.getString(2), rs.getObject(3) != null ? rs.getInt(3) : null,
                            rs.getObject(4) != null ? rs.getInt(4) : null, rs.getObject(5) != null ? rs.getInt(5) : null,
                            "YES".equalsIgnoreCase(rs.getString(6)), rs.getString(7),
                            rs.getInt(8), rs.getString(9)
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MySQL columns: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<PrimaryKeyMetadata> getPrimaryKeys(DataSourceEntity ds, String schema, String table) {
        String sql = """
                SELECT CONSTRAINT_NAME, COLUMN_NAME, ORDINAL_POSITION
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY'
                ORDER BY ORDINAL_POSITION
                """;
        List<PrimaryKeyMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PrimaryKeyMetadata(rs.getString(1), rs.getString(2), rs.getInt(3)));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MySQL primary keys: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<ForeignKeyMetadata> getForeignKeys(DataSourceEntity ds, String schema) {
        String sql = """
                SELECT k.CONSTRAINT_NAME, k.TABLE_SCHEMA, k.TABLE_NAME, k.COLUMN_NAME,
                       k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE k
                WHERE k.TABLE_SCHEMA = ? AND k.REFERENCED_TABLE_NAME IS NOT NULL
                ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """;
        List<ForeignKeyMetadata> result = new ArrayList<>();
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ForeignKeyMetadata(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7)
                    ));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MySQL foreign keys: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public TablePreviewResponse previewTable(DataSourceEntity ds, String schema, String table, int offset, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        int safeOffset = Math.max(offset, 0);
        String full = SqlIdent.mysql(schema) + "." + SqlIdent.mysql(table);
        String sql = "SELECT * FROM " + full + " LIMIT ? OFFSET ?";
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, safeLimit + 1);
            ps.setInt(2, safeOffset);
            try (ResultSet rs = ps.executeQuery()) {
                return PreviewSupport.fromResultSet(rs, safeOffset / safeLimit + 1, safeLimit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MySQL preview failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ColumnProfileResponse profileColumn(DataSourceEntity ds, String schema, String table, String column, int sampleSize) {
        int size = Math.min(Math.max(sampleSize, 100), 50000);
        String full = SqlIdent.mysql(schema) + "." + SqlIdent.mysql(table);
        String col = SqlIdent.mysql(column);
        String sql = """
                SELECT COUNT(*) AS CNT,
                       COUNT(%s) AS NON_NULL_CNT,
                       COUNT(DISTINCT %s) AS DISTINCT_CNT
                FROM (
                  SELECT %s FROM %s LIMIT ?
                ) t
                """.formatted(col, col, col, full);
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long sampled = rs.getLong(1);
                long nonNull = rs.getLong(2);
                long distinct = rs.getLong(3);
                long nullCount = sampled - nonNull;
                Double rate = sampled == 0 ? null : (double) nullCount / sampled;
                return new ColumnProfileResponse(column, size, sampled, nullCount, nonNull, distinct, rate);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MySQL column profile failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long countSampleRows(DataSourceEntity ds, String schema, String table, int sampleSize) {
        int size = Math.min(Math.max(sampleSize, 100), 50000);
        String full = SqlIdent.mysql(schema) + "." + SqlIdent.mysql(table);
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM " + full + " LIMIT ?) t";
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MySQL sample count failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> sampleDistinctValues(DataSourceEntity ds, String schema, String table, String column, int limit) {
        try (Connection conn = connectionFactory.open(ds, ds.getPasswordEncrypted())) {
            return sampleDistinctValues(conn, schema, table, column, limit);
        } catch (Exception e) {
            throw new IllegalStateException("MySQL sample distinct failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> sampleDistinctValues(Connection conn, String schema, String table, String column, int limit) {
        int size = Math.min(Math.max(limit, 10), 10000);
        String full = SqlIdent.mysql(schema) + "." + SqlIdent.mysql(table);
        String col = SqlIdent.mysql(column);
        String sql = "SELECT DISTINCT " + col + " FROM " + full + " WHERE " + col + " IS NOT NULL LIMIT ?";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null) {
                        result.add(v.trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("MySQL sample distinct failed: " + e.getMessage(), e);
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
            throw new IllegalStateException("MySQL query failed: " + e.getMessage(), e);
        }
        return result;
    }
}
