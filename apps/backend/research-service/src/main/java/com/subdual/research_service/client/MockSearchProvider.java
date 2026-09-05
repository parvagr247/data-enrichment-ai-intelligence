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

public class MockSearchProvider implements SourceDiscoveryProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSearchProvider.class);

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
        log.info("MockSearchProvider executing deterministic mock discovery for query: '{}' (maxResults: {})", query, maxResults);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Simulation hooks for testing failure and timeout boundaries
        if (lowerQuery.contains("simulate-failure") || lowerQuery.contains("provider-error") || lowerQuery.contains("simulate-error")) {
            throw new ExternalServiceException("Upstream search provider returned 502 Bad Gateway");
        }

        if (lowerQuery.contains("simulate-timeout")) {
            throw new ExternalServiceException("Search discovery timed out after 4000ms", new TimeoutException("Connection timed out"));
        }

        if (lowerQuery.contains("empty") || lowerQuery.contains("obscure") || lowerQuery.contains("non-existent") || lowerQuery.contains("zero-results")) {
            return List.of();
        }

        List<DiscoveredSource> results = new ArrayList<>();
        Instant now = Instant.now();

        if (lowerQuery.contains("spring-boot") || lowerQuery.contains("spring-projects")) {
            results.add(new DiscoveredSource(
                    "https://github.com/spring-projects/spring-boot",
                    "[MOCK] spring-projects/spring-boot: Spring Boot helps you create Spring-powered applications",
                    "GITHUB",
                    now,
                    1.00,
                    "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can just run."
            ));
            results.add(new DiscoveredSource(
                    "https://spring.io/projects/spring-boot",
                    "[MOCK] Spring Boot - Overview and Quickstart",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.95,
                    "Spring Boot overview, quick start guides, features, and production-ready monitoring tools."
            ));
            results.add(new DiscoveredSource(
                    "https://docs.spring.io/spring-boot/docs/current/reference/html/",
                    "[MOCK] Spring Boot Reference Documentation",
                    "DOCUMENTATION",
                    now,
                    0.90,
                    "Official reference documentation describing Spring Boot configurations and starters."
            ));
        } else if (lowerQuery.contains("jane doe") || lowerQuery.contains("jane-doe")) {
            results.add(new DiscoveredSource(
                    "https://example.com/profiles/jane-doe",
                    "[MOCK] Jane Doe - Principal Infrastructure Engineer",
                    "OFFICIAL_WEBSITE",
                    now,
                    1.00,
                    "Jane Doe is a Principal Infrastructure Engineer specializing in resilient cloud platforms."
            ));
            results.add(new DiscoveredSource(
                    "https://www.linkedin.com/in/jane-doe",
                    "[MOCK] Jane Doe | Professional Profile",
                    "SOCIAL_PROFILE",
                    now,
                    0.95,
                    "LinkedIn profile and professional background for Jane Doe."
            ));
            results.add(new DiscoveredSource(
                    "https://github.com/jane-doe",
                    "[MOCK] Jane Doe (jane-doe) · GitHub",
                    "GITHUB",
                    now,
                    0.85,
                    "GitHub profile featuring open source contributions by Jane Doe."
            ));
        } else if (lowerQuery.contains("linkedin") || lowerQuery.contains("example")) {
            results.add(new DiscoveredSource(
                    "https://www.example.com",
                    "[MOCK] Example Inc. - Official Homepage",
                    "OFFICIAL_WEBSITE",
                    now,
                    1.00,
                    "Example Inc. is a technology organization providing intelligence and data enrichment solutions."
            ));
            results.add(new DiscoveredSource(
                    "https://www.linkedin.com/company/example",
                    "[MOCK] Example Inc. | LinkedIn Profile",
                    "SOCIAL_PROFILE",
                    now,
                    0.90,
                    "Official corporate LinkedIn presence for Example Inc."
            ));
            results.add(new DiscoveredSource(
                    "https://en.wikipedia.org/wiki/Example_Inc",
                    "[MOCK] Example Inc. - Overview & History",
                    "SEARCH_RESULT",
                    now,
                    0.85,
                    "Historical overview and profile of Example Inc."
            ));
        } else {
            // General query fallback with clear mock indicators
            String slug = lowerQuery.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (slug.isBlank()) {
                slug = "entity";
            }
            results.add(new DiscoveredSource(
                    "https://mock-provider.local/sources/" + slug,
                    "[MOCK] " + query + " - Mock Overview",
                    "SEARCH_RESULT",
                    now,
                    0.80,
                    "Deterministic mock search result content for query: " + query
            ));
            results.add(new DiscoveredSource(
                    "https://mock-provider.local/docs/" + slug,
                    "[MOCK] " + query + " - Mock Documentation",
                    "DOCUMENTATION",
                    now,
                    0.75,
                    "Deterministic mock documentation reference for query: " + query
            ));
        }

        int limit = Math.min(results.size(), Math.max(1, maxResults));
        return results.subList(0, limit);
    }
}
