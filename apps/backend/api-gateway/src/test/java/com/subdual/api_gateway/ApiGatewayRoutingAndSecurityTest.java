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

import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "gateway.security.api-key=",
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
        assertFalse(corsConfig.getAllowedHeaders().contains("*"));
    }

    @Test
    @DisplayName("JWT Filter: Should populate SecurityContextHolder on valid token")
    void jwtFilter_shouldPopulateSecurityContextHolderOnValidToken() throws Exception {
        SecurityContextHolder.clearContext();
        try {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET);
            String token = createTestToken("user-context-456", "context@example.com", 60000);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
            request.addHeader("Authorization", "Bearer " + token);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertNotNull(chain.getRequest());
            assertEquals(200, response.getStatus());

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("user-context-456", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("CORS: Should parse GCP VM IP origin without wildcard credentials collision")
    void shouldConfigureCorsWithGcpVmOrigin() {
        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter("test-key");
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(TEST_SECRET);
        GatewaySecurityConfiguration config = new GatewaySecurityConfiguration(
                apiKeyFilter,
                jwtFilter,
                "http://34.93.207.65:3000, http://localhost:3000"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);

        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedOrigins().contains("http://34.93.207.65:3000"));
        assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:3000"));
        assertFalse(corsConfig.getAllowedOrigins().contains("*"));
        assertTrue(Boolean.TRUE.equals(corsConfig.getAllowCredentials()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @DisplayName("Inspect Gateway Beans and RequestFactory")
    void inspectGatewayBeans() {
        assertNotNull(applicationContext);
        String[] beanNames = applicationContext.getBeanNamesForType(org.springframework.http.client.ClientHttpRequestFactory.class);
        System.out.println("=== ClientHttpRequestFactory Beans ===");
        for (String name : beanNames) {
            System.out.println("Bean: " + name + " -> " + applicationContext.getBean(name).getClass().getName());
        }

        String[] multipartResolvers = applicationContext.getBeanNamesForType(org.springframework.web.multipart.MultipartResolver.class);
        System.out.println("=== MultipartResolver Beans ===");
        for (String name : multipartResolvers) {
            System.out.println("Bean: " + name + " -> " + applicationContext.getBean(name).getClass().getName());
        }
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    @DisplayName("Diagnostic: Test OPTIONS and POST Upload routing")
    void testOptionsAndPostUpload() throws Exception {
        System.out.println("=== Gateway running on port: " + port + " ===");

        // Test 1: OPTIONS with allowed origin http://34.93.207.65:3000
        org.springframework.web.client.RestClient client = org.springframework.web.client.RestClient.create("http://localhost:" + port);
        org.springframework.http.ResponseEntity<Void> optionsResp = client.options()
                .uri("/api/v2/datasets/upload")
                .header("Origin", "http://34.93.207.65:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .retrieve()
                .toBodilessEntity();

        System.out.println("OPTIONS status with VM origin: " + optionsResp.getStatusCode());
        System.out.println("OPTIONS Allow-Origin: " + optionsResp.getHeaders().getAccessControlAllowOrigin());

        // Test 2: OPTIONS with Cloudflare / HTTPS origin
        try {
            org.springframework.http.ResponseEntity<Void> cfOptions = client.options()
                    .uri("/api/v2/datasets/upload")
                    .header("Origin", "https://subdual.ai")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "authorization,content-type")
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("OPTIONS status with Cloudflare origin: " + cfOptions.getStatusCode());
            System.out.println("OPTIONS Allow-Origin: " + cfOptions.getHeaders().getAccessControlAllowOrigin());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.out.println("OPTIONS with Cloudflare origin FAILED: " + e.getStatusCode() + " - " + e.getMessage());
        }

        // Test 3: Start mock downstream service for dataset-service on port 9743
        com.sun.net.httpserver.HttpServer mockDatasetService = null;
        try {
            mockDatasetService = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(9743), 0);
            final java.util.concurrent.atomic.AtomicBoolean return500 = new java.util.concurrent.atomic.AtomicBoolean(false);
            mockDatasetService.createContext("/api/v2/datasets/upload", exchange -> {
                System.out.println("--> Mock DatasetService received request: " + exchange.getRequestMethod());
                System.out.println("--> Headers: " + exchange.getRequestHeaders().entrySet());
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                System.out.println("--> Body length: " + bodyBytes.length);

                if (return500.get()) {
                    byte[] err = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Failure in dataset service\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, err.length);
                    try (java.io.OutputStream os = exchange.getResponseBody()) {
                        os.write(err);
                    }
                } else {
                    byte[] resp = "{\"datasetName\":\"test.csv\",\"totalRows\":5}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://downstream-service-sent-this");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (java.io.OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                }
            });
            mockDatasetService.start();

            // Test 3A: POST 200 OK
            String token = createTestToken("test-user", "test@example.com", 60000);
            org.springframework.http.HttpHeaders fileHeaders = new org.springframework.http.HttpHeaders();
            fileHeaders.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
            fileHeaders.setContentDispositionFormData("file", "test.csv");
            org.springframework.http.HttpEntity<String> fileEntity = new org.springframework.http.HttpEntity<>("name,email\nAlice,alice@test.com", fileHeaders);

            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", fileEntity);

            org.springframework.http.ResponseEntity<String> postResp = client.post()
                    .uri("/api/v2/datasets/upload")
                    .header("Origin", "http://34.93.207.65:3000")
                    .header("Authorization", "Bearer " + token)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            System.out.println("POST 200 status: " + postResp.getStatusCode());
            System.out.println("POST 200 CORS Allow-Origin (all values): " + postResp.getHeaders().get("Access-Control-Allow-Origin"));
            System.out.println("POST 200 CORS Allow-Credentials (all values): " + postResp.getHeaders().get("Access-Control-Allow-Credentials"));
            assertEquals(1, postResp.getHeaders().get("Access-Control-Allow-Origin").size(), "Access-Control-Allow-Origin must not be duplicated");
            assertEquals("http://34.93.207.65:3000", postResp.getHeaders().getAccessControlAllowOrigin());
            assertEquals(1, postResp.getHeaders().get("Access-Control-Allow-Credentials").size(), "Access-Control-Allow-Credentials must not be duplicated");
            assertTrue(postResp.getHeaders().getAccessControlAllowCredentials());
            System.out.println("POST 200 response body: " + postResp.getBody());

            // Test 3B: POST when downstream returns 500
            return500.set(true);
            try {
                client.post()
                        .uri("/api/v2/datasets/upload")
                        .header("Origin", "http://34.93.207.65:3000")
                        .header("Authorization", "Bearer " + token)
                        .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                System.out.println("POST 500 status: " + e.getStatusCode());
                System.out.println("POST 500 CORS Allow-Origin: " + e.getResponseHeaders().getAccessControlAllowOrigin());
                System.out.println("POST 500 response body: " + e.getResponseBodyAsString());
            }

        } finally {
            if (mockDatasetService != null) {
                mockDatasetService.stop(0);
            }
        }
    }
}
