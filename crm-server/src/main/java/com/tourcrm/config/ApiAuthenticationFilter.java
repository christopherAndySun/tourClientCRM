package com.tourcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.UserSession;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
            UserSession user = authService.currentUser(AuthTokenSupport.resolveFromRequest(request));
            if (user.mustChangePassword() && !isPasswordChangeAllowed(request)) {
                writeForbidden(response, "请先修改初始密码");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
            ));
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        } catch (BusinessException error) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, error.getMessage());
        }
    }

    private boolean shouldAuthenticate(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/uploads/")) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return false;
        }
        return !path.equals("/api/auth/login") && !path.startsWith("/api/health");
    }

    private boolean isPasswordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/api/auth/logout")) {
            return true;
        }
        return path.equals("/api/auth/me")
                && ("GET".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod()));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
    }
}
