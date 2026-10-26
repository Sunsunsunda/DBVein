package com.dbvein.project;

import com.dbvein.analysis.AnalysisWorkspaceBootstrap;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AnalysisWorkspaceBootstrap workspaceBootstrap;

    public ProjectService(ProjectRepository projectRepository, AnalysisWorkspaceBootstrap workspaceBootstrap) {
        this.projectRepository = projectRepository;
        this.workspaceBootstrap = workspaceBootstrap;
    }

    public List<ProjectEntity> list() {
        return projectRepository.findAll();
    }

    public ProjectEntity get(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
    }

    public ProjectEntity create(ProjectRequest request) {
        ProjectEntity entity = new ProjectEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSourceDataSourceId(request.sourceDataSourceId());
        ProjectEntity saved = projectRepository.save(entity);
        workspaceBootstrap.ensureWorkspace(saved.getId());
        return saved;
    }

    public ProjectEntity update(Long id, ProjectRequest request) {
        ProjectEntity entity = get(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setSourceDataSourceId(request.sourceDataSourceId());
        return projectRepository.save(entity);
    }

    public void delete(Long id) {
        projectRepository.deleteById(id);
    }
}
