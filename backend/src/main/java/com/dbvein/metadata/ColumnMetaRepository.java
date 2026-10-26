package com.dbvein.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ColumnMetaRepository extends JpaRepository<ColumnMetaEntity, Long> {
    List<ColumnMetaEntity> findByTableIdOrderByOrdinalPositionAsc(Long tableId);
    Optional<ColumnMetaEntity> findByTableIdAndColumnName(Long tableId, String columnName);
    void deleteByTableId(Long tableId);
}
