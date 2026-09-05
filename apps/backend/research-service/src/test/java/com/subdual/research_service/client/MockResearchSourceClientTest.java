package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockResearchSourceClientTest {

    private MockResearchSourceClient client;

    @BeforeEach
    void setUp() {
        client = new MockResearchSourceClient();
    }

    @Test
    @DisplayName("Should return realistic sources for Spring Boot query")
    void shouldReturnSourcesForSpringBootQuery() {
        List<DiscoveredSource> sources = client.discoverSources("spring-projects/spring-boot repository", 5);

        assertThat(sources).isNotEmpty();
        assertThat(sources).anyMatch(s -> s.url().contains("github.com/spring-projects/spring-boot") && s.sourceType().equals("GITHUB"));
        assertThat(sources).anyMatch(s -> s.url().contains("spring.io") && s.sourceType().equals("OFFICIAL_WEBSITE"));
        assertThat(sources).anyMatch(s -> s.url().contains("docs.spring.io") && s.sourceType().equals("DOCUMENTATION"));
    }

    @Test
    @DisplayName("Should return realistic sources for LinkedIn / Example organization query")
    void shouldReturnSourcesForLinkedInQuery() {
        List<DiscoveredSource> sources = client.discoverSources("example linkedin organization", 5);

        assertThat(sources).isNotEmpty();
        assertThat(sources).anyMatch(s -> s.sourceType().equals("OFFICIAL_WEBSITE"));
        assertThat(sources).anyMatch(s -> s.sourceType().equals("SOCIAL_PROFILE"));
        assertThat(sources).anyMatch(s -> s.sourceType().equals("SEARCH_RESULT"));
    }

    @Test
    @DisplayName("Should return empty list for empty or obscure query")
    void shouldReturnEmptyListForObscureQuery() {
        List<DiscoveredSource> sources = client.discoverSources("obscure-zero-results", 5);
        assertThat(sources).isEmpty();

        List<DiscoveredSource> nullSources = client.discoverSources(null, 5);
        assertThat(nullSources).isEmpty();
    }

    @Test
    @DisplayName("Should throw ExternalServiceException on failure simulation query")
    void shouldThrowOnSimulatedFailure() {
        assertThatThrownBy(() -> client.discoverSources("simulate-failure-provider", 5))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("502 Bad Gateway");
    }

    @Test
    @DisplayName("Should throw ExternalServiceException with TimeoutException on timeout simulation query")
    void shouldThrowOnSimulatedTimeout() {
        assertThatThrownBy(() -> client.discoverSources("simulate-timeout-provider", 5))
                .isInstanceOf(ExternalServiceException.class)
                .hasCauseInstanceOf(TimeoutException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    @DisplayName("Should respect maxResults limit")
    void shouldRespectMaxResultsLimit() {
        List<DiscoveredSource> sources = client.discoverSources("spring-boot", 2);
        assertThat(sources).hasSize(2);
    }
}
