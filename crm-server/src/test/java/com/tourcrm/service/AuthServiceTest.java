package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.AuthLoginRequest;
import com.tourcrm.dto.AuthUserResponse;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import com.tourcrm.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginSessionRepository loginSessionRepository = mock(LoginSessionRepository.class);
    private final AuthService authService = new AuthService(userRepository, loginSessionRepository, 1440, false);

    @Test
    void loginCreatesTokenSession() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"));
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        AuthUserResponse response = authService.login(new AuthLoginRequest("xa", "xa123456"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.employeeCode()).isEqualTo("XA");
        verify(loginSessionRepository).createSession(eq(response.token()), eq("XA"), any(), eq(false));
    }

    @Test
    void loginBootstrapsAdminWhenDatabaseIsEmpty() {
        UserRecord admin = new UserRecord(
                "admin",
                "ADMIN",
                new BCryptPasswordEncoder().encode("admin123"),
                "ADMIN",
                "OPERATION",
                null,
                "HEADQUARTERS",
                null,
                null,
                true,
                List.of(AuthService.MENU_USERS, AuthService.MENU_SETTINGS),
                "2026-07-05 10:00"
        );
        when(userRepository.findUserByEmployeeCode("ADMIN"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(admin));

        AuthUserResponse response = authService.login(new AuthLoginRequest("admin", "admin123"));

        assertThat(response.employeeCode()).isEqualTo("ADMIN");
        assertThat(response.mustChangePassword()).isTrue();
        verify(userRepository).writeUser(any(UserRecord.class));
        verify(loginSessionRepository).createSession(eq(response.token()), eq("ADMIN"), any(), eq(false));
    }

    @Test
    void loginReturnsMustChangePasswordFlag() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"), true);
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        AuthUserResponse response = authService.login(new AuthLoginRequest("XA", "xa123456"));

        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void loginRejectsWrongPassword() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"));
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest("XA", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工编号或密码错误");
    }

    @Test
    void forcedPasswordChangeRequiresNewPassword() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"), true);
        when(loginSessionRepository.findSessionEmployeeCode("token")).thenReturn(Optional.of("XA"));
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.updateCurrentUser(new UserUpdateRequest("小白", ""), "Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先设置新密码");
    }

    @Test
    void selfPasswordUpdateClearsForcedPasswordChange() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"), true);
        when(loginSessionRepository.findSessionEmployeeCode("token")).thenReturn(Optional.of("XA"));
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        UserSession updated = authService.updateCurrentUser(new UserUpdateRequest("小白", "new123456"), "Bearer token");

        assertThat(updated.mustChangePassword()).isFalse();
    }

    private UserRecord user(String employeeCode, String password) {
        return user(employeeCode, password, false);
    }

    private UserRecord user(String employeeCode, String password, boolean mustChangePassword) {
        return new UserRecord(
                "小白",
                employeeCode,
                password,
                "EMPLOYEE",
                "OPERATION",
                null,
                "HEADQUARTERS",
                null,
                null,
                mustChangePassword,
                List.of(AuthService.MENU_CLUES, AuthService.MENU_CLUE_CREATE),
                "2026-07-05 10:00"
        );
    }
}
