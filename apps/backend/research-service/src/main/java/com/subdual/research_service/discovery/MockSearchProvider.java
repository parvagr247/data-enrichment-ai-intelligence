package com.subdual.research_service.discovery;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Deterministic offline/mock implementation of SearchProvider for local development and testing.
 */
public class MockSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSearchProvider.class);

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
                    "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications."
            ));
            results.add(new DiscoveredSource(
                    "https://spring.io/projects/spring-boot",
                    "[MOCK] Spring Boot Overview - official website",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.95,
                    "Spring Boot provides a comprehensive set of features for modern web apps."
            ));
            results.add(new DiscoveredSource(
                    "https://docs.spring.io/spring-boot",
                    "[MOCK] Spring Boot Reference Documentation",
                    "DOCUMENTATION",
                    now,
                    0.90,
                    "Spring Boot reference guide and documentation."
            ));
        } else if (lowerQuery.contains("linux") || lowerQuery.contains("torvalds")) {
            results.add(new DiscoveredSource(
                    "https://github.com/torvalds",
                    "[MOCK] torvalds (Linus Torvalds) · GitHub",
                    "GITHUB",
                    now,
                    1.00,
                    "Linus Torvalds is the creator of Linux kernel and Git revision control system."
            ));
            results.add(new DiscoveredSource(
                    "https://en.wikipedia.org/wiki/Linus_Torvalds",
                    "[MOCK] Linus Torvalds - Wikipedia Biography",
                    "WIKIPEDIA",
                    now,
                    0.95,
                    "Linus Benedict Torvalds is a Finnish-American software engineer who is the principal developer of Linux."
            ));
        } else if (lowerQuery.contains("acme")) {
            results.add(new DiscoveredSource(
                    "https://acme.org",
                    "[MOCK] Acme Corporation - Global Innovation",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.95,
                    "Acme Corporation produces leading technology products and software solutions."
            ));
            results.add(new DiscoveredSource(
                    "https://github.com/acme",
                    "[MOCK] Acme Corp GitHub Organization",
                    "GITHUB",
                    now,
                    0.90,
                    "Open-source projects and developer tools by Acme Corporation."
            ));
        } else if (lowerQuery.contains("linkedin") || lowerQuery.contains("example")) {
            results.add(new DiscoveredSource(
                    "https://www.example.org",
                    "[MOCK] Example Organization - Official Site",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.95,
                    "Official website of Example Organization."
            ));
            results.add(new DiscoveredSource(
                    "https://www.linkedin.com/company/example",
                    "[MOCK] Example Organization | LinkedIn",
                    "SOCIAL_PROFILE",
                    now,
                    0.90,
                    "LinkedIn profile and corporate directory for Example Organization."
            ));
            results.add(new DiscoveredSource(
                    "https://www.google.com/search?q=example+organization",
                    "[MOCK] Google Search - Example Organization",
                    "SEARCH_RESULT",
                    now,
                    0.70,
                    "Search index results for Example Organization."
            ));
        } else {
            String sanitized = lowerQuery.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
            results.add(new DiscoveredSource(
                    "https://" + (sanitized.isEmpty() ? "example" : sanitized) + ".org",
                    "[MOCK] " + query + " - Official Site",
                    "OFFICIAL_WEBSITE",
                    now,
                    0.90,
                    "General overview and information regarding " + query + "."
            ));
            results.add(new DiscoveredSource(
                    "https://en.wikipedia.org/wiki/" + (sanitized.isEmpty() ? "Example" : sanitized),
                    "[MOCK] " + query + " - Wikipedia Overview",
                    "WIKIPEDIA",
                    now,
                    0.85,
                    "Reference article for " + query + "."
            ));
        }

        int limit = Math.min(maxResults > 0 ? maxResults : 5, results.size());
        return results.subList(0, limit);
    }
}
