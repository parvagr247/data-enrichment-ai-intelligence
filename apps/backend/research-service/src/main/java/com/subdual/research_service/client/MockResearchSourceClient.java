package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public class MockResearchSourceClient implements SourceDiscoveryProvider {

    private static final Logger log = LoggerFactory.getLogger(MockResearchSourceClient.class);

    @Override
    public List<DiscoveredSource> discoverSources(String query, int maxResults) {
        log.info("MockResearchSourceClient discovering sources for query: '{}' (maxResults: {})", query, maxResults);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Simulation hooks for testing failure and timeout boundaries
        if (lowerQuery.contains("simulate-failure") || lowerQuery.contains("provider-error") || lowerQuery.contains("fail")) {
            throw new ExternalServiceException("Upstream search provider returned 502 Bad Gateway");
        }

        if (lowerQuery.contains("simulate-timeout") || lowerQuery.contains("timeout")) {
            throw new ExternalServiceException("Search discovery timed out after 4000ms", new TimeoutException("Connection timed out"));
        }

        if (lowerQuery.contains("empty") || lowerQuery.contains("obscure") || lowerQuery.contains("non-existent")) {
            return List.of();
        }

        List<DiscoveredSource> results = new ArrayList<>();
        Instant now = Instant.now();

        if (lowerQuery.contains("spring-boot") || lowerQuery.contains("spring-projects")) {
            results.add(new DiscoveredSource(
                    "https://github.com/spring-projects/spring-boot",
                    "spring-projects/spring-boot: Spring Boot helps you create Spring-powered applications",
                    "GITHUB",
                    now,
                    1.00
            ));
            results.add(new DiscoveredSource(
                    "https://spring.io/projects/spring-boot",
                    "Spring Boot - Overview and Quickstart",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.95
            ));
            results.add(new DiscoveredSource(
                    "https://docs.spring.io/spring-boot/docs/current/reference/html/",
                    "Spring Boot Reference Documentation",
                    "DOCUMENTATION",
                    now,
                    0.90
            ));
        } else if (lowerQuery.contains("jane doe") || lowerQuery.contains("jane-doe")) {
            results.add(new DiscoveredSource(
                    "https://example.com/profiles/jane-doe",
                    "Jane Doe - Principal Infrastructure Engineer",
                    "OFFICIAL_WEBSITE",
                    now,
                    1.00
            ));
            results.add(new DiscoveredSource(
                    "https://www.linkedin.com/in/jane-doe",
                    "Jane Doe | Professional Profile",
                    "SOCIAL_PROFILE",
                    now,
                    0.95
            ));
            results.add(new DiscoveredSource(
                    "https://github.com/jane-doe",
                    "Jane Doe (jane-doe) · GitHub",
                    "GITHUB",
                    now,
                    0.85
            ));
        } else if (lowerQuery.contains("linkedin") || lowerQuery.contains("example")) {
            results.add(new DiscoveredSource(
                    "https://www.example.com",
                    "Example Inc. - Official Homepage",
                    "OFFICIAL_WEBSITE",
                    now,
                    1.00
            ));
            results.add(new DiscoveredSource(
                    "https://www.linkedin.com/company/example",
                    "Example Inc. | LinkedIn Profile",
                    "SOCIAL_PROFILE",
                    now,
                    0.90
            ));
            results.add(new DiscoveredSource(
                    "https://en.wikipedia.org/wiki/Example_Inc",
                    "Example Inc. - Overview & History",
                    "SEARCH_RESULT",
                    now,
                    0.85
            ));
        } else {
            // General query fallback with realistic discovery metadata
            String slug = lowerQuery.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (slug.isBlank()) {
                slug = "entity";
            }
            results.add(new DiscoveredSource(
                    "https://www." + slug + ".org",
                    query + " - Official Website",
                    "OFFICIAL_WEBSITE",
                    now,
                    1.00
            ));
            results.add(new DiscoveredSource(
                    "https://docs." + slug + ".org",
                    query + " - Reference Documentation",
                    "DOCUMENTATION",
                    now,
                    0.90
            ));
            results.add(new DiscoveredSource(
                    "https://en.wikipedia.org/wiki/" + slug,
                    query + " - Overview & Reference",
                    "SEARCH_RESULT",
                    now,
                    0.80
            ));
        }

        int limit = Math.min(results.size(), Math.max(1, maxResults));
        return results.subList(0, limit);
    }
}
