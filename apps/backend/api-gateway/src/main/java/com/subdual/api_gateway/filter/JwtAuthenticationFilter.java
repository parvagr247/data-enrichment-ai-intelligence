package com.subdual.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret:enrichment-platform-super-secret-jwt-signing-key-256-bits-minimum-required}") String jwtSecret) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("Gateway JWT Authentication Filter initialized");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HeaderMapRequestWrapper wrappedRequest = new HeaderMapRequestWrapper(request);

        // 1. Allow CORS preflight requests
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        String path = request.getRequestURI();

        // 2. Allow public actuator health and info endpoints
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/") || path.equals("/actuator/info")) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // 3. Allow public auth registration and login
        if (path.equals("/api/v1/auth/register") || path.equals("/api/v1/auth/login")) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // 4. Validate Bearer token on protected endpoints (via Authorization header or query parameter for EventSource SSE)
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            token = request.getParameter("token").trim();
        }

        if (token == null || token.isEmpty()) {
            log.warn("Unauthorized request to path='{}': Missing or invalid Authorization token", path);
            sendUnauthorized(response, path, "Missing or invalid Authorization header");
            return;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
                log.warn("Expired token for request to path='{}'", path);
                sendUnauthorized(response, path, "Token has expired");
                return;
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                log.warn("Token has no subject claim for path='{}'", path);
                sendUnauthorized(response, path, "Invalid token subject");
                return;
            }

            String email = claims.get("email", String.class);

            // Populate Spring SecurityContext for defense-in-depth authorization
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Inject trusted identity headers downstream
            wrappedRequest.addHeader("X-User-Id", userId);
            if (email != null && !email.isBlank()) {
                wrappedRequest.addHeader("X-User-Email", email);
            }

            log.debug("Authenticated request for user='{}', path='{}'", userId, path);
            filterChain.doFilter(wrappedRequest, response);

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token for path='{}': {}", path, e.getMessage());
            sendUnauthorized(response, path, "Invalid or malformed token");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                Instant.now().toString(),
                escapeJson(message),
                escapeJson(path)
        ));
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
