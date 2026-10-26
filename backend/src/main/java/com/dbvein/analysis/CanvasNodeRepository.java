package com.dbvein.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanvasNodeRepository extends JpaRepository<CanvasNodeEntity, Long> {
    List<CanvasNodeEntity> findByWorkspaceId(Long workspaceId);
    Optional<CanvasNodeEntity> findByWorkspaceIdAndTableId(Long workspaceId, Long tableId);
    void deleteByWorkspaceIdAndTableId(Long workspaceId, Long tableId);
}
