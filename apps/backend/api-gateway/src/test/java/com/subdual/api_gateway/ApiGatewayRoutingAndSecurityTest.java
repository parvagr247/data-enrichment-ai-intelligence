package com.subdual.api_gateway;

import com.subdual.api_gateway.config.GatewaySecurityConfiguration;
import com.subdual.api_gateway.filter.ApiKeyAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "server.port=0",
        "gateway.security.api-key=test-secret-key-12345",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
class ApiGatewayRoutingAndSecurityTest {

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
    @DisplayName("Should permit /actuator/info publicly without API key")
    void shouldAllowActuatorInfoWithoutApiKey() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/info");
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
    @DisplayName("Should reject request with 401 Unauthorized when X-API-Key is invalid")
    void shouldRejectWhenApiKeyInvalid() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        request.addHeader("X-API-Key", "wrong-key-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":401"));
        assertTrue(body.contains("\"error\":\"Unauthorized\""));
    }

    @Test
    @DisplayName("Should pass request when valid X-API-Key is provided")
    void shouldPassWhenValidApiKeyProvided() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-secret-key-12345");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/research");
        request.addHeader("X-API-Key", "test-secret-key-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should pass request when API key authentication is disabled (empty key)")
    void shouldPassWhenApiKeyDisabled() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/entities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should configure CORS with allowed origins and credentials")
    void shouldConfigureCorsCorrectly() {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("test-key");
        GatewaySecurityConfiguration config = new GatewaySecurityConfiguration(filter, "http://localhost:3000,http://127.0.0.1:3000");

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
