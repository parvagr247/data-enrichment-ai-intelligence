package com.subdual.research_service.discovery.provider;

import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.research.model.DiscoveredSource;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Slf4j
public class MockSearchProvider implements SearchProvider {

    private static final int DEFAULT_MAX_RESULTS = 5;

    @Override
    public List<DiscoveredSource> search(String query, int maxResults) {
        log.info("MockSearchProvider executing deterministic mock discovery for query: '{}' (maxResults: {})", query, maxResults);

        if (isQueryBlank(query)) return List.of();
        
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        checkSimulationHooks(lowerQuery);

        if (isZeroResultQuery(lowerQuery)) return List.of();

        List<DiscoveredSource> candidates = generateCandidates(query, lowerQuery);
        return applyLimit(candidates, maxResults);
    }

    private boolean isQueryBlank(String query) {
        return query == null || query.isBlank();
    }

    private void checkSimulationHooks(String lowerQuery) {
        if (lowerQuery.contains("simulate-failure") || lowerQuery.contains("provider-error") || lowerQuery.contains("simulate-error")) {
            throw new ExternalServiceException("Upstream search provider returned 502 Bad Gateway");
        }
        if (lowerQuery.contains("simulate-timeout")) {
            throw new ExternalServiceException("Search discovery timed out after 4000ms", new TimeoutException("Connection timed out"));
        }
    }

    private boolean isZeroResultQuery(String lowerQuery) {
        return lowerQuery.contains("empty")
                || lowerQuery.contains("obscure")
                || lowerQuery.contains("non-existent")
                || lowerQuery.contains("zero-results");
    }

    private List<DiscoveredSource> generateCandidates(String rawQuery, String lowerQuery) {
        Instant now = Instant.now();

        if (lowerQuery.contains("spring-boot") || lowerQuery.contains("spring-projects")) {
            return mockSpringBootSources(now);
        }
        if (lowerQuery.contains("linux") || lowerQuery.contains("torvalds")) {
            return mockLinuxSources(now);
        }
        if (lowerQuery.contains("acme")) {
            return mockAcmeSources(now);
        }
        if (lowerQuery.contains("linkedin") || lowerQuery.contains("example")) {
            return mockLinkedInSources(now);
        }

        return mockFallbackSources(rawQuery, lowerQuery, now);
    }

    private List<DiscoveredSource> mockSpringBootSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://github.com/spring-projects/spring-boot",
                        "[MOCK] spring-projects/spring-boot: Spring Boot helps you create Spring-powered applications",
                        "GITHUB",
                        now,
                        1.00,
                        "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications."
                ),
                new DiscoveredSource(
                        "https://spring.io/projects/spring-boot",
                        "[MOCK] Spring Boot Overview - official website",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Spring Boot provides a comprehensive set of features for modern web apps."
                ),
                new DiscoveredSource(
                        "https://docs.spring.io/spring-boot",
                        "[MOCK] Spring Boot Reference Documentation",
                        "DOCUMENTATION",
                        now,
                        0.90,
                        "Spring Boot reference guide and documentation."
                )
        );
    }

    private List<DiscoveredSource> mockLinuxSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://github.com/torvalds",
                        "[MOCK] torvalds (Linus Torvalds) · GitHub",
                        "GITHUB",
                        now,
                        1.00,
                        "Linus Torvalds is the creator of Linux kernel and Git revision control system."
                ),
                new DiscoveredSource(
                        "https://en.wikipedia.org/wiki/Linus_Torvalds",
                        "[MOCK] Linus Torvalds - Wikipedia Biography",
                        "WIKIPEDIA",
                        now,
                        0.95,
                        "Linus Benedict Torvalds is a Finnish-American software engineer who is the principal developer of Linux."
                )
        );
    }

    private List<DiscoveredSource> mockAcmeSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://acme.org",
                        "[MOCK] Acme Corporation - Global Innovation",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Acme Corporation produces leading technology products and software solutions."
                ),
                new DiscoveredSource(
                        "https://github.com/acme",
                        "[MOCK] Acme Corp GitHub Organization",
                        "GITHUB",
                        now,
                        0.90,
                        "Open-source projects and developer tools by Acme Corporation."
                )
        );
    }

    private List<DiscoveredSource> mockLinkedInSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.example.org",
                        "[MOCK] Example Organization - Official Site",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Official website of Example Organization."
                ),
                new DiscoveredSource(
                        "https://www.linkedin.com/company/example",
                        "[MOCK] Example Organization | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        0.90,
                        "LinkedIn profile and corporate directory for Example Organization."
                ),
                new DiscoveredSource(
                        "https://www.google.com/search?q=example+organization",
                        "[MOCK] Google Search - Example Organization",
                        "SEARCH_RESULT",
                        now,
                        0.70,
                        "Search index results for Example Organization."
                )
        );
    }

    private List<DiscoveredSource> mockFallbackSources(String rawQuery, String lowerQuery, Instant now) {
        String sanitized = lowerQuery.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        String domainName = sanitized.isEmpty() ? "example" : sanitized;

        return List.of(
                new DiscoveredSource(
                        "https://" + domainName + ".org",
                        "[MOCK] " + rawQuery + " - Official Site",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.90,
                        "General overview and information regarding " + rawQuery + "."
                ),
                new DiscoveredSource(
                        "https://en.wikipedia.org/wiki/" + domainName,
                        "[MOCK] " + rawQuery + " - Wikipedia Overview",
                        "WIKIPEDIA",
                        now,
                        0.85,
                        "Reference article for " + rawQuery + "."
                )
        );
    }

    private List<DiscoveredSource> applyLimit(List<DiscoveredSource> results, int maxResults) {
        int limit = Math.min(maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS, results.size());
        return results.subList(0, limit);
    }
}
