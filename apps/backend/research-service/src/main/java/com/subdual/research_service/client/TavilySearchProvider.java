package com.subdual.research_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TavilySearchProvider implements SourceDiscoveryProvider {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchProvider.class);

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
    public List<DiscoveredSource> discover(String query, int maxResults) {
        return search(query, maxResults);
    }

    @Override
    public List<DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
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

        Map<String, Object> requestBody = Map.of(
                "api_key", properties.apiKey(),
                "query", query,
                "max_results", maxResults,
                "search_depth", "basic"
        );

        try {
            TavilySearchResponse response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TavilySearchResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                log.info("Tavily search provider returned 0 results for query: '{}'", query);
                return List.of();
            }

            Instant now = Instant.now();
            List<DiscoveredSource> sources = response.results().stream()
                    .filter(item -> item != null && item.url() != null && !item.url().isBlank())
                    .map(item -> new DiscoveredSource(
                            item.url(),
                            item.title() != null && !item.title().isBlank() ? item.title() : item.url(),
                            classifySourceType(item.url()),
                            now,
                            item.score() != null ? item.score() : 0.80,
                            item.content()
                    ))
                    .toList();

            log.info("Tavily search provider successfully returned {} sources for query: '{}'", sources.size(), query);
            return sources;
        } catch (ResourceAccessException ex) {
            log.error("Tavily search provider timed out or connection failed: {}", ex.getMessage());
            throw new ExternalServiceException("Search discovery timed out or failed to connect to upstream provider", ex);
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            log.error("Tavily search provider returned HTTP {}: {}", statusCode, ex.getResponseBodyAsString());
            if (statusCode == 401 || statusCode == 403) {
                throw new ExternalServiceException("Search provider authentication failed: invalid or expired API key", ex);
            } else if (statusCode == 429) {
                throw new ExternalServiceException("Search provider rate limit exceeded; please retry later", ex);
            }
            throw new ExternalServiceException("Search provider unavailable; upstream service returned " + statusCode, ex);
        } catch (Exception ex) {
            log.error("Unexpected error querying Tavily search provider: {}", ex.getMessage(), ex);
            throw new ExternalServiceException("Failed to retrieve research sources from provider", ex);
        }
    }

    private String classifySourceType(String url) {
        if (url == null) {
            return "OTHER";
        }
        String lower = url.toLowerCase();
        if (lower.contains("github.com") || lower.contains("gitlab.com")) {
            return "GITHUB";
        }
        if (lower.contains("docs.") || lower.contains("/docs") || lower.contains("/reference") || lower.contains("readthedocs.io")) {
            return "DOCUMENTATION";
        }
        if (lower.contains("linkedin.com") || lower.contains("twitter.com") || lower.contains("x.com")) {
            return "SOCIAL_PROFILE";
        }
        if (lower.contains(".gov") || lower.contains(".mil")) {
            return "GOVERNMENT";
        }
        if (lower.contains("blog.") || lower.contains("/blog")) {
            return "BLOG";
        }
        return "SEARCH_RESULT";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TavilySearchResponse(List<TavilyResultItem> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TavilyResultItem(String title, String url, String content, Double score) {}
}
