package com.dbvein.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {
    List<SnapshotEntity> findByProjectIdOrderByPulledAtDesc(Long projectId);
}
