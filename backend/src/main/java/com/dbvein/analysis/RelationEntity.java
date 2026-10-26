package com.dbvein.analysis;

import jakarta.persistence.*;

@Entity
@Table(name = "relation")
public class RelationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    private Long sourceTableId;
    private Long sourceColumnId;
    private Long targetTableId;
    private Long targetColumnId;

    @Column(nullable = false)
    private String relationType;

    @Column(nullable = false)
    private String sourceKind;

    @Column(nullable = false)
    private String status = "CONFIRMED";

    private Integer confidence;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Long getSourceTableId() { return sourceTableId; }
    public void setSourceTableId(Long sourceTableId) { this.sourceTableId = sourceTableId; }
    public Long getSourceColumnId() { return sourceColumnId; }
    public void setSourceColumnId(Long sourceColumnId) { this.sourceColumnId = sourceColumnId; }
    public Long getTargetTableId() { return targetTableId; }
    public void setTargetTableId(Long targetTableId) { this.targetTableId = targetTableId; }
    public Long getTargetColumnId() { return targetColumnId; }
    public void setTargetColumnId(Long targetColumnId) { this.targetColumnId = targetColumnId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
