package com.dbvein.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

public class DataDirEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dir = environment.getProperty("dbvein.data-dir", "./data");
        try {
            Files.createDirectories(Path.of(dir));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create data directory: " + dir, e);
        }
    }
}
