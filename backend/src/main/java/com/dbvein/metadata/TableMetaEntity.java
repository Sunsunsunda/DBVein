package com.dbvein.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "table_meta", indexes = {
        @Index(name = "idx_table_snapshot", columnList = "snapshotId,schemaName,tableName", unique = true)
})
public class TableMetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long snapshotId;

    @Column(nullable = false)
    private String schemaName;

    @Column(nullable = false)
    private String tableName;

    private String commentText;

    private Long rowCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
}
