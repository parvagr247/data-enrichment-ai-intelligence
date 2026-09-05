package com.subdual.research_service.service;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SourceProcessor {

    private static final Logger log = LoggerFactory.getLogger(SourceProcessor.class);

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "ref_src", "fbclid", "gclid", "source", "feature", "mc_eid"
    );

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target, int maxSources) {
        if (rawSources == null || rawSources.isEmpty()) {
            return List.of();
        }

        Set<String> seenUrls = new HashSet<>();
        List<ResearchSource> processed = new ArrayList<>();

        for (DiscoveredSource ds : rawSources) {
            if (ds == null || ds.url() == null || ds.url().isBlank()) {
                continue;
            }

            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null || seenUrls.contains(normalizedUrl)) {
                continue;
            }
            seenUrls.add(normalizedUrl);

            String title = (ds.title() != null && !ds.title().isBlank())
                    ? ds.title().trim()
                    : normalizedUrl;

            String sourceType = classifySourceType(normalizedUrl, ds.sourceType(), target);
            Instant retrievedAt = ds.retrievedAt() != null ? ds.retrievedAt() : Instant.now();
            double providerRelevance = ds.relevance() != null ? ds.relevance() : 0.80;
            double qualityScore = calculateRankedScore(providerRelevance, sourceType, target);

            processed.add(new ResearchSource(normalizedUrl, title, sourceType, retrievedAt, providerRelevance, qualityScore, ds.snippet()));
        }

        processed.sort(Comparator.comparingDouble(ResearchSource::qualityScore).reversed());

        int limit = Math.min(processed.size(), Math.max(1, maxSources));
        return processed.subList(0, limit);
    }

    public String normalizeDiscoveredUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return null;
            }
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
            if (host == null || host.isBlank()) {
                return null;
            }

            int port = uri.getPort();
            String portPart = (port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                    ? "" : ":" + port;

            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            // Strip tracking query parameters
            String cleanQuery = "";
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                String filtered = Arrays.stream(uri.getQuery().split("&"))
                        .filter(param -> {
                            String paramName = param.split("=")[0].toLowerCase(Locale.ROOT);
                            return !TRACKING_PARAMS.contains(paramName);
                        })
                        .collect(Collectors.joining("&"));
                if (!filtered.isBlank()) {
                    cleanQuery = "?" + filtered;
                }
            }

            return scheme + "://" + host + portPart + path + cleanQuery;
        } catch (Exception ex) {
            return null;
        }
    }

    public String classifySourceType(String url, String candidateType, ResearchTarget target) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);

        // Check if host matches canonical target host
        if (target != null && target.canonicalUrl() != null) {
            try {
                URI targetUri = URI.create(target.canonicalUrl());
                URI sourceUri = URI.create(url);
                if (targetUri.getHost() != null && sourceUri.getHost() != null
                        && targetUri.getHost().equalsIgnoreCase(sourceUri.getHost())) {
                    return "OFFICIAL_WEBSITE";
                }
            } catch (Exception ignored) {}
        }

        if (lowerUrl.contains("github.com") || lowerUrl.contains("gitlab.com")) {
            return "GITHUB";
        }
        if (lowerUrl.contains("docs.") || lowerUrl.contains("/docs") || lowerUrl.contains("/reference")
                || lowerUrl.contains("readthedocs.io") || lowerUrl.contains("/documentation")) {
            return "DOCUMENTATION";
        }
        if (lowerUrl.contains(".gov") || lowerUrl.contains(".mil")) {
            return "GOVERNMENT";
        }
        if (lowerUrl.contains("linkedin.com") || lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
            return "SOCIAL_PROFILE";
        }
        if (lowerUrl.contains("reuters.com") || lowerUrl.contains("bloomberg.com") || lowerUrl.contains("techcrunch.com")
                || lowerUrl.contains("nytimes.com") || lowerUrl.contains("wsj.com") || lowerUrl.contains("bbc.com")
                || lowerUrl.contains("theverge.com") || lowerUrl.contains("forbes.com")) {
            return "NEWS";
        }
        if (lowerUrl.contains("blog.") || lowerUrl.contains("/blog")) {
            return "BLOG";
        }

        // Only retain candidateType if it was classified by client and is not falsely claiming to be official
        if (candidateType != null && !candidateType.isBlank() && !"OFFICIAL_WEBSITE".equals(candidateType)) {
            return candidateType;
        }

        return "SEARCH_RESULT";
    }

    double calculateRankedScore(double providerRelevance, String sourceType, ResearchTarget target) {
        double authorityWeight = switch (sourceType) {
            case "OFFICIAL_WEBSITE" -> 1.00;
            case "DOCUMENTATION" -> 0.90;
            case "GOVERNMENT" -> 0.85;
            case "GITHUB" -> (target != null && target.entityType() == EntityType.REPOSITORY) ? 0.95 : 0.80;
            case "SOCIAL_PROFILE" -> 0.75;
            case "NEWS" -> 0.70;
            case "BLOG" -> 0.65;
            case "SEARCH_RESULT" -> 0.60;
            default -> 0.50;
        };

        double combined = (providerRelevance * 0.5) + (authorityWeight * 0.5);
        return Math.round(combined * 100.0) / 100.0;
    }
}
