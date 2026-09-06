package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies discovered URLs into SourceType and determines baseline SourceReliability (Tasks 58, 59).
 */
public final class SourceTypeClassifier {

    private static final Set<String> REPO_DOMAINS = Set.of(
            "github.com", "gitlab.com", "bitbucket.org", "sourceforge.net"
    );

    private static final Set<String> PROFESSIONAL_DOMAINS = Set.of(
            "linkedin.com"
    );

    private static final Set<String> SOCIAL_DOMAINS = Set.of(
            "twitter.com", "x.com", "facebook.com", "instagram.com", "youtube.com", "reddit.com"
    );

    private static final Set<String> NEWS_DOMAINS = Set.of(
            "techcrunch.com", "reuters.com", "bloomberg.com", "forbes.com",
            "nytimes.com", "wsj.com", "wired.com", "theverge.com", "bbc.com", "cnbc.com"
    );

    private static final Set<String> DIRECTORY_DOMAINS = Set.of(
            "crunchbase.com", "zoominfo.com", "pitchbook.com", "wikipedia.org", "wikidata.org"
    );

    private SourceTypeClassifier() {}

    public static SourceType classify(String url, String targetCanonicalUrl) {
        if (url == null || url.isBlank()) {
            return SourceType.OTHER;
        }

        String host = extractHost(url);
        if (host.isBlank()) {
            return SourceType.OTHER;
        }

        if (targetCanonicalUrl != null && !targetCanonicalUrl.isBlank()) {
            String targetHost = extractHost(targetCanonicalUrl);
            if (!targetHost.isBlank() && host.equalsIgnoreCase(targetHost)) {
                return SourceType.OFFICIAL_WEBSITE;
            }
        }

        if (matchesDomain(host, REPO_DOMAINS)) {
            return SourceType.REPOSITORY;
        }

        if (matchesDomain(host, PROFESSIONAL_DOMAINS)) {
            return SourceType.PROFESSIONAL_PROFILE;
        }

        if (matchesDomain(host, SOCIAL_DOMAINS)) {
            return SourceType.SOCIAL_PROFILE;
        }

        if (matchesDomain(host, NEWS_DOMAINS)) {
            return SourceType.NEWS;
        }

        if (host.startsWith("docs.") || host.contains("readthedocs.io") || host.startsWith("developer.")) {
            return SourceType.DOCUMENTATION;
        }

        if (matchesDomain(host, DIRECTORY_DOMAINS)) {
            return SourceType.DIRECTORY;
        }

        return SourceType.SEARCH_RESULT;
    }

    public static SourceReliability determineReliability(SourceType sourceType) {
        if (sourceType == null) return SourceReliability.UNKNOWN;
        return switch (sourceType) {
            case OFFICIAL_WEBSITE, REPOSITORY, PROFESSIONAL_PROFILE, DOCUMENTATION -> SourceReliability.HIGH;
            case NEWS, DIRECTORY -> SourceReliability.MEDIUM;
            case SOCIAL_PROFILE, SEARCH_RESULT -> SourceReliability.LOW;
            case OTHER -> SourceReliability.UNKNOWN;
        };
    }

    private static boolean matchesDomain(String host, Set<String> domains) {
        for (String domain : domains) {
            if (host.equalsIgnoreCase(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            String h = uri.getHost();
            return h != null ? h.replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
