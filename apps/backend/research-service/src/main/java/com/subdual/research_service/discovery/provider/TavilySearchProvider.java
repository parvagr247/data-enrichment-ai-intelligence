package com.subdual.research_service.discovery.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.research.model.DiscoveredSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
public class TavilySearchProvider implements SearchProvider {

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final double DEFAULT_RELEVANCE_SCORE = 0.80;

    private final ResearchDiscoveryProperties properties;
    private final RestClient restClient;

    public TavilySearchProvider(ResearchDiscoveryProperties properties) {
        this.properties = properties;
        this.restClient = createHttpClient(properties);
    }

    @Override
    public List<DiscoveredSource> search(String query, int maxResults) {
        validateApiKey();
        
        if (isQueryBlank(query)) return List.of();
        
        TavilyResponse response = executeSearch(query, maxResults);
        return mapResults(response);
    }

    @Override
    public String providerName() {
        return "tavily";
    }

    private void validateApiKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ExternalServiceException("Search provider API key is not configured for provider: tavily");
        }
    }

    private boolean isQueryBlank(String query) {
        return query == null || query.isBlank();
    }

    private TavilyResponse executeSearch(String query, int maxResults) {
        log.info("Executing real Tavily search query: '{}' (maxResults: {})", query, maxResults);

        try {
            Map<String, Object> requestBody = buildRequestBody(query, maxResults);
            return restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TavilyResponse.class);
        } catch (Exception ex) {
            throw translateException(ex, query);
        }
    }

    private Map<String, Object> buildRequestBody(String query, int maxResults) {
        return Map.of(
                "api_key", properties.apiKey(),
                "query", query,
                "max_results", resolveMaxResults(maxResults),
                "search_depth", "basic",
                "include_answer", false,
                "include_raw_content", false
        );
    }

    private int resolveMaxResults(int maxResults) {
        return maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
    }

    private List<DiscoveredSource> mapResults(TavilyResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }

        Instant retrievedAt = Instant.now();
        return response.results().stream()
                .filter(this::hasValidUrl)
                .map(result -> toDiscoveredSource(result, retrievedAt))
                .toList();
    }

    private boolean hasValidUrl(TavilyResult result) {
        return result != null && result.url() != null && !result.url().isBlank();
    }

    private DiscoveredSource toDiscoveredSource(TavilyResult result, Instant retrievedAt) {
        String title = result.title() != null ? result.title() : "";
        double score = result.score() != null ? result.score() : DEFAULT_RELEVANCE_SCORE;
        String content = result.content() != null ? result.content() : "";

        return new DiscoveredSource(
                result.url(),
                title,
                "SEARCH_DISCOVERY",
                retrievedAt,
                score,
                content
        );
    }

    private ExternalServiceException translateException(Exception ex, String query) {
        if (ex instanceof ResourceAccessException) {
            log.error("Network timeout or unreachable host calling Tavily search API for query '{}': {}",
                    query, ex.getMessage());
            return new ExternalServiceException("Search provider timed out or is unreachable: " + ex.getMessage(), ex);
        }
        if (ex instanceof RestClientResponseException restEx) {
            log.error("Tavily search API returned HTTP error status {} for query '{}': {}",
                    restEx.getStatusCode(), query, restEx.getResponseBodyAsString());
            return new ExternalServiceException("Tavily search API returned error status: " + restEx.getStatusCode(), ex);
        }
        log.error("Unexpected failure querying Tavily search API for query '{}': {}", query, ex.getMessage(), ex);
        return new ExternalServiceException("Failed to discover sources from search provider: " + ex.getMessage(), ex);
    }

    private static RestClient createHttpClient(ResearchDiscoveryProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(properties.timeoutMs(), 3000)));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResponse(List<TavilyResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResult(String url, String title, String content, Double score) {}
}
