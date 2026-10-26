package com.dbvein.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RelationRepository extends JpaRepository<RelationEntity, Long> {
    List<RelationEntity> findByWorkspaceIdAndStatusNot(Long workspaceId, String status);
    List<RelationEntity> findByWorkspaceId(Long workspaceId);
    void deleteBySourceTableIdOrTargetTableId(Long sourceTableId, Long targetTableId);
}
