package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


// It is the data-hygiene and ranking filter in the research pipeline.
// Raw search engine results (from Tavily or mock providers) contain duplicates, tracking parameters, Malicious URLs, irrelevant domains, and homonym noise.
@Component
@RequiredArgsConstructor
@Slf4j
public class SourceProcessor {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "ref_src", "fbclid", "gclid", "source", "feature","mc_eid"
        );

    private final SourceClassifier sourceClassifier;
    private final RelevanceEvaluator relevanceEvaluator;

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target, int maxSources) {
        return processSources(rawSources, target, maxSources,null);
    }

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target, int maxSources, String provider) {
        if (rawSources == null || rawSources.isEmpty()) return List.of();

        Set<String> seenUrls = new HashSet<>();
        List<ResearchSource> processed = new ArrayList<>();

        for (DiscoveredSource ds : rawSources) {
            if (!isValidSource(ds)) continue;

            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null) continue;

            String dedupKey = deduplicationKey(normalizedUrl);
            if (!seenUrls.add(dedupKey)) continue;

            if (isConflictingCandidate(ds, normalizedUrl, target)) {

                log.info("[Pipeline: SOURCE_FILTERED] Candidate '{}' rejected due to conflicting identity signals with target '{}'", ds.url(), target.displayName());

                continue;
            }

            processed.add(buildResearchSource(ds, normalizedUrl, target, provider));
        }

        return rankAndLimit(processed, target, maxSources);
    }

    private static final Set<String> CONFLICTING_PROFESSIONS = Set.of(
            "dentist", "dentistry", "dental", "dds", "dmd",
            "actress", "actor", "filmography", "hollywood", "imdb", "realtor", "real estate", "broker", "physician", "pediatrician", "surgeon" );

    private boolean isConflictingCandidate(DiscoveredSource ds, String normalizedUrl, ResearchTarget target) {
        if (target == null || ds == null) return false;

        if (isPrimaryAnchor(normalizedUrl, target)) return false;

        String text = (ds.title() != null ? ds.title() : "") + " " + (ds.snippet() != null ? ds.snippet() : "");
        String lower = text.toLowerCase(Locale.ROOT);

        if (target.entityType() == EntityType.PERSON) {
            String targetContext = getTargetContext(target).toLowerCase(Locale.ROOT);
            for (String prof : CONFLICTING_PROFESSIONS) {
                if (lower.contains(prof) && !targetContext.contains(prof)) return true;
            }

            if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
                String seedOrg = target.seedOrganization().toLowerCase(Locale.ROOT);
                
                if (!lower.contains(seedOrg) && !normalizedUrl.toLowerCase(Locale.ROOT)
                    .contains(seedOrg.replaceAll("[^a-z0-9]", ""))){
                        if (isConflictingInstitution(lower)) return true;
                }
            }
        }

        return false;
    }

    private boolean isConflictingInstitution(String text) {
        return text.contains("d. e. shaw") || text.contains("deshaw")
            || text.contains("iit delhi") || text.contains("iit bombay")
            || text.contains("dental clinic") || text.contains("family dentistry");
    }

    private String getTargetContext(ResearchTarget target) {
        StringBuilder sb = new StringBuilder();
        if (target.displayName() != null) sb.append(target.displayName()).append(" ");
        if (target.seedOrganization() != null) sb.append(target.seedOrganization()).append(" ");
        if (target.seedRole() != null) sb.append(target.seedRole()).append(" ");
        if (target.metadata() != null) {
            target.metadata().values().forEach(v -> sb.append(v).append(" "));
        }
        return sb.toString();
    }

    private boolean isPrimaryAnchor(String url, ResearchTarget target) {
        if (url == null || target == null) return false;
        if (target.canonicalUrl() != null && isSameUrl(url, target.canonicalUrl())) return true;
        if (target.rawUrl() != null && isSameUrl(url, target.rawUrl())) return true;
        return false;
    }

    private boolean isValidSource(DiscoveredSource ds) {
        return ds != null && ds.url() != null && !ds.url().isBlank();
    }

    private ResearchSource buildResearchSource(DiscoveredSource ds, String normalizedUrl, ResearchTarget target, String provider) {
        String title = resolveTitle(ds.title(), normalizedUrl);
        
        String sourceType = classifySourceType(normalizedUrl, ds.sourceType(), target);

        Instant retrievedAt = ds.retrievedAt() != null ? ds.retrievedAt() : Instant.now();
        double providerRelevance = ds.relevance() != null ? ds.relevance() : 0.80;
        double qualityScore = calculateRankedScore(providerRelevance, sourceType, normalizedUrl, title, target);
        String domain = ResearchSource.extractDomain(normalizedUrl);

        return new ResearchSource(
            normalizedUrl, title, sourceType, retrievedAt,
            providerRelevance, qualityScore, ds.snippet(), domain, provider
        );
    }

    private String resolveTitle(String candidateTitle, String fallbackUrl) {
        return (candidateTitle != null && !candidateTitle.isBlank()) ? candidateTitle.trim() : fallbackUrl;
    }

    private List<ResearchSource> rankAndLimit(List<ResearchSource> sources, ResearchTarget target, int maxSources) {
        sources.sort((s1, s2) -> {
            boolean isAnchor1 = isPrimaryAnchor(s1, target);
            boolean isAnchor2 = isPrimaryAnchor(s2, target);
            if (isAnchor1 && !isAnchor2) return -1;
            if (!isAnchor1 && isAnchor2) return 1;
            return Double.compare(s2.qualityScore(), s1.qualityScore());
        });
        int limit = Math.min(sources.size(), Math.max(1, maxSources));
        return sources.subList(0, limit);
    }

    private boolean isPrimaryAnchor(ResearchSource source, ResearchTarget target) {

        if (source == null || target == null) return false;
        if ("PRIMARY_ANCHOR".equalsIgnoreCase(source.sourceType())) return true;
        if (target.canonicalUrl() != null && isSameUrl(source.url(), target.canonicalUrl())) return true;
        if (target.rawUrl() != null && isSameUrl(source.url(), target.rawUrl())) return true;

        return false;
    }

    private boolean isSameUrl(String u1, String u2) {
        if (u1 == null || u2 == null) return false;

        String s1 = u1.trim().replaceFirst("^https?://(www\\.)?", "").replaceFirst("/+$", "");

        String s2 = u2.trim().replaceFirst("^https?://(www\\.)?", "").replaceFirst("/+$", "");

        return s1.equalsIgnoreCase(s2);
    }

    public String normalizeDiscoveredUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());

            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;

            if (!isSupportedScheme(scheme)) return null;

            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;

            if (host == null || host.isBlank()) return null;

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

    private String deduplicationKey(String url) {
        if (url == null) return "";
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.length() > 1 && lower.endsWith("/")) {
            return lower.substring(0, lower.length() - 1);
        }
        return lower;
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

    public double calculateRankedScore(double providerRelevance, String sourceType, String url, String title, ResearchTarget target) {
        return relevanceEvaluator.evaluateRelevance(providerRelevance, sourceType, url, title, target);
    }

    public double calculateRankedScore(double providerRelevance, String sourceType, ResearchTarget target) {
        return calculateRankedScore(providerRelevance, sourceType, null, null, target);
    }
}
