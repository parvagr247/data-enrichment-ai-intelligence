package com.subdual.api_gateway;

import com.subdual.api_gateway.config.GatewaySecurityConfiguration;
import com.subdual.api_gateway.filter.ApiKeyAuthenticationFilter;
import com.subdual.api_gateway.filter.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "server.port=0",
        "gateway.security.api-key=test-secret-key-12345",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "jwt.secret=enrichment-platform-super-secret-jwt-signing-key-256-bits-minimum-required"
})
class ApiGatewayRoutingAndSecurityTest {

    private static final String TEST_SECRET = "enrichment-platform-super-secret-jwt-signing-key-256-bits-minimum-required";

    private String createTestToken(String userId, String email, long expiryOffsetMs) {
        byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryOffsetMs);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("name", "Test User")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        // Confirms Spring Boot application context loads cleanly
    }

    @Test
    @DisplayName("Should permit /actuator/health publicly without API key")
    void shouldAllowActuatorHealthWithoutApiKey() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should permit CORS preflight OPTIONS requests without API key")
    void shouldAllowCorsPreflightWithoutApiKey() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/research");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should reject request with 401 Unauthorized when X-API-Key is missing")
    void shouldRejectWhenApiKeyMissing() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":401"));
        assertTrue(body.contains("\"error\":\"Unauthorized\""));
        assertTrue(body.contains("Invalid or missing API key"));
    }

    @Test
    @DisplayName("JWT Filter: Should allow /api/v1/auth/register and /api/v1/auth/login publicly")
    void jwtFilter_shouldAllowPublicAuthEndpoints() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("JWT Filter: Should reject protected endpoint without Bearer token")
    void jwtFilter_shouldRejectWithoutBearerToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("JWT Filter: Should accept valid Bearer token, inject X-User headers, and strip client spoofed headers")
    void jwtFilter_shouldAcceptValidTokenAndInjectHeaders() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET);

        String token = createTestToken("real-user-123", "real@example.com", 60000);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-User-Id", "spoofed-attacker-id"); // Attempted spoofing

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());

        // Verify downstream headers
        jakarta.servlet.http.HttpServletRequest downstreamReq = (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertEquals("real-user-123", downstreamReq.getHeader("X-User-Id"));
        assertEquals("real@example.com", downstreamReq.getHeader("X-User-Email"));
    }

    @Test
    @DisplayName("JWT Filter: Should reject expired token")
    void jwtFilter_shouldRejectExpiredToken() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET);

        String expiredToken = createTestToken("expired-user", "exp@example.com", -10000);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        request.addHeader("Authorization", "Bearer " + expiredToken);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should configure CORS with allowed origins and credentials")
    void shouldConfigureCorsCorrectly() {
        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter("test-key");
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(TEST_SECRET);
        GatewaySecurityConfiguration config = new GatewaySecurityConfiguration(apiKeyFilter, jwtFilter, "http://localhost:3000,http://127.0.0.1:3000");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/research");
        request.addHeader("Origin", "http://localhost:3000");

        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);
        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(corsConfig.getAllowedOrigins().contains("http://127.0.0.1:3000"));
        assertTrue(Boolean.TRUE.equals(corsConfig.getAllowCredentials()));
        assertTrue(corsConfig.getAllowedMethods().contains("GET"));
        assertTrue(corsConfig.getAllowedMethods().contains("POST"));
    }
}
