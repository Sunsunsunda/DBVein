package com.dbvein.analysis;

import com.dbvein.metadata.ColumnMetaEntity;
import com.dbvein.metadata.ColumnMetaRepository;
import com.dbvein.metadata.TableMetaEntity;
import com.dbvein.metadata.TableMetaRepository;
import com.dbvein.project.AnalysisWorkspaceEntity;
import com.dbvein.project.AnalysisWorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnalysisService {

    private final AnalysisWorkspaceRepository workspaceRepository;
    private final CanvasNodeRepository canvasNodeRepository;
    private final RelationRepository relationRepository;
    private final TableMetaRepository tableMetaRepository;
    private final ColumnMetaRepository columnMetaRepository;
    private final DiscoveryRulesService discoveryRulesService;
    private final RelationValueMatchService valueMatchService;

    public AnalysisService(AnalysisWorkspaceRepository workspaceRepository, CanvasNodeRepository canvasNodeRepository,
                           RelationRepository relationRepository, TableMetaRepository tableMetaRepository,
                           ColumnMetaRepository columnMetaRepository, DiscoveryRulesService discoveryRulesService,
                           RelationValueMatchService valueMatchService) {
        this.workspaceRepository = workspaceRepository;
        this.canvasNodeRepository = canvasNodeRepository;
        this.relationRepository = relationRepository;
        this.tableMetaRepository = tableMetaRepository;
        this.columnMetaRepository = columnMetaRepository;
        this.discoveryRulesService = discoveryRulesService;
        this.valueMatchService = valueMatchService;
    }

    public AnalysisWorkspaceEntity requireWorkspace(Long projectId) {
        return workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
    }

    public List<CanvasNodeEntity> getCanvas(Long projectId) {
        return canvasNodeRepository.findByWorkspaceId(requireWorkspace(projectId).getId());
    }

    @Transactional
    public List<CanvasNodeEntity> saveCanvas(Long projectId, List<CanvasNodeRequest> nodes) {
        if (nodes == null) throw new IllegalArgumentException("Canvas nodes are required");
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        List<CanvasNodeEntity> existing = canvasNodeRepository.findByWorkspaceId(ws.getId());
        Map<Long, CanvasNodeEntity> byTable = new HashMap<>();
        for (CanvasNodeEntity e : existing) {
            byTable.put(e.getTableId(), e);
        }
        Set<Long> keep = new HashSet<>();
        for (CanvasNodeRequest node : nodes) {
            requireActiveTable(ws, node.tableId());
            if (!keep.add(node.tableId())) throw new IllegalArgumentException("Duplicate table in canvas");
            CanvasNodeEntity entity = byTable.getOrDefault(node.tableId(), new CanvasNodeEntity());
            entity.setWorkspaceId(ws.getId());
            entity.setTableId(node.tableId());
            entity.setPositionX(node.positionX());
            entity.setPositionY(node.positionY());
            entity.setWidth(node.width());
            entity.setHeight(node.height());
            entity.setCollapsed(node.collapsed());
            canvasNodeRepository.save(entity);
        }
        for (CanvasNodeEntity e : existing) {
            if (!keep.contains(e.getTableId())) {
                canvasNodeRepository.delete(e);
            }
        }
        return canvasNodeRepository.findByWorkspaceId(ws.getId());
    }

    @Transactional
    public CanvasNodeEntity addTableToCanvas(Long projectId, AddTableRequest request) {
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        requireActiveTable(ws, request.tableId());
        CanvasNodeEntity entity = canvasNodeRepository.findByWorkspaceIdAndTableId(ws.getId(), request.tableId())
                .orElseGet(CanvasNodeEntity::new);
        entity.setWorkspaceId(ws.getId());
        entity.setTableId(request.tableId());
        entity.setPositionX(request.positionX());
        entity.setPositionY(request.positionY());
        entity.setCollapsed(false);
        return canvasNodeRepository.save(entity);
    }

    @Transactional
    public void removeTableFromCanvas(Long projectId, Long tableId) {
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        canvasNodeRepository.deleteByWorkspaceIdAndTableId(ws.getId(), tableId);

    }

    public List<RelationView> listRelations(Long projectId) {
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        return relationRepository.findByWorkspaceIdAndStatusNot(ws.getId(), "IGNORED").stream()
                .map(this::toView).toList();
    }

    @Transactional
    public RelationView createRelation(Long projectId, RelationRequest request) {
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        ColumnMetaEntity sourceCol = columnMetaRepository.findById(request.sourceColumnId())
                .orElseThrow(() -> new IllegalArgumentException("Source column not found"));
        ColumnMetaEntity targetCol = columnMetaRepository.findById(request.targetColumnId())
                .orElseThrow(() -> new IllegalArgumentException("Target column not found"));

        requireActiveTable(ws, sourceCol.getTableId());
        requireActiveTable(ws, targetCol.getTableId());
        if (sourceCol.getId().equals(targetCol.getId())) throw new IllegalArgumentException("不能将同一字段关联到自己");
        boolean duplicate = relationRepository.findByWorkspaceId(ws.getId()).stream()
                .filter(r -> !"IGNORED".equals(r.getStatus()))
                .anyMatch(r -> (r.getSourceColumnId().equals(sourceCol.getId()) && r.getTargetColumnId().equals(targetCol.getId()))
                        || (r.getSourceColumnId().equals(targetCol.getId()) && r.getTargetColumnId().equals(sourceCol.getId())));
        if (duplicate) throw new IllegalArgumentException("这两个字段之间已经存在关系，请查看已有连线");

        ValueMatchResult match = valueMatchService.match(
                request.sourceColumnId(), request.targetColumnId(),
                resolveSampleSize(projectId, request.sampleSize()),
                resolveMinInclusion(projectId, request.minInclusion()));
        boolean force = Boolean.TRUE.equals(request.force());
        if (!match.passed() && !force) {
            throw new IllegalArgumentException(
                    "数据校验未通过：" + match.summary()
                            + "。若仍要创建，请确认后强制保存。");
        }

        RelationEntity entity = new RelationEntity();
        entity.setWorkspaceId(ws.getId());
        entity.setSourceTableId(sourceCol.getTableId());
        entity.setSourceColumnId(sourceCol.getId());
        entity.setTargetTableId(targetCol.getTableId());
        entity.setTargetColumnId(targetCol.getId());
        entity.setRelationType(request.relationType() != null ? request.relationType() : "USER_DEFINED");
        entity.setSourceKind("USER");
        entity.setStatus("CONFIRMED");
        entity.setConfidence(valueMatchService.confidenceFrom(match));
        String remark = valueMatchService.remarkFrom(match);
        if (force && !match.passed()) {
            remark = "[强制保存] " + remark;
        }
        if (request.remark() != null && !request.remark().isBlank()) {
            remark = request.remark() + "；" + remark;
        }
        entity.setRemark(remark);
        return toView(relationRepository.save(entity));
    }

    public ValueMatchResult validateRelation(Long projectId, ValidateRelationRequest request) {
        AnalysisWorkspaceEntity ws = requireWorkspace(projectId);
        requireActiveColumn(ws, request.sourceColumnId());
        requireActiveColumn(ws, request.targetColumnId());
        return valueMatchService.match(
                request.sourceColumnId(), request.targetColumnId(),
                resolveSampleSize(projectId, request.sampleSize()),
                resolveMinInclusion(projectId, request.minInclusion()));
    }

    @Transactional
    public RelationView ignoreRelation(Long projectId, Long id) {
        RelationEntity entity = requireRelation(projectId, id);
        entity.setStatus("IGNORED");
        return toView(relationRepository.save(entity));
    }

    @Transactional
    public RelationView confirmRelation(Long projectId, Long id, Boolean force) {
        RelationEntity entity = requireRelation(projectId, id);
        ValueMatchResult match = valueMatchService.match(
                entity.getSourceColumnId(), entity.getTargetColumnId(),
                projectId == null ? null : resolveSampleSize(projectId, null),
                projectId == null ? null : resolveMinInclusion(projectId, null));
        if (!match.passed() && !Boolean.TRUE.equals(force)) {
            throw new IllegalArgumentException(
                    "确认前数据校验未通过：" + match.summary()
                            + "。若仍要确认，请强制确认。");
        }
        entity.setStatus("CONFIRMED");
        entity.setConfidence(valueMatchService.confidenceFrom(match));
        String remark = valueMatchService.remarkFrom(match);
        if (Boolean.TRUE.equals(force) && !match.passed()) {
            remark = "[强制确认] " + remark;
        }
        entity.setRemark(remark);
        if ("INFERRED".equals(entity.getRelationType())) {
            entity.setRelationType("BUSINESS");
            entity.setSourceKind("USER");
        }
        return toView(relationRepository.save(entity));
    }

    @Transactional
    public int ignoreRelations(Long projectId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        for (Long id : ids) requireRelation(projectId, id);
        int n = 0;
        for (Long id : ids) {
            RelationEntity entity = relationRepository.findById(id).orElse(null);
            if (entity == null) continue;
            entity.setStatus("IGNORED");
            relationRepository.save(entity);
            n++;
        }
        return n;
    }

    @Transactional
    public int confirmRelations(Long projectId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        for (Long id : ids) requireRelation(projectId, id);
        int n = 0;
        for (Long id : ids) {
            if (!relationRepository.existsById(id)) continue;
            try {
                confirmRelation(projectId, id, false);
                n++;
            } catch (IllegalArgumentException ignored) {
                // Skip relations that fail value match during batch confirm.
            }
        }
        return n;
    }

    @Transactional
    public void deleteRelation(Long projectId, Long id) {
        requireRelation(projectId, id);
        relationRepository.deleteById(id);
    }

    private TableMetaEntity requireActiveTable(AnalysisWorkspaceEntity ws, Long tableId) {
        TableMetaEntity table = tableMetaRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        if (!java.util.Objects.equals(ws.getActiveSnapshotId(), table.getSnapshotId())) {
            throw new IllegalArgumentException("表不属于当前项目的活动快照，请刷新后重试");
        }
        return table;
    }

    private void requireActiveColumn(AnalysisWorkspaceEntity ws, Long columnId) {
        ColumnMetaEntity column = columnMetaRepository.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found"));
        requireActiveTable(ws, column.getTableId());
    }

    private RelationEntity requireRelation(Long projectId, Long id) {
        RelationEntity relation = relationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Relation not found"));
        if (!requireWorkspace(projectId).getId().equals(relation.getWorkspaceId())) {
            throw new IllegalArgumentException("关系不属于当前项目");
        }
        return relation;
    }

    private RelationView toView(RelationEntity entity) {
        TableMetaEntity sourceTable = tableMetaRepository.findById(entity.getSourceTableId()).orElse(null);
        TableMetaEntity targetTable = tableMetaRepository.findById(entity.getTargetTableId()).orElse(null);
        ColumnMetaEntity sourceColumn = columnMetaRepository.findById(entity.getSourceColumnId()).orElse(null);
        ColumnMetaEntity targetColumn = columnMetaRepository.findById(entity.getTargetColumnId()).orElse(null);
        return new RelationView(
                entity.getId(), entity.getSourceTableId(), entity.getSourceColumnId(),
                entity.getTargetTableId(), entity.getTargetColumnId(),
                sourceTable != null ? sourceTable.getTableName() : null,
                sourceColumn != null ? sourceColumn.getColumnName() : null,
                targetTable != null ? targetTable.getTableName() : null,
                targetColumn != null ? targetColumn.getColumnName() : null,
                entity.getRelationType(), entity.getSourceKind(), entity.getStatus(), entity.getConfidence(), entity.getRemark()
        );
    }

    private Integer resolveSampleSize(Long projectId, Integer override) {
        if (override != null) return override;
        return discoveryRulesService.getRules(projectId).resolvedSampleSize();
    }

    private Double resolveMinInclusion(Long projectId, Double override) {
        if (override != null) return override;
        return discoveryRulesService.getRules(projectId).resolvedMinInclusion();
    }
}
