package com.subdual.research_service.integration.web;

import com.subdual.research_service.config.WebFetchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWebContentFetcherTest {

    private DefaultWebContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        WebFetchProperties properties = new WebFetchProperties(3000, 5000, 5, "TestBot/1.0");
        fetcher = new DefaultWebContentFetcher(properties, true); // mockMode = true
    }

    @Test
    @DisplayName("Should return deterministic mock content in mock mode without socket connections")
    void shouldReturnMockContentInMockMode() {
        FetchedContent result = fetcher.fetch("https://github.com/spring-projects/spring-boot");

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("text/html");
        assertThat(result.rawBody()).contains("[MOCK] Spring Boot");
        assertThat(result.rawBody()).contains("meta name=\"description\"");
    }

    @Test
    @DisplayName("Should block SSRF requests to localhost, loopback, and Docker container hosts")
    void shouldBlockSsrfHosts() {
        // Create a non-mock fetcher to test SSRF hostname validation
        WebFetchProperties properties = new WebFetchProperties(3000, 5000, 5, "TestBot/1.0");
        DefaultWebContentFetcher liveFetcher = new DefaultWebContentFetcher(properties, false);

        FetchedContent localhostResult = liveFetcher.fetch("http://localhost:8080/actuator");
        assertThat(localhostResult.success()).isFalse();
        assertThat(localhostResult.httpStatus()).isEqualTo(403);
        assertThat(localhostResult.errorMessage()).contains("restricted");

        FetchedContent loopbackResult = liveFetcher.fetch("http://127.0.0.1:9741/api");
        assertThat(loopbackResult.success()).isFalse();
        assertThat(loopbackResult.httpStatus()).isEqualTo(403);

        FetchedContent mysqlResult = liveFetcher.fetch("http://mysql:3306");
        assertThat(mysqlResult.success()).isFalse();
        assertThat(mysqlResult.httpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Should reject invalid or unsupported URI schemes")
    void shouldRejectInvalidSchemes() {
        WebFetchProperties properties = new WebFetchProperties(3000, 5000, 5, "TestBot/1.0");
        DefaultWebContentFetcher liveFetcher = new DefaultWebContentFetcher(properties, false);

        FetchedContent ftpResult = liveFetcher.fetch("ftp://ftp.example.com/file");
        assertThat(ftpResult.success()).isFalse();
        assertThat(ftpResult.httpStatus()).isEqualTo(400);

        FetchedContent fileResult = liveFetcher.fetch("file:///etc/passwd");
        assertThat(fileResult.success()).isFalse();
        assertThat(fileResult.httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Should handle empty or null URL gracefully")
    void shouldHandleEmptyOrNullUrl() {
        FetchedContent nullResult = fetcher.fetch(null);
        assertThat(nullResult.success()).isFalse();
        assertThat(nullResult.httpStatus()).isEqualTo(400);

        FetchedContent blankResult = fetcher.fetch("   ");
        assertThat(blankResult.success()).isFalse();
        assertThat(blankResult.httpStatus()).isEqualTo(400);
    }
}
