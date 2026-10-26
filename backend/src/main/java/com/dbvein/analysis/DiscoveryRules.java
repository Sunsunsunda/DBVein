package com.dbvein.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Project-scoped relation discovery rules. Empty lists = pure generic scoring only.
 */
public class DiscoveryRules {

    /** Shared denormalized fields that should NOT be treated as join keys */
    private List<String> redundantKeys = new ArrayList<>();

    /** Preferred hierarchy joins: child table kind + parent kind + join column */
    private List<HierarchyRule> hierarchy = new ArrayList<>();

    /** Extra business keys that boost same-name matches */
    private List<String> businessKeys = new ArrayList<>();

    /** Table name contains → kind label, e.g. REGION, BLD */
    private List<TableKindRule> tableKinds = new ArrayList<>();

    private int minConfidence = 60;

    /** Distinct values sampled per column for discovery / link validation. */
    private int sampleSize = 2000;

    /** Minimum inclusion ratio 0..1 for value-match pass. */
    private double minInclusion = 0.85;

    public List<String> getRedundantKeys() { return redundantKeys; }
    public void setRedundantKeys(List<String> redundantKeys) { this.redundantKeys = redundantKeys != null ? redundantKeys : new ArrayList<>(); }
    public List<HierarchyRule> getHierarchy() { return hierarchy; }
    public void setHierarchy(List<HierarchyRule> hierarchy) { this.hierarchy = hierarchy != null ? hierarchy : new ArrayList<>(); }
    public List<String> getBusinessKeys() { return businessKeys; }
    public void setBusinessKeys(List<String> businessKeys) { this.businessKeys = businessKeys != null ? businessKeys : new ArrayList<>(); }
    public List<TableKindRule> getTableKinds() { return tableKinds; }
    public void setTableKinds(List<TableKindRule> tableKinds) { this.tableKinds = tableKinds != null ? tableKinds : new ArrayList<>(); }
    public int getMinConfidence() { return minConfidence; }
    public void setMinConfidence(int minConfidence) { this.minConfidence = minConfidence; }
    public int getSampleSize() { return sampleSize; }
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }
    public double getMinInclusion() { return minInclusion; }
    public void setMinInclusion(double minInclusion) { this.minInclusion = minInclusion; }

    /** Clamp sample size to a safe range. */
    public int resolvedSampleSize() {
        int s = sampleSize <= 0 ? 2000 : sampleSize;
        return Math.max(10, Math.min(10000, s));
    }

    public double resolvedMinInclusion() {
        double m = minInclusion <= 0 ? 0.85 : minInclusion;
        return Math.max(0.5, Math.min(1.0, m));
    }

    public static class HierarchyRule {
        private String parentKind;
        private String childKind;
        private String joinColumn;
        /** Ancestor columns on child that become redundant when joinColumn exists */
        private List<String> redundantWhenPresent = new ArrayList<>();

        public HierarchyRule() {}

        public HierarchyRule(String parentKind, String childKind, String joinColumn, List<String> redundantWhenPresent) {
            this.parentKind = parentKind;
            this.childKind = childKind;
            this.joinColumn = joinColumn;
            this.redundantWhenPresent = redundantWhenPresent != null ? redundantWhenPresent : new ArrayList<>();
        }

        public String getParentKind() { return parentKind; }
        public void setParentKind(String parentKind) { this.parentKind = parentKind; }
        public String getChildKind() { return childKind; }
        public void setChildKind(String childKind) { this.childKind = childKind; }
        public String getJoinColumn() { return joinColumn; }
        public void setJoinColumn(String joinColumn) { this.joinColumn = joinColumn; }
        public List<String> getRedundantWhenPresent() { return redundantWhenPresent; }
        public void setRedundantWhenPresent(List<String> redundantWhenPresent) {
            this.redundantWhenPresent = redundantWhenPresent != null ? redundantWhenPresent : new ArrayList<>();
        }
    }

    public static class TableKindRule {
        private String contains;
        private String kind;

        public TableKindRule() {}

        public TableKindRule(String contains, String kind) {
            this.contains = contains;
            this.kind = kind;
        }

        public String getContains() { return contains; }
        public void setContains(String contains) { this.contains = contains; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
    }

    public static DiscoveryRules empty() {
        return new DiscoveryRules();
    }

    /** Optional template for 维修资金基础库 — applied only when user chooses it */
    public static DiscoveryRules hfmpHousingTemplate() {
        DiscoveryRules r = new DiscoveryRules();
        r.setRedundantKeys(List.of(
                "ZONECODE", "COMPNO", "BANKCODE", "STATE", "ACCSTATE", "OLDSTATE",
                "CONFDATE", "CONFOPER", "RECDATE", "RECOPER", "REMARK", "HISFLAG"
        ));
        r.setBusinessKeys(List.of(
                "REGIONNO", "BLDNO", "CELLNO", "FLOORNO", "ROOMNO",
                "HOUSENO", "ACCNO", "BUSINO", "PROJNO", "ORDERNO", "REQNO"
        ));
        r.setTableKinds(List.of(
                new TableKindRule("REGION", "REGION"),
                new TableKindRule("BLD", "BLD"),
                new TableKindRule("BUILD", "BLD"),
                new TableKindRule("CELL", "CELL"),
                new TableKindRule("HOUSE", "HOUSE"),
                new TableKindRule("ROOM", "HOUSE"),
                new TableKindRule("ACC", "ACC")
        ));
        r.setHierarchy(List.of(
                new HierarchyRule("REGION", "BLD", "REGIONNO", List.of()),
                new HierarchyRule("BLD", "CELL", "BLDNO", List.of("REGIONNO")),
                new HierarchyRule("CELL", "HOUSE", "CELLNO", List.of("REGIONNO", "BLDNO")),
                new HierarchyRule("HOUSE", "ACC", "HOUSENO", List.of("REGIONNO", "BLDNO", "CELLNO")),
                new HierarchyRule("HOUSE", "ACC", "ROOMNO", List.of("REGIONNO", "BLDNO", "CELLNO"))
        ));
        r.setMinConfidence(60);
        r.setSampleSize(2000);
        r.setMinInclusion(0.85);
        return r;
    }
}
