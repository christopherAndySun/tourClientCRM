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
    private final SystemAuditService systemAuditService;

    public SystemSettingsService(
            AuthService authService,
            SystemSettingsRepository systemSettingsRepository,
            SystemAuditService systemAuditService
    ) {
        this.authService = authService;
        this.systemSettingsRepository = systemSettingsRepository;
        this.systemAuditService = systemAuditService;
    }

    public SystemSettingsRecord get(String token) {
        authService.requireAdminUser(token);
        return maskSecret(read());
    }

    public SystemSettingsRecord getForSystem() {
        return read();
    }

    public SystemSettingsRecord save(SystemSettingsRecord request, String token) {
        authService.requireAdminUser(token);
        SystemSettingsRecord old = read();
        String nextSecret = clean(request.ocrAppSecret());
        if (isMaskedSecret(nextSecret) || nextSecret.isEmpty()) {
            nextSecret = old.ocrAppSecret();
        }
        SystemSettingsRecord updated = new SystemSettingsRecord(
                clean(request.ocrAppCode()),
                nextSecret,
                clean(request.remark()),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        write(updated);
        systemAuditService.record(token, "SETTINGS_UPDATE", "保存系统设置", "SYSTEM_SETTINGS", "OCR", "更新 OCR 配置");
        return maskSecret(updated);
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

    private SystemSettingsRecord maskSecret(SystemSettingsRecord settings) {
        return new SystemSettingsRecord(
                settings.ocrAppCode(),
                mask(settings.ocrAppSecret()),
                settings.remark(),
                settings.updatedAt()
        );
    }

    private String mask(String value) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        if (cleaned.length() <= 4) {
            return "****";
        }
        return cleaned.substring(0, 2) + "****" + cleaned.substring(cleaned.length() - 2);
    }

    private boolean isMaskedSecret(String value) {
        return value != null && value.contains("****");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
