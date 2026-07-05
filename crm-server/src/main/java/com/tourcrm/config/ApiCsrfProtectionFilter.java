package com.tourcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.service.AuthTokenSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ApiCsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ObjectMapper objectMapper;

    public ApiCsrfProtectionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!shouldValidate(request) || isSameSiteRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("请求来源校验失败，请刷新页面后重试")));
    }

    private boolean shouldValidate(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.equals("/api/auth/login")) {
            return false;
        }
        return hasAuthCookie(request);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (AuthTokenSupport.COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameSiteRequest(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin)) {
            return isTrustedOrigin(request, origin);
        }
        String referer = request.getHeader(HttpHeaders.REFERER);
        return !StringUtils.hasText(referer) || isTrustedOrigin(request, referer);
    }

    private boolean isTrustedOrigin(HttpServletRequest request, String originOrReferer) {
        try {
            URI uri = URI.create(originOrReferer);
            String requestHost = forwardedHost(request);
            String originHost = uri.getHost();
            if (!StringUtils.hasText(originHost)) {
                return false;
            }
            if (originHost.equalsIgnoreCase(requestHost)) {
                return true;
            }
            return isLocalDevOrigin(originHost, uri.getPort());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private String forwardedHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = StringUtils.hasText(forwardedHost) ? forwardedHost : request.getServerName();
        int commaIndex = host.indexOf(',');
        if (commaIndex >= 0) {
            host = host.substring(0, commaIndex);
        }
        int colonIndex = host.indexOf(':');
        return (colonIndex >= 0 ? host.substring(0, colonIndex) : host).trim();
    }

    private boolean isLocalDevOrigin(String host, int port) {
        if (port != 5173) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
