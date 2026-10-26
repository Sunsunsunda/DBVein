package com.dbvein.analysis;

import com.dbvein.metadata.ColumnMetaEntity;
import com.dbvein.metadata.ColumnMetaRepository;
import com.dbvein.metadata.MetadataService;
import com.dbvein.metadata.TableMetaEntity;
import com.dbvein.metadata.TableMetaRepository;
import com.dbvein.project.AnalysisWorkspaceEntity;
import com.dbvein.project.AnalysisWorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Relation discovery driven primarily by sampled value inclusion (child ⊆ parent).
 * Naming / project discovery rules only boost or filter; they are not required.
 */
@Service
public class RelationDiscoverService {

    private static final Logger log = LoggerFactory.getLogger(RelationDiscoverService.class);
    private static final int MAX_PAIRS_PER_TABLE_PAIR = 80;

    private final AnalysisWorkspaceRepository workspaceRepository;
    private final CanvasNodeRepository canvasNodeRepository;
    private final RelationRepository relationRepository;
    private final TableMetaRepository tableMetaRepository;
    private final ColumnMetaRepository columnMetaRepository;
    private final DiscoveryRulesService discoveryRulesService;
    private final MetadataService metadataService;

    public RelationDiscoverService(AnalysisWorkspaceRepository workspaceRepository,
                                   CanvasNodeRepository canvasNodeRepository,
                                   RelationRepository relationRepository,
                                   TableMetaRepository tableMetaRepository,
                                   ColumnMetaRepository columnMetaRepository,
                                   DiscoveryRulesService discoveryRulesService,
                                   MetadataService metadataService) {
        this.workspaceRepository = workspaceRepository;
        this.canvasNodeRepository = canvasNodeRepository;
        this.relationRepository = relationRepository;
        this.tableMetaRepository = tableMetaRepository;
        this.columnMetaRepository = columnMetaRepository;
        this.discoveryRulesService = discoveryRulesService;
        this.metadataService = metadataService;
    }

    @Transactional
    public DiscoverRelationsResponse discover(Long projectId, DiscoverRelationsRequest request) {
        AnalysisWorkspaceEntity ws = workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        if (ws.getActiveSnapshotId() == null) {
            throw new IllegalArgumentException("No snapshot pulled yet");
        }

        DiscoveryRules rules = discoveryRulesService.getRules(projectId);
        String scope = request.scope() == null ? "canvas" : request.scope();
        if (!"canvas".equalsIgnoreCase(scope)) {
            // Full-snapshot value matching is too expensive for v1.
            throw new IllegalArgumentException("当前仅支持画布范围的数据值关系发现（scope=canvas）");
        }

        int minConfidence = request.minConfidence() != null
                ? Math.max(40, Math.min(100, request.minConfidence()))
                : Math.max(40, Math.min(100, rules.getMinConfidence() <= 0 ? 55 : rules.getMinConfidence()));
        int maxResults = request.maxResults() == null ? 200 : Math.max(1, Math.min(1000, request.maxResults()));
        int sampleSize = request.sampleSize() == null
                ? rules.resolvedSampleSize()
                : Math.max(50, Math.min(10000, request.sampleSize()));
        double minInclusion = request.minInclusion() == null
                ? rules.resolvedMinInclusion()
                : Math.max(0.5, Math.min(1.0, request.minInclusion()));

        List<TableMetaEntity> tables = resolveTables(ws);
        if (tables.size() < 2) {
            throw new IllegalArgumentException("画布上至少需要 2 张表才能发现关系");
        }

        Map<Long, List<ColumnMetaEntity>> columnsByTable = new HashMap<>();
        for (TableMetaEntity t : tables) {
            columnsByTable.put(t.getId(), columnMetaRepository.findByTableIdOrderByOrdinalPositionAsc(t.getId()));
        }

        Set<String> existingPairs = new HashSet<>();
        for (RelationEntity r : relationRepository.findByWorkspaceId(ws.getId())) {
            existingPairs.add(pairKey(r.getSourceColumnId(), r.getTargetColumnId()));
            existingPairs.add(pairKey(r.getTargetColumnId(), r.getSourceColumnId()));
        }

        Set<String> redundantKeys = upperSet(rules.getRedundantKeys());
        Set<String> businessKeys = upperSet(rules.getBusinessKeys());

        Map<Long, ColumnSample> samples = sampleColumns(tables, columnsByTable, sampleSize);

        List<ScoredPair> scored = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            for (int j = i + 1; j < tables.size(); j++) {
                TableMetaEntity a = tables.get(i);
                TableMetaEntity b = tables.get(j);
                scoreTablePairByValues(
                        a, b,
                        columnsByTable.get(a.getId()), columnsByTable.get(b.getId()),
                        samples, rules, redundantKeys, businessKeys, minInclusion, scored);
            }
        }

