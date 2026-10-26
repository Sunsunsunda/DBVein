package com.dbvein.metadata;

import jakarta.persistence.*;

@Entity
@Table(name = "column_meta", indexes = {
        @Index(name = "idx_column_table", columnList = "tableId,columnName", unique = true)
})
public class ColumnMetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tableId;

    @Column(nullable = false)
    private String columnName;

    private String dataType;
    private Integer length;
    private Integer precisionValue;
    private Integer scaleValue;
    private boolean nullable = true;
    private String defaultValue;
    private int ordinalPosition;
    private String commentText;
    private boolean primaryKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getLength() { return length; }
    public void setLength(Integer length) { this.length = length; }
    public Integer getPrecisionValue() { return precisionValue; }
    public void setPrecisionValue(Integer precisionValue) { this.precisionValue = precisionValue; }
    public Integer getScaleValue() { return scaleValue; }
    public void setScaleValue(Integer scaleValue) { this.scaleValue = scaleValue; }
    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public int getOrdinalPosition() { return ordinalPosition; }
    public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
}
