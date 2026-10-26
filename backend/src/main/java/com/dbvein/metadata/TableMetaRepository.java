package com.dbvein.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TableMetaRepository extends JpaRepository<TableMetaEntity, Long> {
    List<TableMetaEntity> findBySnapshotIdOrderBySchemaNameAscTableNameAsc(Long snapshotId);
    List<TableMetaEntity> findBySnapshotIdAndSchemaNameOrderByTableNameAsc(Long snapshotId, String schemaName);
    Optional<TableMetaEntity> findBySnapshotIdAndSchemaNameAndTableName(Long snapshotId, String schemaName, String tableName);
}
