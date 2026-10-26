package com.dbvein.metadata;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final MetadataService metadataService;

    public TableController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/{tableId}")
    public TableDetailResponse detail(@PathVariable Long tableId) {
        return metadataService.getTableDetail(tableId);
    }

    @GetMapping("/{tableId}/preview")
    public TablePreviewResponse preview(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return metadataService.previewTable(tableId, page, size);
    }

    @GetMapping("/{tableId}/profile")
    public TableProfileResponse tableProfile(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "10000") int sampleSize) {
        return metadataService.profileTable(tableId, sampleSize);
    }

    @GetMapping("/{tableId}/columns/{columnId}/profile")
    public ColumnProfileResponse columnProfile(
            @PathVariable Long tableId,
            @PathVariable Long columnId,
            @RequestParam(defaultValue = "10000") int sampleSize) {
        return metadataService.profileColumn(tableId, columnId, sampleSize);
    }

    @PutMapping("/{tableId}")
    public TableMetaEntity update(@PathVariable Long tableId, @RequestBody UpdateTableRequest request) {
        return metadataService.updateTable(tableId, request);
    }

    @PutMapping("/{tableId}/columns/{columnId}")
    public ColumnMetaEntity updateColumn(
            @PathVariable Long tableId,
            @PathVariable Long columnId,
            @RequestBody UpdateColumnRequest request) {
        return metadataService.updateColumn(tableId, columnId, request);
    }

    @DeleteMapping("/{tableId}")
    public void delete(@PathVariable Long tableId) {
        metadataService.deleteTable(tableId);
    }

    @PostMapping("/batch-delete")
    public java.util.Map<String, Object> batchDelete(@RequestBody BatchDeleteTablesRequest request) {
        return metadataService.deleteTables(request.tableIds());
    }
}
