package com.dbvein.datasource;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody DataSourceRequest request) {
        return service.test(request);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> testSaved(@PathVariable Long id, @RequestBody(required = false) DataSourceRequest request) {
        return service.testSaved(id, request);
    }

    @PostMapping
    public DataSourceResponse create(@RequestBody DataSourceRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<DataSourceResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DataSourceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public DataSourceResponse update(@PathVariable Long id, @RequestBody DataSourceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
