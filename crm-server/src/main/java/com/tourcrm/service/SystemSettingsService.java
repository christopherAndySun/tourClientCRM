package com.tourcrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.dto.SystemSettingsRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SystemSettingsService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final Path dataFile;

    public SystemSettingsService(
            ObjectMapper objectMapper,
            AuthService authService,
            @Value("${app.system-settings-file:data/system-settings.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.dataFile = Path.of(dataFile);
    }

    public synchronized SystemSettingsRecord get(String token) {
        authService.requireAdminUser(token);
        return read();
    }

    public synchronized SystemSettingsRecord save(SystemSettingsRecord request, String token) {
        authService.requireAdminUser(token);
        SystemSettingsRecord updated = new SystemSettingsRecord(
                clean(request.ocrAppCode()),
                clean(request.ocrAppSecret()),
                clean(request.remark()),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        write(updated);
        return updated;
    }

    private SystemSettingsRecord read() {
        if (!Files.exists(dataFile)) {
            return emptySettings();
        }
        try {
            SystemSettingsRecord settings = objectMapper.readValue(dataFile.toFile(), SystemSettingsRecord.class);
            return new SystemSettingsRecord(
                    clean(settings.ocrAppCode()),
                    clean(settings.ocrAppSecret()),
                    clean(settings.remark()),
                    clean(settings.updatedAt())
            );
        } catch (IOException error) {
            throw new IllegalStateException("读取系统设置失败", error);
        }
    }

    private void write(SystemSettingsRecord settings) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), settings);
        } catch (IOException error) {
            throw new IllegalStateException("保存系统设置失败", error);
        }
    }

    private SystemSettingsRecord emptySettings() {
        return new SystemSettingsRecord("", "", "", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
