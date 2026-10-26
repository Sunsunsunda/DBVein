package com.dbvein.analysis;

import com.dbvein.project.AnalysisWorkspaceEntity;
import com.dbvein.project.AnalysisWorkspaceRepository;
import org.springframework.stereotype.Component;

@Component
public class AnalysisWorkspaceBootstrap {

    private final AnalysisWorkspaceRepository workspaceRepository;

    public AnalysisWorkspaceBootstrap(AnalysisWorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public AnalysisWorkspaceEntity ensureWorkspace(Long projectId) {
        return workspaceRepository.findByProjectId(projectId).orElseGet(() -> {
            AnalysisWorkspaceEntity ws = new AnalysisWorkspaceEntity();
            ws.setProjectId(projectId);
            return workspaceRepository.save(ws);
        });
    }
}
