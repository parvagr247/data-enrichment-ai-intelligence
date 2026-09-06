package com.subdual.api_gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyAuthenticationFilter(@Value("${gateway.security.api-key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey != null ? expectedApiKey.trim() : "";
        if (!this.expectedApiKey.isEmpty()) {
            log.info("Gateway API Key authentication is ENABLED");
        } else {
            log.info("Gateway API Key authentication is DISABLED (open access mode)");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Allow CORS preflight requests
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Allow public health / actuator and authentication endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info") || path.startsWith("/api/v1/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. If API key is not configured, bypass authentication
        if (expectedApiKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Validate X-API-Key header
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !expectedApiKey.equals(providedKey.trim())) {
            log.warn("Unauthorized request to path='{}': Invalid or missing {}", path, API_KEY_HEADER);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(String.format(
                    "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\",\"path\":\"%s\"}",
                    Instant.now().toString(),
                    escapeJson(path)
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
