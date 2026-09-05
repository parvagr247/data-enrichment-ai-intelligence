package com.subdual.research_service.discovery;

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

/**
 * Strategy implementation connecting to Tavily Search API.
 * Maps external Tavily payloads directly into internal DiscoveredSource domain objects.
 */
@Slf4j
public class TavilySearchProvider implements SearchProvider {

    private final ResearchDiscoveryProperties properties;
    private final RestClient restClient;

    public TavilySearchProvider(ResearchDiscoveryProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(properties.timeoutMs(), 3000)));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<DiscoveredSource> search(String query, int maxResults) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new ExternalServiceException("Search provider API key is not configured for provider: tavily");
        }

        if (query == null || query.isBlank()) {
            return List.of();
        }

        log.info("Executing real Tavily search query: '{}' (maxResults: {})", query, maxResults);

        try {
            Map<String, Object> requestBody = Map.of(
                    "api_key", properties.apiKey(),
                    "query", query,
                    "max_results", maxResults > 0 ? maxResults : 5,
                    "search_depth", "basic",
                    "include_answer", false,
                    "include_raw_content", false
            );

            TavilyResponse response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TavilyResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }

            Instant now = Instant.now();
            return response.results().stream()
                    .filter(result -> result.url() != null && !result.url().isBlank())
                    .map(result -> new DiscoveredSource(
                            result.url(),
                            result.title() != null ? result.title() : "",
                            "SEARCH_DISCOVERY",
                            now,
                            result.score() != null ? result.score() : 0.8,
                            result.content() != null ? result.content() : ""
                    ))
                    .toList();

        } catch (ResourceAccessException ex) {
            log.error("Network timeout or unreachable host calling Tavily search API: {}", ex.getMessage());
            throw new ExternalServiceException("Search provider timed out or is unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            log.error("Tavily search API returned HTTP error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ExternalServiceException("Tavily search API returned error status: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            log.error("Unexpected failure querying Tavily search API: {}", ex.getMessage(), ex);
            throw new ExternalServiceException("Failed to discover sources from search provider: " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResponse(List<TavilyResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResult(String url, String title, String content, Double score) {}
}
