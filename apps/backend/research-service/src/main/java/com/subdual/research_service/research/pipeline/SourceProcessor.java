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

/**
 * Normalizes discovered search results, applies source classification,
 * evaluates composite relevance/quality scores, deduplicates, and ranks sources.
 */
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

    public SourceProcessor() {
        this(new DeterministicSourceClassifier(), new DeterministicRelevanceEvaluator());
    }

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
            if (ds == null || ds.url() == null || ds.url().isBlank()) {
                continue;
            }

            // 1. URL Normalization
            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null || seenUrls.contains(normalizedUrl)) {
                continue;
            }
            seenUrls.add(normalizedUrl);

            String title = (ds.title() != null && !ds.title().isBlank())
                    ? ds.title().trim()
                    : normalizedUrl;

            // 2. Source Classification
            String sourceType = classifySourceType(normalizedUrl, ds.sourceType(), target);

            // 3. Relevance & Quality Evaluation
            Instant retrievedAt = ds.retrievedAt() != null ? ds.retrievedAt() : Instant.now();
            double providerRelevance = ds.relevance() != null ? ds.relevance() : 0.80;
            double qualityScore = calculateRankedScore(providerRelevance, sourceType, target);

            String domain = ResearchSource.extractDomain(normalizedUrl);

            processed.add(new ResearchSource(
                    normalizedUrl,
                    title,
                    sourceType,
                    retrievedAt,
                    providerRelevance,
                    qualityScore,
                    ds.snippet(),
                    domain,
                    provider
            ));
        }

        // 4. Rank by composite quality score descending
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
        return sourceClassifier.classify(url, candidateType, target);
    }

    public double calculateRankedScore(double providerRelevance, String sourceType, ResearchTarget target) {
        return relevanceEvaluator.evaluateRelevance(providerRelevance, sourceType, null, null, target);
    }
}
