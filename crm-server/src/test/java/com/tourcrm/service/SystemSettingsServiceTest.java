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
    void saveEncryptsSecretButReturnsMaskedValue() {
        when(repository.readSystemSettings()).thenReturn(Optional.empty());

        SystemSettingsRecord result = service.save(new SystemSettingsRecord("app-code", "app-secret-123", "备注", ""), TOKEN);

        ArgumentCaptor<SystemSettingsRecord> captor = ArgumentCaptor.forClass(SystemSettingsRecord.class);
        verify(repository).writeSystemSettings(captor.capture());
        SystemSettingsRecord stored = captor.getValue();
        assertThat(stored.ocrAppSecret()).startsWith("enc:v1:");
        assertThat(stored.ocrAppSecret()).doesNotContain("app-secret-123");
        assertThat(secretCryptoService.decrypt(stored.ocrAppSecret())).isEqualTo("app-secret-123");
        assertThat(result.ocrAppSecret()).isEqualTo("ap****23");
        verify(systemAuditService).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getForSystemDecryptsStoredSecret() {
        String encrypted = secretCryptoService.encrypt("app-secret-123");
        when(repository.readSystemSettings()).thenReturn(Optional.of(new SystemSettingsRecord("app-code", encrypted, "备注", "2026-07-05 10:00")));

        SystemSettingsRecord result = service.getForSystem();

        assertThat(result.ocrAppSecret()).isEqualTo("app-secret-123");
    }
}
