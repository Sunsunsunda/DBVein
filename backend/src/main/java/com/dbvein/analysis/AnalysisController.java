package com.dbvein.analysis;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final RelationDiscoverService discoverService;
    private final DiscoveryRulesService discoveryRulesService;

    public AnalysisController(AnalysisService analysisService, RelationDiscoverService discoverService,
                              DiscoveryRulesService discoveryRulesService) {
        this.analysisService = analysisService;
        this.discoverService = discoverService;
        this.discoveryRulesService = discoveryRulesService;
    }

    @GetMapping("/canvas")
    public List<CanvasNodeEntity> getCanvas(@PathVariable Long projectId) {
        return analysisService.getCanvas(projectId);
    }

    @PutMapping("/canvas")
    public List<CanvasNodeEntity> saveCanvas(@PathVariable Long projectId, @RequestBody List<CanvasNodeRequest> nodes) {
        return analysisService.saveCanvas(projectId, nodes);
    }

    @PostMapping("/canvas/tables")
    public CanvasNodeEntity addTable(@PathVariable Long projectId, @RequestBody AddTableRequest request) {
        return analysisService.addTableToCanvas(projectId, request);
    }

    @DeleteMapping("/canvas/tables/{tableId}")
    public void removeTable(@PathVariable Long projectId, @PathVariable Long tableId) {
        analysisService.removeTableFromCanvas(projectId, tableId);
    }

    @GetMapping("/relations")
    public List<RelationView> relations(@PathVariable Long projectId) {
        return analysisService.listRelations(projectId);
    }

    @PostMapping("/relations")
    public RelationView createRelation(@PathVariable Long projectId, @RequestBody RelationRequest request) {
        return analysisService.createRelation(projectId, request);
    }

    @PostMapping("/relations/validate")
    public ValueMatchResult validateRelation(
            @PathVariable Long projectId,
            @RequestBody ValidateRelationRequest request) {
        return analysisService.validateRelation(projectId, request);
    }

    @PostMapping("/relations/discover")
    public DiscoverRelationsResponse discover(
            @PathVariable Long projectId,
            @RequestBody(required = false) DiscoverRelationsRequest request) {
        DiscoverRelationsRequest req = request != null
                ? request
                : new DiscoverRelationsRequest("canvas", null, 200, null, null);
        return discoverService.discover(projectId, req);
    }

    @GetMapping("/discovery-rules")
    public DiscoveryRules getDiscoveryRules(@PathVariable Long projectId) {
        return discoveryRulesService.getRules(projectId);
    }

    @PutMapping("/discovery-rules")
    public DiscoveryRules saveDiscoveryRules(@PathVariable Long projectId, @RequestBody DiscoveryRules rules) {
        return discoveryRulesService.saveRules(projectId, rules);
    }

    @GetMapping("/discovery-templates")
    public java.util.Map<String, Object> discoveryTemplates(@PathVariable Long projectId) {
        return discoveryRulesService.listTemplates();
    }

    @PostMapping("/discovery-rules/apply-template")
    public DiscoveryRules applyDiscoveryTemplate(
            @PathVariable Long projectId,
            @RequestBody java.util.Map<String, String> body) {
        return discoveryRulesService.applyTemplate(projectId, body.get("templateId"));
    }

    @PutMapping("/relations/{id}/confirm")
    public RelationView confirmRelation(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        return analysisService.confirmRelation(projectId, id, force);
    }

    @PutMapping("/relations/{id}/ignore")
    public RelationView ignoreRelation(@PathVariable Long projectId, @PathVariable Long id) {
        return analysisService.ignoreRelation(projectId, id);
    }

    @PostMapping("/relations/batch-ignore")
    public java.util.Map<String, Object> batchIgnore(
            @PathVariable Long projectId, @RequestBody BatchRelationRequest request) {
        int n = analysisService.ignoreRelations(projectId, request.ids());
        return java.util.Map.of("updated", n);
    }

    @PostMapping("/relations/batch-confirm")
    public java.util.Map<String, Object> batchConfirm(
            @PathVariable Long projectId, @RequestBody BatchRelationRequest request) {
        int n = analysisService.confirmRelations(projectId, request.ids());
        return java.util.Map.of("updated", n);
    }

    @DeleteMapping("/relations/{id}")
    public void deleteRelation(@PathVariable Long projectId, @PathVariable Long id) {
        analysisService.deleteRelation(projectId, id);
    }
}
