package com.dbvein.project;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "analysis_workspace")
public class AnalysisWorkspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    private Long activeSnapshotId;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String discoveryRulesJson;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getActiveSnapshotId() { return activeSnapshotId; }
    public void setActiveSnapshotId(Long activeSnapshotId) { this.activeSnapshotId = activeSnapshotId; }
    public String getDiscoveryRulesJson() { return discoveryRulesJson; }
    public void setDiscoveryRulesJson(String discoveryRulesJson) { this.discoveryRulesJson = discoveryRulesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
