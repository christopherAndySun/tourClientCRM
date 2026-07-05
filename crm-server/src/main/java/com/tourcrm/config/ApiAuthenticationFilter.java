package com.tourcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.common.BusinessException;
import com.tourcrm.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public ApiAuthenticationFilter(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!shouldAuthenticate(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authService.currentUser(request.getHeader("Authorization"));
            filterChain.doFilter(request, response);
        } catch (BusinessException error) {
            writeUnauthorized(response, error.getMessage());
        }
    }

    private boolean shouldAuthenticate(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !path.equals("/api/auth/login") && !path.startsWith("/api/health");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }
}
