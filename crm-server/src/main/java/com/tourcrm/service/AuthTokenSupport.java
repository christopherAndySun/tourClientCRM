package com.tourcrm.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

public final class AuthTokenSupport {

    public static final String COOKIE_NAME = "CRM_AUTH_TOKEN";

    private AuthTokenSupport() {
    }

    public static String resolveFromCurrentRequest(String token) {
        String rawToken = normalizeToken(token);
        if (StringUtils.hasText(rawToken)) {
            return rawToken;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return resolveFromRequest(servletAttributes.getRequest());
        }
        return "";
    }

    public static String resolveFromRequest(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String rawToken = normalizeToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (StringUtils.hasText(rawToken)) {
            return rawToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return normalizeToken(cookie.getValue());
            }
        }
        return "";
    }

    public static String resolveFromWebSocketSession(WebSocketSession session) {
        if (session == null) {
            return "";
        }
        String queryToken = "";
        if (session.getUri() != null) {
            queryToken = UriComponentsBuilder.fromUri(session.getUri())
                    .build()
                    .getQueryParams()
                    .getFirst("token");
        }
        String rawToken = normalizeToken(queryToken);
        if (StringUtils.hasText(rawToken)) {
            return rawToken;
        }
        return resolveFromCookieHeader(session.getHandshakeHeaders().getFirst(HttpHeaders.COOKIE));
    }

    public static String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private static String resolveFromCookieHeader(String cookieHeader) {
        if (!StringUtils.hasText(cookieHeader)) {
            return "";
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && COOKIE_NAME.equals(pair[0].trim())) {
                return normalizeToken(pair[1]);
            }
        }
        return "";
    }
}
