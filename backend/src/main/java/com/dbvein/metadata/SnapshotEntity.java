package com.dbvein.metadata;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "snapshot")
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long dataSourceId;

    private String label;

    private Instant pulledAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getPulledAt() { return pulledAt; }
    public void setPulledAt(Instant pulledAt) { this.pulledAt = pulledAt; }
}
