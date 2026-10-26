package com.dbvein.datasource;

import com.dbvein.common.AppConfig.PasswordCipher;
import com.dbvein.metadata.DatabaseAdapterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DataSourceService {

    private final com.dbvein.project.ProjectRepository projectRepository;
    private final DataSourceRepository repository;
    private final PasswordCipher passwordCipher;
    private final DatabaseAdapterRegistry adapterRegistry;

    public DataSourceService(DataSourceRepository repository, PasswordCipher passwordCipher,
                             DatabaseAdapterRegistry adapterRegistry, com.dbvein.project.ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.repository = repository;
        this.passwordCipher = passwordCipher;
        this.adapterRegistry = adapterRegistry;
    }

    public Map<String, Object> test(DataSourceRequest request) {
        DataSourceEntity probe = toEntity(request, null);
        try {
            adapterRegistry.testConnectionOrThrow(probe, request.password());
        } catch (Exception e) {
            String detail = rootMessage(e);
            throw new IllegalArgumentException("连接失败: " + detail);
        }
        return Map.of("success", true, "message", "Connection OK");
    }

    public Map<String, Object> testSaved(Long id, DataSourceRequest request) {
        DataSourceEntity saved = requireWithPassword(id);
        DataSourceEntity probe = request == null ? saved : toEntity(request, id);
        String password = request != null && request.password() != null && !request.password().isBlank()
                ? request.password() : saved.getPasswordEncrypted();
        try {
            adapterRegistry.testConnectionOrThrow(probe, password);
        } catch (Exception e) {
            throw new IllegalArgumentException("连接失败: " + rootMessage(e));
        }
        return Map.of("success", true, "message", "连接成功");
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : e.getMessage();
    }

    public DataSourceResponse create(DataSourceRequest request) {
        DataSourceEntity entity = toEntity(request, null);
        entity.setPasswordEncrypted(passwordCipher.encrypt(request.password()));
        return toResponse(repository.save(entity));
    }

    public DataSourceResponse update(Long id, DataSourceRequest request) {
        DataSourceEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DataSource not found"));
        apply(entity, request);
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordEncrypted(passwordCipher.encrypt(request.password()));
        }
        return toResponse(repository.save(entity));
    }

    public DataSourceResponse get(Long id) {
        return toResponse(require(id));
    }

    public List<DataSourceResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public void delete(Long id) {
        require(id);
        if (projectRepository.existsBySourceDataSourceId(id)) {
            throw new IllegalArgumentException("数据源正在被项目使用，请先解除关联或删除关联项目");
        }
        repository.deleteById(id);
    }

    /**
     * Returns a detached copy whose passwordEncrypted field holds the PLAIN password for JDBC.
     * Never mutates the persisted encrypted value in-memory on the managed entity (except healing).
     */
    @org.springframework.transaction.annotation.Transactional
    public DataSourceEntity requireWithPassword(Long id) {
        DataSourceEntity entity = require(id);
        String stored = entity.getPasswordEncrypted();
        String plain;
        if (passwordCipher.canDecrypt(stored)) {
            plain = passwordCipher.decrypt(stored);
        } else {
            // Likely corrupted by older bug that flushed plaintext into DB — heal it.
            plain = stored;
            entity.setPasswordEncrypted(passwordCipher.encrypt(plain));
            repository.save(entity);
        }
        return copyWithPlainPassword(entity, plain);
    }

    private DataSourceEntity copyWithPlainPassword(DataSourceEntity source, String plainPassword) {
        DataSourceEntity copy = new DataSourceEntity();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setHost(source.getHost());
        copy.setPort(source.getPort());
        copy.setDatabaseName(source.getDatabaseName());
        copy.setServiceName(source.getServiceName());
        copy.setUsername(source.getUsername());
        copy.setPasswordEncrypted(plainPassword);
        copy.setRole(source.getRole());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private DataSourceEntity require(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("DataSource not found"));
    }

    private DataSourceEntity toEntity(DataSourceRequest request, Long id) {
        DataSourceEntity entity = new DataSourceEntity();
        if (id != null) {
            entity.setId(id);
        }
        apply(entity, request);
        return entity;
    }

    private void apply(DataSourceEntity entity, DataSourceRequest request) {
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setHost(request.host());
        entity.setPort(request.port());
        entity.setDatabaseName(request.databaseName());
        entity.setServiceName(request.serviceName());
        entity.setUsername(request.username());
        entity.setRole(request.role() != null ? request.role() : "SOURCE");
    }

    private DataSourceResponse toResponse(DataSourceEntity entity) {
        return new DataSourceResponse(
                entity.getId(), entity.getName(), entity.getType(), entity.getHost(), entity.getPort(),
                entity.getDatabaseName(), entity.getServiceName(), entity.getUsername(), entity.getRole(), "******"
        );
    }
}
