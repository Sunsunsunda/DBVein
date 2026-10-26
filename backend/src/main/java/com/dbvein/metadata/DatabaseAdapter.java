package com.dbvein.metadata;

import com.dbvein.datasource.DataSourceEntity;

import java.util.List;

public interface DatabaseAdapter {

    boolean testConnection(DataSourceEntity ds);

    List<String> getSchemas(DataSourceEntity ds);

    List<TableMetadata> getTables(DataSourceEntity ds, String schema);

    List<ColumnMetadata> getColumns(DataSourceEntity ds, String schema, String table);

    List<PrimaryKeyMetadata> getPrimaryKeys(DataSourceEntity ds, String schema, String table);

    List<ForeignKeyMetadata> getForeignKeys(DataSourceEntity ds, String schema);

    TablePreviewResponse previewTable(DataSourceEntity ds, String schema, String table, int offset, int limit);

    ColumnProfileResponse profileColumn(DataSourceEntity ds, String schema, String table, String column, int sampleSize);

    long countSampleRows(DataSourceEntity ds, String schema, String table, int sampleSize);

    /** Read-only sample of distinct non-null values for inclusion-based FK discovery. */
    List<String> sampleDistinctValues(DataSourceEntity ds, String schema, String table, String column, int limit);

    /**
     * Same as {@link #sampleDistinctValues(DataSourceEntity, String, String, String, int)} but reuses an open connection
     * (avoids repeated Oracle/MySQL handshake cost).
     */
    List<String> sampleDistinctValues(java.sql.Connection conn, String schema, String table, String column, int limit);
}