        scored.sort(Comparator.comparingInt(ScoredPair::score).reversed());

        int created = 0;
        int skipped = 0;
        int candidates = 0;
        List<RelationEntity> createdEntities = new ArrayList<>();

        for (ScoredPair pair : scored) {
            if (pair.score() < minConfidence) continue;
            candidates++;
            if (existingPairs.contains(pairKey(pair.sourceColumn().getId(), pair.targetColumn().getId()))) {
                skipped++;
                continue;
            }
            if (created >= maxResults) break;

            RelationEntity entity = new RelationEntity();
            entity.setWorkspaceId(ws.getId());
            entity.setSourceTableId(pair.sourceTable().getId());
            entity.setSourceColumnId(pair.sourceColumn().getId());
            entity.setTargetTableId(pair.targetTable().getId());
            entity.setTargetColumnId(pair.targetColumn().getId());
            entity.setRelationType("INFERRED");
            entity.setSourceKind("AUTO_ANALYSIS");
            entity.setStatus("PENDING");
            entity.setConfidence(pair.score());
            entity.setRemark(pair.reason());
            createdEntities.add(relationRepository.save(entity));
            existingPairs.add(pairKey(pair.sourceColumn().getId(), pair.targetColumn().getId()));
            created++;
        }

        List<RelationView> views = createdEntities.stream().map(this::toView).toList();
        return new DiscoverRelationsResponse(tables.size(), candidates, created, skipped, views);
    }

    private Map<Long, ColumnSample> sampleColumns(List<TableMetaEntity> tables,
                                                  Map<Long, List<ColumnMetaEntity>> columnsByTable,
                                                  int sampleSize) {
        Map<Long, ColumnSample> samples = new HashMap<>();
        for (TableMetaEntity table : tables) {
            List<ColumnMetaEntity> comparable = columnsByTable.getOrDefault(table.getId(), List.of()).stream()
                    .filter(c -> isComparableType(c.getDataType()))
                    .toList();
            if (comparable.isEmpty()) continue;
            try {
                List<String> names = comparable.stream().map(ColumnMetaEntity::getColumnName).toList();
                Map<String, List<String>> byName = metadataService.sampleDistinctColumns(
                        table.getId(), names, sampleSize);
                for (ColumnMetaEntity col : comparable) {
                    List<String> values = byName.getOrDefault(col.getColumnName(), List.of());
                    if (values.size() <= 1) continue;
                    Set<String> set = new LinkedHashSet<>(values);
                    samples.put(col.getId(), new ColumnSample(col, set, set.size()));
                }
            } catch (Exception e) {
                log.warn("Skip sampling table {}: {}", table.getTableName(), e.getMessage());
            }
        }
        return samples;
    }

    private void scoreTablePairByValues(TableMetaEntity tableA, TableMetaEntity tableB,
                                        List<ColumnMetaEntity> colsA, List<ColumnMetaEntity> colsB,
                                        Map<Long, ColumnSample> samples,
                                        DiscoveryRules rules, Set<String> redundantKeys, Set<String> businessKeys,
                                        double minInclusion, List<ScoredPair> out) {
        String nameA = NameNormalizer.normalize(tableA.getTableName());
        String nameB = NameNormalizer.normalize(tableB.getTableName());
        String kindA = tableKind(nameA, rules);
        String kindB = tableKind(nameB, rules);
        Set<String> namesA = columnNameSet(colsA);
        Set<String> namesB = columnNameSet(colsB);

        List<ColumnMetaEntity> usableA = colsA.stream()
                .filter(c -> samples.containsKey(c.getId()))
                .toList();
        List<ColumnMetaEntity> usableB = colsB.stream()
                .filter(c -> samples.containsKey(c.getId()))
                .toList();
        if (usableA.isEmpty() || usableB.isEmpty()) {
            return;
        }

        List<CandidatePair> candidates = new ArrayList<>();
        for (ColumnMetaEntity ca : usableA) {
            ColumnSample sa = samples.get(ca.getId());
            String na = NameNormalizer.normalize(ca.getColumnName());
            for (ColumnMetaEntity cb : usableB) {
                ColumnSample sb = samples.get(cb.getId());
                String nb = NameNormalizer.normalize(cb.getColumnName());
                if (!typesCompatible(ca.getDataType(), cb.getDataType())) {
                    continue;
                }
                if (na.equals(nb) && redundantKeys.contains(na)) {
                    continue;
                }
                if (isRedundantByHierarchy(rules, kindA, kindB, na, nb, namesA, namesB)) {
                    continue;
                }

                int priority = 0;
                if (ca.isPrimaryKey() || cb.isPrimaryKey()) priority += 100;
                if (businessKeys.contains(na) || businessKeys.contains(nb)) priority += 40;
                if (na.equals(nb) && !na.isEmpty()) priority += 20;
                // Prefer pairs where one side looks unique-ish (parent-like)
                priority += Math.min(sa.distinctCount(), sb.distinctCount()) > 5 ? 5 : 0;
                candidates.add(new CandidatePair(ca, sa, na, cb, sb, nb, priority));
            }
        }

        candidates.sort(Comparator.comparingInt(CandidatePair::priority).reversed());
        if (candidates.size() > MAX_PAIRS_PER_TABLE_PAIR) {
            candidates = candidates.subList(0, MAX_PAIRS_PER_TABLE_PAIR);
        }

        for (CandidatePair cand : candidates) {
            ScoredPair pair = scoreInclusion(
                    tableA, cand.colA(), cand.sampleA(), cand.nameA(), kindA,
                    tableB, cand.colB(), cand.sampleB(), cand.nameB(), kindB,
                    rules, businessKeys, minInclusion);
            if (pair != null) {
                out.add(pair);
            }
        }
    }

    private ScoredPair scoreInclusion(TableMetaEntity tableA, ColumnMetaEntity colA, ColumnSample sampleA, String na, String kindA,
                                      TableMetaEntity tableB, ColumnMetaEntity colB, ColumnSample sampleB, String nb, String kindB,
                                      DiscoveryRules rules, Set<String> businessKeys, double minInclusion) {
        // Try both orientations; keep the better inclusion as child → parent.
        Inclusion ab = inclusion(sampleA.values(), sampleB.values()); // A child, B parent
        Inclusion ba = inclusion(sampleB.values(), sampleA.values()); // B child, A parent

        // Shared enum / denormalized attribute: both sides nearly the same value set.
        // e.g. ACCSTATE, BUILDTYPE on REGION/BLD/HOUSE — not a FK.
        boolean nearSymmetric = ab.ratio() >= minInclusion && ba.ratio() >= minInclusion;
        boolean eitherPk = colA.isPrimaryKey() || colB.isPrimaryKey();
        if (nearSymmetric && !eitherPk) {
            return null;
        }

        boolean preferAb;
        if (nearSymmetric) {
            preferAb = preferAsParent(colB, sampleB, colA, sampleA);
        } else if (ab.ratio() >= minInclusion) {
            preferAb = true;
        } else if (ba.ratio() >= minInclusion) {
            preferAb = false;
        } else {
            return null;
        }

        TableMetaEntity childTable;
        ColumnMetaEntity childCol;
        ColumnSample childSample;
        String childName;
        String childKind;
        TableMetaEntity parentTable;
        ColumnMetaEntity parentCol;
        ColumnSample parentSample;
        String parentName;
        String parentKind;
        Inclusion hit;

        if (preferAb) {
            childTable = tableA; childCol = colA; childSample = sampleA; childName = na; childKind = kindA;
            parentTable = tableB; parentCol = colB; parentSample = sampleB; parentName = nb; parentKind = kindB;
            hit = ab;
        } else {
            childTable = tableB; childCol = colB; childSample = sampleB; childName = nb; childKind = kindB;
            parentTable = tableA; parentCol = colA; parentSample = sampleA; parentName = na; parentKind = kindA;
            hit = ba;
        }

        // True FK-like: child value set is mostly in parent, and sets are not tiny shared codes.
        int minDistinct = parentCol.isPrimaryKey() ? 3 : 15;
        if (childSample.distinctCount() < minDistinct || parentSample.distinctCount() < minDistinct) {
            return null;
        }

        // Parent should not be clearly smaller cardinality than child (anti reverse FK).
        if (parentSample.distinctCount() + 2 < childSample.distinctCount()
                && !parentCol.isPrimaryKey()
                && hit.ratio() < 0.98) {
            return null;
        }

        int score = (int) Math.round(55 * hit.ratio());
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format(Locale.ROOT, "值包含率 %d%% (%d/%d)",
                Math.round(hit.ratio() * 100), hit.hits(), hit.childSize()));

        if (typesCompatible(childCol.getDataType(), parentCol.getDataType())) {
            score += 15;
            reasons.add("类型兼容");
        }
        if (parentCol.isPrimaryKey()) {
            score += 18;
            reasons.add("父侧为主键");
        } else if (parentSample.distinctCount() >= Math.min(50, (int) (childSample.distinctCount() * 0.9))) {
            score += 10;
            reasons.add("父侧基数较高");
        }

        // Optional naming / hierarchy boost — never required for candidates.
        Integer hierarchyBoost = hierarchyJoinBoost(rules, childKind, parentKind, childName, parentName);
        if (hierarchyBoost != null && childName.equals(parentName)) {
            score += 12;
            reasons.add("层级规则加权");
        } else if (childName.equals(parentName) && !childName.isEmpty() && !NameNormalizer.isWeakName(childName)) {
            score += 8;
            reasons.add("字段同名加权");
        } else if (businessKeys.contains(childName) && childName.equals(parentName)) {
            score += 8;
            reasons.add("业务键加权");
        }

        score = Math.min(100, score);
        if (score < 40) {
            return null;
        }

        return new ScoredPair(childTable, childCol, parentTable, parentCol, score, String.join("; ", reasons));
    }

    private boolean preferAsParent(ColumnMetaEntity asParent, ColumnSample parentSample,
                                   ColumnMetaEntity asChild, ColumnSample childSample) {
        if (asParent.isPrimaryKey() && !asChild.isPrimaryKey()) return true;
        if (asChild.isPrimaryKey() && !asParent.isPrimaryKey()) return false;
        return parentSample.distinctCount() >= childSample.distinctCount();
    }

    private Inclusion inclusion(Set<String> child, Set<String> parent) {
        if (child == null || child.isEmpty()) {
            return new Inclusion(0, 0, 0.0);
        }
        int hits = 0;
        for (String v : child) {
            if (parent.contains(v)) {
                hits++;
            }
        }
        return new Inclusion(hits, child.size(), (double) hits / child.size());
    }

    private boolean isComparableType(String type) {
        if (type == null) return true;
        String t = type.toUpperCase(Locale.ROOT);
        return !(t.contains("BLOB") || t.contains("CLOB") || t.contains("RAW")
                || t.contains("BINARY") || t.equals("LONG") || t.contains("XML") || t.contains("JSON"));
    }

    private Set<String> upperSet(List<String> list) {
        if (list == null) return Set.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private List<TableMetaEntity> resolveTables(AnalysisWorkspaceEntity ws) {
        List<CanvasNodeEntity> nodes = canvasNodeRepository.findByWorkspaceId(ws.getId());
        List<TableMetaEntity> tables = new ArrayList<>();
        for (CanvasNodeEntity node : nodes) {
            tableMetaRepository.findById(node.getTableId()).ifPresent(tables::add);
        }
        return tables;
    }

    private Set<String> columnNameSet(List<ColumnMetaEntity> cols) {
        Set<String> set = new HashSet<>();
        for (ColumnMetaEntity c : cols) {
            set.add(NameNormalizer.normalize(c.getColumnName()));
        }
        return set;
    }

    private String tableKind(String normalizedTableName, DiscoveryRules rules) {
        if (rules.getTableKinds() != null) {
            for (DiscoveryRules.TableKindRule rule : rules.getTableKinds()) {
                if (rule.getContains() == null || rule.getKind() == null) continue;
                String contains = rule.getContains().trim().toUpperCase(Locale.ROOT);
                if (!contains.isEmpty() && normalizedTableName.contains(contains)) {
                    return rule.getKind().trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        return "OTHER";
    }

    private boolean isRedundantByHierarchy(DiscoveryRules rules, String kindA, String kindB,
                                           String na, String nb, Set<String> namesA, Set<String> namesB) {
        if (!na.equals(nb) || rules.getHierarchy() == null) return false;
        String col = na;
        for (DiscoveryRules.HierarchyRule link : rules.getHierarchy()) {
            if (link.getJoinColumn() == null) continue;
            String join = link.getJoinColumn().trim().toUpperCase(Locale.ROOT);
            String parent = normKind(link.getParentKind());
            String child = normKind(link.getChildKind());
            if (!isPair(kindA, kindB, parent, child)) continue;
            Set<String> childNames = child.equals(kindA) ? namesA : namesB;
            if (childNames.contains(join) && link.getRedundantWhenPresent() != null) {
                for (String red : link.getRedundantWhenPresent()) {
                    if (red != null && col.equals(red.trim().toUpperCase(Locale.ROOT))) {
                        return true;
                    }
                }
            }
        }
        for (DiscoveryRules.HierarchyRule link : rules.getHierarchy()) {
            if (link.getRedundantWhenPresent() == null || link.getJoinColumn() == null) continue;
            String join = link.getJoinColumn().trim().toUpperCase(Locale.ROOT);
            String child = normKind(link.getChildKind());
            String parent = normKind(link.getParentKind());
            for (String red : link.getRedundantWhenPresent()) {
                if (red == null || !col.equals(red.trim().toUpperCase(Locale.ROOT))) continue;
                if (child.equals(kindA) && !parent.equals(kindB) && namesA.contains(join)) return true;
                if (child.equals(kindB) && !parent.equals(kindA) && namesB.contains(join)) return true;
            }
        }
        return false;
    }

    private String normKind(String k) {
        return k == null ? "" : k.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPair(String a, String b, String x, String y) {
        return (x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a));
    }

    private Integer hierarchyJoinBoost(DiscoveryRules rules, String kindChild, String kindParent, String na, String nb) {
        if (!na.equals(nb) || rules.getHierarchy() == null) return null;
        for (DiscoveryRules.HierarchyRule link : rules.getHierarchy()) {
            if (link.getJoinColumn() == null) continue;
            if (!link.getJoinColumn().trim().equalsIgnoreCase(na)) continue;
            if (normKind(link.getChildKind()).equals(kindChild)
                    && normKind(link.getParentKind()).equals(kindParent)) {
                return 20;
            }
        }
        return null;
    }

    private boolean typesCompatible(String a, String b) {
        String ta = categorize(a);
        String tb = categorize(b);
        return ta.equals(tb) || ta.equals("UNKNOWN") || tb.equals("UNKNOWN");
    }

    private String categorize(String type) {
        if (type == null) return "UNKNOWN";
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("CHAR") || t.contains("TEXT") || t.contains("CLOB") || t.contains("VARCHAR")) return "STRING";
        if (t.contains("NUMBER") || t.contains("NUMERIC") || t.contains("DECIMAL") || t.contains("INT")
                || t.contains("FLOAT") || t.contains("DOUBLE") || t.contains("LONG")) return "NUMBER";
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP")) return "DATE";
        if (t.contains("BLOB") || t.contains("BINARY") || t.contains("RAW")) return "BINARY";
        return "UNKNOWN";
    }

    private String pairKey(Long a, Long b) {
        return a + "->" + b;
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
                entity.getRelationType(), entity.getSourceKind(), entity.getStatus(),
                entity.getConfidence(), entity.getRemark()
        );
    }

    private record ColumnSample(ColumnMetaEntity column, Set<String> values, int distinctCount) {}

    private record CandidatePair(
            ColumnMetaEntity colA, ColumnSample sampleA, String nameA,
            ColumnMetaEntity colB, ColumnSample sampleB, String nameB,
            int priority
    ) {}

    private record Inclusion(int hits, int childSize, double ratio) {}

    private record ScoredPair(
            TableMetaEntity sourceTable,
            ColumnMetaEntity sourceColumn,
            TableMetaEntity targetTable,
            ColumnMetaEntity targetColumn,
            int score,
            String reason
    ) {}
}
