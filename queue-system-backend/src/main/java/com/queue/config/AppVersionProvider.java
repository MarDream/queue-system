package com.queue.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class AppVersionProvider {

    @Value("${app.version:}")
    private String configuredVersion;

    private String version = "dev";

    @PostConstruct
    public void init() {
        version = resolveVersion();
    }

    public String getVersion() {
        return version;
    }

    private String resolveVersion() {
        if (configuredVersion != null && !configuredVersion.isBlank()) {
            return configuredVersion.trim();
        }

        for (String candidate : List.of("VERSION", "../VERSION", "../../VERSION")) {
            String value = readVersionFile(Path.of(candidate));
            if (value != null) {
                return value;
            }
        }

        return "dev";
    }

    private String readVersionFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }

            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException ignored) {
            return null;
        }
    }
}
