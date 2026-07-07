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
    private final SecretCryptoService secretCryptoService;

    public SystemSettingsService(
            AuthService authService,
            SystemSettingsRepository systemSettingsRepository,
            SystemAuditService systemAuditService,
            SecretCryptoService secretCryptoService
    ) {
        this.authService = authService;
        this.systemSettingsRepository = systemSettingsRepository;
        this.systemAuditService = systemAuditService;
        this.secretCryptoService = secretCryptoService;
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
        String nextSecret = nextSensitiveValue(request.ocrAppSecret(), old.ocrAppSecret());
        String nextHqWebhook = nextSensitiveValue(request.dingtalkHqClueWebhook(), old.dingtalkHqClueWebhook());
        String nextBranchWebhook = nextSensitiveValue(request.dingtalkBranchClueWebhook(), old.dingtalkBranchClueWebhook());
        String updatedAt = LocalDateTime.now().format(DATE_TIME_FORMAT);
        SystemSettingsRecord stored = new SystemSettingsRecord(
                clean(request.ocrAppCode()),
                secretCryptoService.encrypt(nextSecret),
                secretCryptoService.encrypt(nextHqWebhook),
                Boolean.TRUE.equals(request.dingtalkHqClueEnabled()),
                secretCryptoService.encrypt(nextBranchWebhook),
                Boolean.TRUE.equals(request.dingtalkBranchClueEnabled()),
                clean(request.remark()),
                updatedAt
        );
        write(stored);
        systemAuditService.record(token, "SETTINGS_UPDATE", "保存系统设置", "SYSTEM_SETTINGS", "SYSTEM", "更新 OCR / 钉钉机器人配置");
        return maskSecret(new SystemSettingsRecord(
                clean(request.ocrAppCode()),
                nextSecret,
                nextHqWebhook,
                Boolean.TRUE.equals(request.dingtalkHqClueEnabled()),
                nextBranchWebhook,
                Boolean.TRUE.equals(request.dingtalkBranchClueEnabled()),
                clean(request.remark()),
                updatedAt
        ));
    }

    private SystemSettingsRecord read() {
        Optional<SystemSettingsRecord> databaseSettings = systemSettingsRepository.readSystemSettings();
        if (databaseSettings.isEmpty()) {
            return emptySettings();
        }
        SystemSettingsRecord settings = databaseSettings.get();
        String rawSecret = clean(settings.ocrAppSecret());
        String decryptedSecret = secretCryptoService.decrypt(rawSecret);
        String rawHqWebhook = clean(settings.dingtalkHqClueWebhook());
        String decryptedHqWebhook = secretCryptoService.decrypt(rawHqWebhook);
        String rawBranchWebhook = clean(settings.dingtalkBranchClueWebhook());
        String decryptedBranchWebhook = secretCryptoService.decrypt(rawBranchWebhook);
        if (needsEncrypt(rawSecret) || needsEncrypt(rawHqWebhook) || needsEncrypt(rawBranchWebhook)) {
            write(new SystemSettingsRecord(
                    clean(settings.ocrAppCode()),
                    secretCryptoService.encrypt(decryptedSecret),
                    secretCryptoService.encrypt(decryptedHqWebhook),
                    Boolean.TRUE.equals(settings.dingtalkHqClueEnabled()),
                    secretCryptoService.encrypt(decryptedBranchWebhook),
                    Boolean.TRUE.equals(settings.dingtalkBranchClueEnabled()),
                    clean(settings.remark()),
                    clean(settings.updatedAt())
            ));
        }
        return new SystemSettingsRecord(
                clean(settings.ocrAppCode()),
                decryptedSecret,
                decryptedHqWebhook,
                Boolean.TRUE.equals(settings.dingtalkHqClueEnabled()),
                decryptedBranchWebhook,
                Boolean.TRUE.equals(settings.dingtalkBranchClueEnabled()),
                clean(settings.remark()),
                clean(settings.updatedAt())
        );
    }

    private void write(SystemSettingsRecord settings) {
        systemSettingsRepository.writeSystemSettings(settings);
    }

    private SystemSettingsRecord emptySettings() {
        return new SystemSettingsRecord("", "", "", false, "", false, "", "");
    }

    private SystemSettingsRecord maskSecret(SystemSettingsRecord settings) {
        return new SystemSettingsRecord(
                settings.ocrAppCode(),
                mask(settings.ocrAppSecret()),
                maskUrl(settings.dingtalkHqClueWebhook()),
                Boolean.TRUE.equals(settings.dingtalkHqClueEnabled()),
                maskUrl(settings.dingtalkBranchClueWebhook()),
                Boolean.TRUE.equals(settings.dingtalkBranchClueEnabled()),
                settings.remark(),
                settings.updatedAt()
        );
    }

    private String nextSensitiveValue(String nextValue, String oldValue) {
        String cleaned = clean(nextValue);
        if (isMaskedSecret(cleaned) || cleaned.isEmpty()) {
            return clean(oldValue);
        }
        return cleaned;
    }

    private boolean needsEncrypt(String value) {
        String cleaned = clean(value);
        return !cleaned.isEmpty() && !secretCryptoService.isEncrypted(cleaned);
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

    private String maskUrl(String value) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        int slashIndex = cleaned.lastIndexOf('/');
        String prefix = slashIndex >= 0 ? cleaned.substring(0, slashIndex + 1) : "";
        String token = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        if (token.length() <= 8) {
            return prefix + "****";
        }
        return prefix + token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private boolean isMaskedSecret(String value) {
        return value != null && value.contains("****");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
