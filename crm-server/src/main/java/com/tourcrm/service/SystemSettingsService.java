package com.tourcrm.service;

import com.tourcrm.dto.SystemSettingsRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class SystemSettingsService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final SystemSettingsRepository systemSettingsRepository;

    public SystemSettingsService(
            AuthService authService,
            SystemSettingsRepository systemSettingsRepository
    ) {
        this.authService = authService;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    public SystemSettingsRecord get(String token) {
        authService.requireAdminUser(token);
        return read();
    }

    public SystemSettingsRecord getForSystem() {
        return read();
    }

    public SystemSettingsRecord save(SystemSettingsRecord request, String token) {
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
        Optional<SystemSettingsRecord> databaseSettings = systemSettingsRepository.readSystemSettings();
        if (databaseSettings.isEmpty()) {
            return emptySettings();
        }
        SystemSettingsRecord settings = databaseSettings.get();
        return new SystemSettingsRecord(
                clean(settings.ocrAppCode()),
                clean(settings.ocrAppSecret()),
                clean(settings.remark()),
                clean(settings.updatedAt())
        );
    }

    private void write(SystemSettingsRecord settings) {
        systemSettingsRepository.writeSystemSettings(settings);
    }

    private SystemSettingsRecord emptySettings() {
        return new SystemSettingsRecord("", "", "", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
