package com.tourcrm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldForwardToIndex(request)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldForwardToIndex(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/uploads/") || path.startsWith("/ws/")) {
            return false;
        }
        if (path.equals("/") || path.equals("/index.html")) {
            return false;
        }
        return !path.substring(path.lastIndexOf('/') + 1).contains(".");
    }
}
