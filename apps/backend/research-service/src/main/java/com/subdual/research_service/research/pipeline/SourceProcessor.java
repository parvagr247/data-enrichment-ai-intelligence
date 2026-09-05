package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
public class SourceProcessor {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "ref_src", "fbclid", "gclid", "source", "feature", "mc_eid"
    );

    private final SourceClassifier sourceClassifier;
    private final RelevanceEvaluator relevanceEvaluator;

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target, int maxSources) {
        return processSources(rawSources, target, maxSources, null);
    }

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target, int maxSources, String provider) {
        if (rawSources == null || rawSources.isEmpty()) {
            return List.of();
        }

        Set<String> seenUrls = new HashSet<>();
        List<ResearchSource> processed = new ArrayList<>();

        for (DiscoveredSource ds : rawSources) {
            if (!isValidSource(ds)) {
                continue;
            }

            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null || !seenUrls.add(normalizedUrl)) {
                continue;
            }

            processed.add(buildResearchSource(ds, normalizedUrl, target, provider));
        }

        return rankAndLimit(processed, maxSources);
    }

    private boolean isValidSource(DiscoveredSource ds) {
        return ds != null && ds.url() != null && !ds.url().isBlank();
    }

    private ResearchSource buildResearchSource(DiscoveredSource ds, String normalizedUrl, ResearchTarget target, String provider) {
        String title = resolveTitle(ds.title(), normalizedUrl);
        String sourceType = classifySourceType(normalizedUrl, ds.sourceType(), target);

        Instant retrievedAt = ds.retrievedAt() != null ? ds.retrievedAt() : Instant.now();
        double providerRelevance = ds.relevance() != null ? ds.relevance() : 0.80;
        double qualityScore = calculateRankedScore(providerRelevance, sourceType, target);
        String domain = ResearchSource.extractDomain(normalizedUrl);

        return new ResearchSource(
                normalizedUrl,
                title,
                sourceType,
                retrievedAt,
                providerRelevance,
                qualityScore,
                ds.snippet(),
                domain,
                provider
        );
    }

    private String resolveTitle(String candidateTitle, String fallbackUrl) {
        return (candidateTitle != null && !candidateTitle.isBlank())
                ? candidateTitle.trim()
                : fallbackUrl;
    }

    private List<ResearchSource> rankAndLimit(List<ResearchSource> sources, int maxSources) {
        sources.sort(Comparator.comparingDouble(ResearchSource::qualityScore).reversed());
        int limit = Math.min(sources.size(), Math.max(1, maxSources));
        return sources.subList(0, limit);
    }

    public String normalizeDiscoveredUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!isSupportedScheme(scheme)) {
                return null;
            }

            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
            if (host == null || host.isBlank()) {
                return null;
            }

            String portPart = formatPort(scheme, uri.getPort());
            String path = normalizePath(uri.getPath());
            String cleanQuery = cleanTrackingQuery(uri.getQuery());

            return scheme + "://" + host + portPart + path + cleanQuery;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isSupportedScheme(String scheme) {
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private String normalizePath(String path) {
        return (path == null || path.isEmpty()) ? "/" : path;
    }

    private String formatPort(String scheme, int port) {
        boolean isDefaultPort = port == -1
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return isDefaultPort ? "" : ":" + port;
    }

    private String cleanTrackingQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        String filtered = Arrays.stream(rawQuery.split("&"))
                .filter(param -> {
                    String paramName = param.split("=")[0].toLowerCase(Locale.ROOT);
                    return !TRACKING_PARAMS.contains(paramName);
                })
                .collect(Collectors.joining("&"));

        return filtered.isBlank() ? "" : "?" + filtered;
    }

    public String classifySourceType(String url, String candidateType, ResearchTarget target) {
        return sourceClassifier.classify(url, candidateType, target);
    }

    public double calculateRankedScore(double providerRelevance, String sourceType, ResearchTarget target) {
        return relevanceEvaluator.evaluateRelevance(providerRelevance, sourceType, null, null, target);
    }
}
