package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.AuthLoginRequest;
import com.tourcrm.dto.AuthUserResponse;
import com.tourcrm.dto.UserRecord;
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
    void loginRejectsWrongPassword() {
        UserRecord user = user("XA", new BCryptPasswordEncoder().encode("xa123456"));
        when(userRepository.findUserByEmployeeCode("XA")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest("XA", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工编号或密码错误");
    }

    private UserRecord user(String employeeCode, String password) {
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
                List.of(AuthService.MENU_CLUES, AuthService.MENU_CLUE_CREATE),
                "2026-07-05 10:00"
        );
    }
}
