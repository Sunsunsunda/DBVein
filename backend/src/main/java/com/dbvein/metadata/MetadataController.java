package com.dbvein.metadata;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping("/snapshots/pull")
    public SnapshotEntity pull(@PathVariable Long projectId, @RequestBody(required = false) PullSnapshotRequest request) {
        PullSnapshotRequest req = request != null ? request : new PullSnapshotRequest(null, null);
        return metadataService.pullSnapshot(projectId, req);
    }

    @GetMapping("/source-schemas")
    public List<String> sourceSchemas(@PathVariable Long projectId) {
        return metadataService.listSourceSchemas(projectId);
    }

    @GetMapping("/schemas")
    public List<String> schemas(@PathVariable Long projectId) {
        return metadataService.listSchemas(projectId);
    }

    @GetMapping("/tables")
    public List<TableMetaEntity> tables(@PathVariable Long projectId, @RequestParam(required = false) String schema) {
        return metadataService.listTables(projectId, schema);
    }
}
