package com.tourcrm.service;

import com.tourcrm.dto.SystemSettingsRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSettingsServiceTest {

    private static final String TOKEN = "token";

    private final AuthService authService = mock(AuthService.class);
    private final SystemSettingsRepository repository = mock(SystemSettingsRepository.class);
    private final SystemAuditService systemAuditService = mock(SystemAuditService.class);
    private final SecretCryptoService secretCryptoService = new SecretCryptoService("settings-test-key", "jwt-test-key");
    private final SystemSettingsService service = new SystemSettingsService(authService, repository, systemAuditService, secretCryptoService);

    @Test
    void saveEncryptsSensitiveSettingsButReturnsMaskedValues() {
        when(repository.readSystemSettings()).thenReturn(Optional.empty());

        SystemSettingsRecord result = service.save(new SystemSettingsRecord(
                "app-code",
                "app-secret-123",
                "https://connector.dingtalk.com/webhook/flow/test-webhook-token",
                true,
                "remark",
                ""
        ), TOKEN);

        ArgumentCaptor<SystemSettingsRecord> captor = ArgumentCaptor.forClass(SystemSettingsRecord.class);
        verify(repository).writeSystemSettings(captor.capture());
        SystemSettingsRecord stored = captor.getValue();
        assertThat(stored.ocrAppSecret()).startsWith("enc:v1:");
        assertThat(stored.dingtalkHqClueWebhook()).startsWith("enc:v1:");
        assertThat(secretCryptoService.decrypt(stored.ocrAppSecret())).isEqualTo("app-secret-123");
        assertThat(secretCryptoService.decrypt(stored.dingtalkHqClueWebhook())).contains("test-webhook-token");
        assertThat(stored.dingtalkHqClueEnabled()).isTrue();
        assertThat(result.ocrAppSecret()).isEqualTo("ap****23");
        assertThat(result.dingtalkHqClueWebhook()).contains("test****oken");
        verify(systemAuditService).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getForSystemDecryptsStoredSensitiveSettings() {
        String encryptedSecret = secretCryptoService.encrypt("app-secret-123");
        String encryptedWebhook = secretCryptoService.encrypt("https://connector.dingtalk.com/webhook/flow/test-webhook-token");
        when(repository.readSystemSettings()).thenReturn(Optional.of(new SystemSettingsRecord(
                "app-code",
                encryptedSecret,
                encryptedWebhook,
                true,
                "remark",
                "2026-07-05 10:00"
        )));

        SystemSettingsRecord result = service.getForSystem();

        assertThat(result.ocrAppSecret()).isEqualTo("app-secret-123");
        assertThat(result.dingtalkHqClueWebhook()).contains("test-webhook-token");
        assertThat(result.dingtalkHqClueEnabled()).isTrue();
    }

    @Test
    void getForSystemMigratesPlainSensitiveSettingsToEncryptedStorage() {
        when(repository.readSystemSettings()).thenReturn(Optional.of(new SystemSettingsRecord(
                "app-code",
                "plain-secret",
                "https://connector.dingtalk.com/webhook/flow/plain-webhook-token",
                true,
                "remark",
                "2026-07-05 10:00"
        )));

        SystemSettingsRecord result = service.getForSystem();

        ArgumentCaptor<SystemSettingsRecord> captor = ArgumentCaptor.forClass(SystemSettingsRecord.class);
        verify(repository).writeSystemSettings(captor.capture());
        assertThat(result.ocrAppSecret()).isEqualTo("plain-secret");
        assertThat(result.dingtalkHqClueWebhook()).contains("plain-webhook-token");
        assertThat(captor.getValue().ocrAppSecret()).startsWith("enc:v1:");
        assertThat(captor.getValue().dingtalkHqClueWebhook()).startsWith("enc:v1:");
        assertThat(secretCryptoService.decrypt(captor.getValue().ocrAppSecret())).isEqualTo("plain-secret");
        assertThat(secretCryptoService.decrypt(captor.getValue().dingtalkHqClueWebhook())).contains("plain-webhook-token");
    }
}
