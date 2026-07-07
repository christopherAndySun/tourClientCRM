package com.tourcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.UserSession;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiAuthenticationFilterTest {

    private final AuthService authService = mock(AuthService.class);
    private final ApiAuthenticationFilter filter = new ApiAuthenticationFilter(authService, new ObjectMapper());

    @Test
    void skipsLoginEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsApiRequestWithoutValidToken() throws Exception {
        when(authService.currentUser("")).thenThrow(new BusinessException("请先登录"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clues");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请先登录");
        verify(authService).currentUser("");
    }

    @Test
    void acceptsApiRequestWithAuthCookie() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clues");
        request.setCookies(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(authService).currentUser("cookie-token");
    }

    @Test
    void rejectsUploadRequestWithoutValidToken() throws Exception {
        when(authService.currentUser("")).thenThrow(new BusinessException("璇峰厛鐧诲綍"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/clues/a.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(authService).currentUser("");
    }

    @Test
    void acceptsUploadRequestWithAuthCookie() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/clues/a.jpg");
        request.setCookies(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(authService).currentUser("cookie-token");
    }

    @Test
    void rejectsBusinessRequestWhenPasswordMustBeChanged() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user(true));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clues");
        request.setCookies(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("请先修改初始密码");
    }

    private UserSession user() {
        return user(false);
    }

    private UserSession user(boolean mustChangePassword) {
        return new UserSession("小白", "XA", "EMPLOYEE", "OPERATION", "", "HEADQUARTERS", null, null, mustChangePassword, List.of("CLUES"));
    }
}
