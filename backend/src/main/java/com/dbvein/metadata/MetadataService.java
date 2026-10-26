package com.dbvein.metadata;

import com.dbvein.analysis.CanvasNodeRepository;
import com.dbvein.analysis.RelationEntity;
import com.dbvein.analysis.RelationRepository;
import com.dbvein.datasource.DataSourceEntity;
import com.dbvein.datasource.DataSourceService;
import com.dbvein.project.AnalysisWorkspaceEntity;
import com.dbvein.project.AnalysisWorkspaceRepository;
import com.dbvein.project.ProjectEntity;
import com.dbvein.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class MetadataService {

    private final ProjectRepository projectRepository;
    private final DataSourceService dataSourceService;
    private final DatabaseAdapterRegistry adapterRegistry;
    private final SnapshotRepository snapshotRepository;
    private final TableMetaRepository tableMetaRepository;
    private final ColumnMetaRepository columnMetaRepository;
    private final AnalysisWorkspaceRepository workspaceRepository;
    private final RelationRepository relationRepository;
    private final CanvasNodeRepository canvasNodeRepository;
    private final JdbcConnectionFactory connectionFactory;

    public MetadataService(ProjectRepository projectRepository, DataSourceService dataSourceService,
                           DatabaseAdapterRegistry adapterRegistry, SnapshotRepository snapshotRepository,
                           TableMetaRepository tableMetaRepository, ColumnMetaRepository columnMetaRepository,
                           AnalysisWorkspaceRepository workspaceRepository, RelationRepository relationRepository,
                           CanvasNodeRepository canvasNodeRepository, JdbcConnectionFactory connectionFactory) {
        this.projectRepository = projectRepository;
        this.dataSourceService = dataSourceService;
        this.adapterRegistry = adapterRegistry;
        this.snapshotRepository = snapshotRepository;
        this.tableMetaRepository = tableMetaRepository;
        this.columnMetaRepository = columnMetaRepository;
        this.workspaceRepository = workspaceRepository;
        this.relationRepository = relationRepository;
        this.canvasNodeRepository = canvasNodeRepository;
        this.connectionFactory = connectionFactory;
    }

    @Transactional
    public SnapshotEntity pullSnapshot(Long projectId, PullSnapshotRequest request) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (project.getSourceDataSourceId() == null) {
            throw new IllegalArgumentException("Project has no source datasource");
        }
        DataSourceEntity ds = dataSourceService.requireWithPassword(project.getSourceDataSourceId());
        DatabaseAdapter adapter = adapterRegistry.get(ds);

        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setProjectId(projectId);
        snapshot.setDataSourceId(ds.getId());
        snapshot.setLabel(request.label() != null ? request.label() : Instant.now().toString());
        SnapshotEntity savedSnapshot = snapshotRepository.save(snapshot);
        final Long savedSnapshotId = savedSnapshot.getId();

        List<String> schemas = request.schemas() != null && !request.schemas().isEmpty()
                ? request.schemas()
                : adapter.getSchemas(ds);

        Map<String, TableMetaEntity> tableIndex = new HashMap<>();
        Map<String, ColumnMetaEntity> columnIndex = new HashMap<>();

        for (String schema : schemas) {
            for (TableMetadata table : adapter.getTables(ds, schema)) {
                TableMetaEntity tableEntity = new TableMetaEntity();
                tableEntity.setSnapshotId(savedSnapshotId);
                tableEntity.setSchemaName(table.schemaName());
                tableEntity.setTableName(table.tableName());
                tableEntity.setCommentText(table.comment());
                tableEntity.setRowCount(table.rowCount());
                tableEntity = tableMetaRepository.save(tableEntity);
                tableIndex.put(key(schema, table.tableName()), tableEntity);

                Set<String> pkColumns = new HashSet<>();
                for (PrimaryKeyMetadata pk : adapter.getPrimaryKeys(ds, schema, table.tableName())) {
                    pkColumns.add(pk.columnName().toUpperCase(Locale.ROOT));
                }

                for (ColumnMetadata col : adapter.getColumns(ds, schema, table.tableName())) {
                    ColumnMetaEntity columnEntity = new ColumnMetaEntity();
                    columnEntity.setTableId(tableEntity.getId());
                    columnEntity.setColumnName(col.columnName());
                    columnEntity.setDataType(col.dataType());
                    columnEntity.setLength(col.length());
                    columnEntity.setPrecisionValue(col.precision());
                    columnEntity.setScaleValue(col.scale());
                    columnEntity.setNullable(col.nullable());
                    columnEntity.setDefaultValue(col.defaultValue());
                    columnEntity.setOrdinalPosition(col.ordinal());
                    columnEntity.setCommentText(col.comment());
                    columnEntity.setPrimaryKey(pkColumns.contains(col.columnName().toUpperCase(Locale.ROOT)));
                    columnEntity = columnMetaRepository.save(columnEntity);
                    columnIndex.put(key(schema, table.tableName(), col.columnName()), columnEntity);
                }
            }

            AnalysisWorkspaceEntity workspace = workspaceRepository.findByProjectId(projectId)
                    .orElseThrow(() -> new IllegalStateException("Workspace missing"));
            for (ForeignKeyMetadata fk : adapter.getForeignKeys(ds, schema)) {
                TableMetaEntity sourceTable = tableIndex.get(key(fk.sourceSchema(), fk.sourceTable()));
                TableMetaEntity targetTable = tableIndex.get(key(fk.targetSchema(), fk.targetTable()));
                ColumnMetaEntity sourceColumn = columnIndex.get(key(fk.sourceSchema(), fk.sourceTable(), fk.sourceColumn()));
                ColumnMetaEntity targetColumn = columnIndex.get(key(fk.targetSchema(), fk.targetTable(), fk.targetColumn()));
                if (sourceTable == null || targetTable == null || sourceColumn == null || targetColumn == null) {
                    continue;
                }
                RelationEntity relation = new RelationEntity();
                relation.setWorkspaceId(workspace.getId());
                relation.setSourceTableId(sourceTable.getId());
                relation.setSourceColumnId(sourceColumn.getId());
                relation.setTargetTableId(targetTable.getId());
                relation.setTargetColumnId(targetColumn.getId());
                relation.setRelationType("FOREIGN_KEY");
                relation.setSourceKind("DATABASE");
                relation.setStatus("CONFIRMED");
                relation.setConfidence(100);
                relationRepository.save(relation);
            }
        }

        workspaceRepository.findByProjectId(projectId).ifPresent(ws -> {
            ws.setActiveSnapshotId(savedSnapshotId);
            workspaceRepository.save(ws);
        });

        return savedSnapshot;
    }

    public List<String> listSourceSchemas(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (project.getSourceDataSourceId() == null) {
            throw new IllegalArgumentException("Project has no source datasource");
        }
        DataSourceEntity ds = dataSourceService.requireWithPassword(project.getSourceDataSourceId());
        return adapterRegistry.get(ds).getSchemas(ds);
    }

    public List<String> listSchemas(Long projectId) {
        SnapshotEntity snapshot = requireActiveSnapshot(projectId);
        return tableMetaRepository.findBySnapshotIdOrderBySchemaNameAscTableNameAsc(snapshot.getId()).stream()
                .map(TableMetaEntity::getSchemaName).distinct().sorted().toList();
    }

    public List<TableMetaEntity> listTables(Long projectId, String schema) {
        SnapshotEntity snapshot = requireActiveSnapshot(projectId);
        if (schema == null || schema.isBlank()) {
            return tableMetaRepository.findBySnapshotIdOrderBySchemaNameAscTableNameAsc(snapshot.getId());
        }
        return tableMetaRepository.findBySnapshotIdAndSchemaNameOrderByTableNameAsc(snapshot.getId(), schema);
    }

    public TableDetailResponse getTableDetail(Long tableId) {
        TableMetaEntity table = tableMetaRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        List<ColumnMetaEntity> columns = columnMetaRepository.findByTableIdOrderByOrdinalPositionAsc(tableId);
        return new TableDetailResponse(table, columns);
    }

    @Transactional
    public TableMetaEntity updateTable(Long tableId, UpdateTableRequest request) {
        TableMetaEntity table = tableMetaRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (request.commentText() != null) {
            table.setCommentText(request.commentText());
        }
        return tableMetaRepository.save(table);
    }

    @Transactional
    public ColumnMetaEntity updateColumn(Long tableId, Long columnId, UpdateColumnRequest request) {
        ColumnMetaEntity column = columnMetaRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found"));
        if (!column.getTableId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to table");
        }
        if (request.commentText() != null) {
            column.setCommentText(request.commentText());
        }
        return columnMetaRepository.save(column);
    }

    @Transactional
    public void deleteTable(Long tableId) {
        TableMetaEntity table = tableMetaRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        SnapshotEntity snapshot = snapshotRepository.findById(table.getSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found"));
        workspaceRepository.findByProjectId(snapshot.getProjectId()).ifPresent(ws ->
                canvasNodeRepository.deleteByWorkspaceIdAndTableId(ws.getId(), tableId));
        relationRepository.deleteBySourceTableIdOrTargetTableId(tableId, tableId);
        columnMetaRepository.deleteByTableId(tableId);
        tableMetaRepository.delete(table);
    }

    @Transactional
    public Map<String, Object> deleteTables(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return Map.of("deleted", 0);
        }
        int count = 0;
        for (Long id : tableIds) {
            if (tableMetaRepository.existsById(id)) {
                deleteTable(id);
                count++;
            }
        }
        return Map.of("deleted", count);
    }

    public TablePreviewResponse previewTable(Long tableId, int page, int size) {
        ResolvedTable resolved = resolveLive(tableId);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        return resolved.adapter().previewTable(resolved.ds(), resolved.table().getSchemaName(),
                resolved.table().getTableName(), offset, safeSize);
    }

    public ColumnProfileResponse profileColumn(Long tableId, Long columnId, int sampleSize) {
        ResolvedTable resolved = resolveLive(tableId);
        ColumnMetaEntity column = columnMetaRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found"));
        if (!column.getTableId().equals(tableId)) {
            throw new IllegalArgumentException("Column does not belong to table");
        }
        return resolved.adapter().profileColumn(resolved.ds(), resolved.table().getSchemaName(),
                resolved.table().getTableName(), column.getColumnName(), sampleSize);
    }

    public TableProfileResponse profileTable(Long tableId, int sampleSize) {
        ResolvedTable resolved = resolveLive(tableId);
        List<ColumnMetaEntity> columns = columnMetaRepository.findByTableIdOrderByOrdinalPositionAsc(tableId);
        long liveSample = resolved.adapter().countSampleRows(resolved.ds(), resolved.table().getSchemaName(),
                resolved.table().getTableName(), sampleSize);
        return new TableProfileResponse(
                resolved.table().getSchemaName(),
                resolved.table().getTableName(),
                resolved.table().getRowCount(),
                liveSample,
                sampleSize,
                columns.size()
        );
    }

    /** Read-only distinct value sample for relation discovery. */
    public List<String> sampleDistinctValues(Long tableId, String columnName, int limit) {
        ResolvedTable resolved = resolveLive(tableId);
        return resolved.adapter().sampleDistinctValues(
                resolved.ds(),
                resolved.table().getSchemaName(),
                resolved.table().getTableName(),
                columnName,
                limit);
    }

    /**
     * Sample two columns with a single JDBC connection when they share a datasource.
     * Link validation opens 1 connection instead of 2.
     */
    public SamplePair sampleDistinctPair(Long tableId1, String column1, Long tableId2, String column2, int limit) {
        ResolvedTable a = resolveLive(tableId1);
        ResolvedTable b = resolveLive(tableId2);
        if (!Objects.equals(a.ds().getId(), b.ds().getId())) {
            return new SamplePair(
                    a.adapter().sampleDistinctValues(a.ds(), a.table().getSchemaName(), a.table().getTableName(), column1, limit),
                    b.adapter().sampleDistinctValues(b.ds(), b.table().getSchemaName(), b.table().getTableName(), column2, limit));
        }
        try (java.sql.Connection conn = connectionFactory.open(a.ds(), a.ds().getPasswordEncrypted())) {
            List<String> va = a.adapter().sampleDistinctValues(
                    conn, a.table().getSchemaName(), a.table().getTableName(), column1, limit);
            List<String> vb = b.adapter().sampleDistinctValues(
                    conn, b.table().getSchemaName(), b.table().getTableName(), column2, limit);
            return new SamplePair(va, vb);
        } catch (Exception e) {
            throw new IllegalStateException("抽样校验失败: " + e.getMessage(), e);
        }
    }

    /** Check sampled values against the entire opposite column, using bounded IN batches. */
    public Set<String> findExistingValues(Long tableId, String columnName, Set<String> values) {
        if (values.isEmpty()) return Set.of();
        ResolvedTable resolved = resolveLive(tableId);
        boolean oracle = resolved.ds().getType() == com.dbvein.datasource.DatabaseType.ORACLE;
        String schema = oracle ? SqlIdent.oracle(resolved.table().getSchemaName()) : SqlIdent.mysql(resolved.table().getSchemaName());
        String table = oracle ? SqlIdent.oracle(resolved.table().getTableName()) : SqlIdent.mysql(resolved.table().getTableName());
        String column = oracle ? SqlIdent.oracle(columnName) : SqlIdent.mysql(columnName);
        try (java.sql.Connection conn = connectionFactory.open(resolved.ds(), resolved.ds().getPasswordEncrypted())) {
            return ValueExistenceQuery.find(conn, schema + "." + table, column, values);
        } catch (Exception e) {
            throw new IllegalStateException("全表存在性校验失败: " + e.getMessage(), e);
        }
    }

    /**
     * Sample many columns of one table on a single connection (relation discovery).
     * @return columnName → values
     */
    public Map<String, List<String>> sampleDistinctColumns(Long tableId, List<String> columnNames, int limit) {
        if (columnNames == null || columnNames.isEmpty()) {
            return Map.of();
        }
        ResolvedTable resolved = resolveLive(tableId);
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (java.sql.Connection conn = connectionFactory.open(resolved.ds(), resolved.ds().getPasswordEncrypted())) {
            for (String columnName : columnNames) {
                try {
                    out.put(columnName, resolved.adapter().sampleDistinctValues(
                            conn,
                            resolved.table().getSchemaName(),
                            resolved.table().getTableName(),
                            columnName,
                            limit));
                } catch (Exception e) {
                    // Skip bad columns; discovery continues.
                    out.put(columnName, List.of());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("表抽样失败: " + e.getMessage(), e);
        }
        return out;
    }

    public record SamplePair(List<String> first, List<String> second) {}

    private ResolvedTable resolveLive(Long tableId) {
        TableMetaEntity table = tableMetaRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        SnapshotEntity snapshot = snapshotRepository.findById(table.getSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found"));
        DataSourceEntity ds = dataSourceService.requireWithPassword(snapshot.getDataSourceId());
        DatabaseAdapter adapter = adapterRegistry.get(ds);
        return new ResolvedTable(table, ds, adapter);
    }

    private record ResolvedTable(TableMetaEntity table, DataSourceEntity ds, DatabaseAdapter adapter) {}

    public SnapshotEntity requireActiveSnapshot(Long projectId) {
        AnalysisWorkspaceEntity ws = workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        if (ws.getActiveSnapshotId() == null) {
            throw new IllegalArgumentException("No snapshot pulled yet");
        }
        return snapshotRepository.findById(ws.getActiveSnapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found"));
    }

    private String key(String schema, String table) {
        return schema.toUpperCase(Locale.ROOT) + "." + table.toUpperCase(Locale.ROOT);
    }

    private String key(String schema, String table, String column) {
        return key(schema, table) + "." + column.toUpperCase(Locale.ROOT);
    }
}
