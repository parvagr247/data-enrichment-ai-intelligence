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

    private static final Set<String> CONFLICTING_PROFESSIONS = Set.of(
            "dentist", "dentistry", "dental", "dds", "dmd",
            "actress", "actor", "filmography", "hollywood", "imdb",
            "realtor", "real estate", "broker",
            "physician", "pediatrician", "surgeon"
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
            if (!isValidSource(ds)) continue;

            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null) continue;

            String dedupKey = deduplicationKey(normalizedUrl);
            if (!seenUrls.add(dedupKey)) continue;

            if (isConflictingCandidate(ds, normalizedUrl, target)) {
                log.info("[Pipeline: SOURCE_FILTERED] Candidate '{}' rejected due to conflicting identity signals with target '{}'",
                        ds.url(), target.displayName());
                continue;
            }

            processed.add(buildResearchSource(ds, normalizedUrl, target, provider));
        }

        return rankAndLimit(processed, target, maxSources);
    }

    private boolean isConflictingCandidate(DiscoveredSource ds, String normalizedUrl, ResearchTarget target) {
        if (!shouldCheckCandidateConflicts(ds, normalizedUrl, target)) {
            return false;
        }

        String lower = extractCandidateText(ds);
        return hasConflictingProfession(lower, target)
                || hasConflictingOrganization(lower, normalizedUrl, target);
    }

    private boolean shouldCheckCandidateConflicts(DiscoveredSource ds, String normalizedUrl, ResearchTarget target) {
        if (target == null || ds == null) return false;
        if (isPrimaryAnchor(normalizedUrl, target)) return false;
        return target.entityType() == EntityType.PERSON;
    }

    private String extractCandidateText(DiscoveredSource ds) {
        String title = ds.title() != null ? ds.title() : "";
        String snippet = ds.snippet() != null ? ds.snippet() : "";
        return (title + " " + snippet).toLowerCase(Locale.ROOT);
    }

    private boolean hasConflictingProfession(String lowerText, ResearchTarget target) {
        String targetContext = getTargetContext(target).toLowerCase(Locale.ROOT);
        for (String prof : CONFLICTING_PROFESSIONS) {
            if (lowerText.contains(prof) && !targetContext.contains(prof)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConflictingOrganization(String lowerText, String normalizedUrl, ResearchTarget target) {
        if (target.seedOrganization() == null || target.seedOrganization().isBlank()) {
            return false;
        }

        String seedOrg = target.seedOrganization().toLowerCase(Locale.ROOT);
        String cleanOrg = seedOrg.replaceAll("[^a-z0-9]", "");
        boolean urlHasOrg = normalizedUrl.toLowerCase(Locale.ROOT).contains(cleanOrg);

        if (!lowerText.contains(seedOrg) && !urlHasOrg) {
            return isConflictingInstitution(lowerText);
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
        sources.sort(createRankingComparator(target));
        int limit = Math.min(sources.size(), Math.max(1, maxSources));
        return sources.subList(0, limit);
    }

    private Comparator<ResearchSource> createRankingComparator(ResearchTarget target) {
        return (s1, s2) -> {
            boolean isAnchor1 = isPrimaryAnchor(s1, target);
            boolean isAnchor2 = isPrimaryAnchor(s2, target);
            if (isAnchor1 && !isAnchor2) return -1;
            if (!isAnchor1 && isAnchor2) return 1;
            return Double.compare(s2.qualityScore(), s1.qualityScore());
        };
    }

    private boolean isPrimaryAnchor(ResearchSource source, ResearchTarget target) {
        if (source == null || target == null) return false;
        if ("PRIMARY_ANCHOR".equalsIgnoreCase(source.sourceType())) return true;
        if (target.canonicalUrl() != null && isSameUrl(source.url(), target.canonicalUrl())) return true;
        if (target.rawUrl() != null && isSameUrl(source.url(), target.rawUrl())) return true;
        return false;
    }

    private boolean isSameUrl(String u1, String u2) {
        return com.subdual.research_service.util.UrlNormalizer.isSameUrl(u1, u2);
    }

    public String normalizeDiscoveredUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String portPart = (uri.getPort() == -1 || ("http".equals(scheme) && uri.getPort() == 80) || ("https".equals(scheme) && uri.getPort() == 443)) ? "" : ":" + uri.getPort();
            String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
            String cleanQuery = com.subdual.research_service.util.UrlNormalizer.cleanQueryParameters(uri.getQuery(), true);
            return scheme + "://" + host + portPart + path + cleanQuery;
        } catch (Exception ex) {
            return null;
        }
    }

    private String deduplicationKey(String url) {
        return com.subdual.research_service.util.UrlNormalizer.toComparisonKey(url);
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
