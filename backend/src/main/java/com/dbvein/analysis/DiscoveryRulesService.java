package com.dbvein.analysis;

import com.dbvein.project.AnalysisWorkspaceEntity;
import com.dbvein.project.AnalysisWorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiscoveryRulesService {

    private final AnalysisWorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    public DiscoveryRulesService(AnalysisWorkspaceRepository workspaceRepository, ObjectMapper objectMapper) {
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
    }

    public DiscoveryRules getRules(Long projectId) {
        AnalysisWorkspaceEntity ws = workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        return parse(ws.getDiscoveryRulesJson());
    }

    @Transactional
    public DiscoveryRules saveRules(Long projectId, DiscoveryRules rules) {
        AnalysisWorkspaceEntity ws = workspaceRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        try {
            ws.setDiscoveryRulesJson(objectMapper.writeValueAsString(rules != null ? rules : DiscoveryRules.empty()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize discovery rules", e);
        }
        workspaceRepository.save(ws);
        return getRules(projectId);
    }

    @Transactional
    public DiscoveryRules applyTemplate(Long projectId, String templateId) {
        DiscoveryRules rules = switch (templateId == null ? "" : templateId) {
            case "hfmp-housing" -> DiscoveryRules.hfmpHousingTemplate();
            case "empty", "generic", "" -> DiscoveryRules.empty();
            default -> throw new IllegalArgumentException("Unknown template: " + templateId);
        };
        return saveRules(projectId, rules);
    }

    public Map<String, Object> listTemplates() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templates", List.of(
                Map.of("id", "generic", "name", "无加权（仅数据）", "description", "只用抽样值包含率发现；不按业务名加权"),
                Map.of("id", "hfmp-housing", "name", "维修资金-房屋层级（可选加权）", "description", "在数据证据之上，对 REGION→BLD→CELL→HOUSE 等同名键加权，并过滤 ZONECODE 等冗余字段")
        ));
        return map;
    }

    public DiscoveryRules parse(String json) {
        if (json == null || json.isBlank()) {
            return DiscoveryRules.empty();
        }
        try {
            return objectMapper.readValue(json, DiscoveryRules.class);
        } catch (Exception e) {
            return DiscoveryRules.empty();
        }
    }
}
