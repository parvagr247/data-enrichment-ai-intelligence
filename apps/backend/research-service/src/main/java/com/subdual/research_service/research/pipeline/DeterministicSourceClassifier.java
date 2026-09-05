package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Deterministic rule-based source classifier using domain patterns,
 * URL path structures, and canonical entity target host alignment.
 */
@Component
public class DeterministicSourceClassifier implements SourceClassifier {

    public static final String OFFICIAL_WEBSITE = "OFFICIAL_WEBSITE";
    public static final String DOCUMENTATION = "DOCUMENTATION";
    public static final String SOCIAL_PROFILE = "SOCIAL_PROFILE";
    public static final String NEWS = "NEWS";
    public static final String GITHUB = "GITHUB";
    public static final String GOVERNMENT = "GOVERNMENT";
    public static final String BLOG = "BLOG";
    public static final String SEARCH_RESULT = "SEARCH_RESULT";
    public static final String UNKNOWN = "UNKNOWN";

    @Override
    public String classify(String url, String title, String candidateType, ResearchTarget target) {
        if (url == null || url.isBlank()) {
            return UNKNOWN;
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);

        // 1. Documentation patterns
        if (lowerUrl.contains("docs.") || lowerUrl.contains("/docs")
                || lowerUrl.contains("/reference") || lowerUrl.contains("readthedocs.io")
                || lowerUrl.contains("/documentation") || lowerUrl.contains("/api-docs")) {
            return DOCUMENTATION;
        }

        // 2. Open source & repository hosts (classified as GITHUB)
        if (lowerUrl.contains("github.com") || lowerUrl.contains("gitlab.com")) {
            return GITHUB;
        }

        // 3. Social & Professional Profiles
        if (lowerUrl.contains("linkedin.com") || lowerUrl.contains("twitter.com")
                || lowerUrl.contains("x.com") || lowerUrl.contains("facebook.com")
                || lowerUrl.contains("instagram.com") || lowerUrl.contains("youtube.com")) {
            return SOCIAL_PROFILE;
        }

        // 4. Official website check: host matches canonical target host
        if (target != null && target.canonicalUrl() != null) {
            try {
                URI targetUri = URI.create(target.canonicalUrl());
                URI sourceUri = URI.create(url);
                String targetHost = extractHost(targetUri);
                String sourceHost = extractHost(sourceUri);
                if (!targetHost.isBlank() && targetHost.equalsIgnoreCase(sourceHost)) {
                    return OFFICIAL_WEBSITE;
                }
            } catch (Exception ignored) {}
        }

        // 5. Government & Public Institutions
        if (lowerUrl.contains(".gov") || lowerUrl.contains(".mil")) {
            return GOVERNMENT;
        }

        // 6. News & Major Media
        if (lowerUrl.contains("reuters.com") || lowerUrl.contains("bloomberg.com")
                || lowerUrl.contains("techcrunch.com") || lowerUrl.contains("nytimes.com")
                || lowerUrl.contains("wsj.com") || lowerUrl.contains("bbc.com")
                || lowerUrl.contains("theverge.com") || lowerUrl.contains("forbes.com")
                || lowerUrl.contains("wired.com") || lowerUrl.contains("/news/")) {
            return NEWS;
        }

        // 7. Technical Blogs
        if (lowerUrl.contains("blog.") || lowerUrl.contains("/blog")) {
            return BLOG;
        }

        // 8. Retain valid candidateType from provider if not falsely claiming OFFICIAL_WEBSITE
        if (candidateType != null && !candidateType.isBlank() && !OFFICIAL_WEBSITE.equalsIgnoreCase(candidateType)) {
            return candidateType.trim().toUpperCase(Locale.ROOT);
        }

        return SEARCH_RESULT;
    }

    private String extractHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        return uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT);
    }
}
