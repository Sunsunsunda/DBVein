package com.dbvein.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DataDirectoryInitializer {

    @Value("${dbvein.data-dir:./data}")
    private String dataDir;

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(Path.of(dataDir));
    }
}
