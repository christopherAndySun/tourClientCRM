package com.tourcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(authService.currentUser(isNull())).thenThrow(new BusinessException("请先登录"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clues");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("请先登录");
        verify(authService).currentUser(null);
    }
}
