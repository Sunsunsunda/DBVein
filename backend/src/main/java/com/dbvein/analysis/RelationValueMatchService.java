package com.dbvein.analysis;

import com.dbvein.metadata.ColumnMetaEntity;
import com.dbvein.metadata.ColumnMetaRepository;
import com.dbvein.metadata.MetadataService;
import com.dbvein.metadata.TableMetaEntity;
import com.dbvein.metadata.TableMetaRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RelationValueMatchService {

    private static final double DEFAULT_THRESHOLD = 0.85;
    private static final int DEFAULT_SAMPLE = 2000;

    private final ColumnMetaRepository columnMetaRepository;
    private final TableMetaRepository tableMetaRepository;
    private final MetadataService metadataService;

    public RelationValueMatchService(ColumnMetaRepository columnMetaRepository,
                                     TableMetaRepository tableMetaRepository,
                                     MetadataService metadataService) {
        this.columnMetaRepository = columnMetaRepository;
        this.tableMetaRepository = tableMetaRepository;
        this.metadataService = metadataService;
    }

    public ValueMatchResult match(Long sourceColumnId, Long targetColumnId, Integer sampleSize, Double threshold) {
        int size = sampleSize == null ? DEFAULT_SAMPLE : Math.max(50, Math.min(10000, sampleSize));
        double min = threshold == null ? DEFAULT_THRESHOLD : Math.max(0.5, Math.min(1.0, threshold));

        ColumnMetaEntity sourceCol = columnMetaRepository.findById(sourceColumnId)
                .orElseThrow(() -> new IllegalArgumentException("Source column not found"));
        ColumnMetaEntity targetCol = columnMetaRepository.findById(targetColumnId)
                .orElseThrow(() -> new IllegalArgumentException("Target column not found"));
        if (sourceCol.getId().equals(targetCol.getId())) {
            throw new IllegalArgumentException("不能将同一字段关联到自己");
        }

        TableMetaEntity sourceTable = tableMetaRepository.findById(sourceCol.getTableId())
                .orElseThrow(() -> new IllegalArgumentException("Source table not found"));
        TableMetaEntity targetTable = tableMetaRepository.findById(targetCol.getTableId())
                .orElseThrow(() -> new IllegalArgumentException("Target table not found"));

        Set<String> sourceValues;
        Set<String> targetValues;
        try {
            MetadataService.SamplePair pair = metadataService.sampleDistinctPair(
                    sourceTable.getId(), sourceCol.getColumnName(),
                    targetTable.getId(), targetCol.getColumnName(),
                    size);
            sourceValues = toSet(pair.first());
            targetValues = toSet(pair.second());
        } catch (Exception e) {
            throw new IllegalStateException("抽样校验失败: " + e.getMessage(), e);
        }

        if (sourceValues.isEmpty() || targetValues.isEmpty()) {
            String summary = String.format(Locale.ROOT,
                    "抽样无有效数据：%s.%s=%d 个值，%s.%s=%d 个值",
                    sourceTable.getTableName(), sourceCol.getColumnName(), sourceValues.size(),
                    targetTable.getTableName(), targetCol.getColumnName(), targetValues.size());
            return new ValueMatchResult(
                    sourceTable.getId(), sourceCol.getId(), targetTable.getId(), targetCol.getId(),
                    sourceTable.getTableName(), sourceCol.getColumnName(),
                    targetTable.getTableName(), targetCol.getColumnName(),
                    size, sourceValues.size(), targetValues.size(),
                    0, 0, 0, null, false, min, summary);
        }

        Set<String> sourceMatches = metadataService.findExistingValues(targetTable.getId(), targetCol.getColumnName(), sourceValues);
        Set<String> targetMatches = metadataService.findExistingValues(sourceTable.getId(), sourceCol.getColumnName(), targetValues);
        double sourceInTarget = inclusion(sourceValues, sourceMatches);
        double targetInSource = inclusion(targetValues, targetMatches);
        double best = selectedInclusion(sourceCol.isPrimaryKey(), targetCol.isPrimaryKey(), sourceInTarget, targetInSource);

        // Prefer FK-like direction: non-PK → PK, else higher inclusion as child→parent.
        String suggested;
        if (targetCol.isPrimaryKey() && !sourceCol.isPrimaryKey()) {
            suggested = sourceTable.getTableName() + "." + sourceCol.getColumnName()
                    + " → " + targetTable.getTableName() + "." + targetCol.getColumnName()
                    + String.format(Locale.ROOT, "（源→目标包含 %.0f%%）", sourceInTarget * 100);
        } else if (sourceCol.isPrimaryKey() && !targetCol.isPrimaryKey()) {
            suggested = targetTable.getTableName() + "." + targetCol.getColumnName()
                    + " → " + sourceTable.getTableName() + "." + sourceCol.getColumnName()
                    + String.format(Locale.ROOT, "（目标→源包含 %.0f%%）", targetInSource * 100);
        } else if (sourceInTarget >= targetInSource) {
            suggested = sourceTable.getTableName() + "." + sourceCol.getColumnName()
                    + " → " + targetTable.getTableName() + "." + targetCol.getColumnName()
                    + String.format(Locale.ROOT, "（源→目标包含 %.0f%%）", sourceInTarget * 100);
        } else {
            suggested = targetTable.getTableName() + "." + targetCol.getColumnName()
                    + " → " + sourceTable.getTableName() + "." + sourceCol.getColumnName()
                    + String.format(Locale.ROOT, "（目标→源包含 %.0f%%）", targetInSource * 100);
        }

        // With one primary key, validate the referencing column against that key.
        boolean passed = best >= min;
        String summary = String.format(Locale.ROOT,
                "%s.%s → %s.%s：源列抽样值在目标全表存在 %.0f%%（%d/%d），目标列抽样值在源全表存在 %.0f%%（%d/%d）%s",
                sourceTable.getTableName(), sourceCol.getColumnName(),
                targetTable.getTableName(), targetCol.getColumnName(),
                sourceInTarget * 100, sourceMatches.size(), sourceValues.size(),
                targetInSource * 100, targetMatches.size(), targetValues.size(),
                passed ? "，通过" : "，未通过阈值 " + Math.round(min * 100) + "%")
                + String.format(Locale.ROOT, "（单侧抽样上限 %d；对方全表查存在性）", size);

        summary += "；判定匹配率 " + Math.round(best * 100) + "%";
        if (sourceMatches.size() < sourceValues.size()) summary += "；源列未匹配示例：" + unmatched(sourceValues, sourceMatches);
        if (targetMatches.size() < targetValues.size()) summary += "；目标列未匹配示例：" + unmatched(targetValues, targetMatches);

        return new ValueMatchResult(
                sourceTable.getId(), sourceCol.getId(), targetTable.getId(), targetCol.getId(),
                sourceTable.getTableName(), sourceCol.getColumnName(),
                targetTable.getTableName(), targetCol.getColumnName(),
                size, sourceValues.size(), targetValues.size(),
                sourceInTarget, targetInSource, best, suggested, passed, min, summary);
    }

    static double selectedInclusion(boolean sourcePk, boolean targetPk, double forward, double reverse) {
        if (targetPk && !sourcePk) return forward;
        if (sourcePk && !targetPk) return reverse;
        return Math.max(forward, reverse);
    }

    private String unmatched(Set<String> values, Set<String> matched) {
        return values.stream().filter(value -> !matched.contains(value)).sorted().limit(5).toList().toString();
    }

    public int confidenceFrom(ValueMatchResult match) {
        return (int) Math.round(Math.min(100, match.bestInclusion() * 100));
    }

    public String remarkFrom(ValueMatchResult match) {
        return match.summary() + (match.suggestedDirection() != null ? "；建议方向：" + match.suggestedDirection() : "");
    }

    private Set<String> toSet(List<String> list) {
        Set<String> set = new HashSet<>();
        if (list != null) {
            for (String v : list) {
                if (v != null && !v.isBlank()) {
                    set.add(v.trim());
                }
            }
        }
        return set;
    }

    private double inclusion(Set<String> child, Set<String> parent) {
        if (child.isEmpty()) return 0;
        return (double) hits(child, parent) / child.size();
    }

    private int hits(Set<String> child, Set<String> parent) {
        int n = 0;
        for (String v : child) {
            if (parent.contains(v)) n++;
        }
        return n;
    }
}
