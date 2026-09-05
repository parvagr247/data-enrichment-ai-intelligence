package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

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

        if (isDocumentation(lowerUrl)) return DOCUMENTATION;
        if (isRepositoryHost(lowerUrl)) return GITHUB;
        if (isSocialProfile(lowerUrl)) return SOCIAL_PROFILE;
        if (isOfficialWebsite(url, target)) return OFFICIAL_WEBSITE;
        if (isGovernment(lowerUrl)) return GOVERNMENT;
        if (isNews(lowerUrl)) return NEWS;
        if (isBlog(lowerUrl)) return BLOG;

        return resolveCandidateType(candidateType);
    }

    private boolean isDocumentation(String lowerUrl) {
        return lowerUrl.contains("docs.")
                || lowerUrl.contains("/docs")
                || lowerUrl.contains("/reference")
                || lowerUrl.contains("readthedocs.io")
                || lowerUrl.contains("/documentation")
                || lowerUrl.contains("/api-docs");
    }

    private boolean isRepositoryHost(String lowerUrl) {
        return lowerUrl.contains("github.com") || lowerUrl.contains("gitlab.com");
    }

    private boolean isSocialProfile(String lowerUrl) {
        return lowerUrl.contains("linkedin.com")
                || lowerUrl.contains("twitter.com")
                || lowerUrl.contains("x.com")
                || lowerUrl.contains("facebook.com")
                || lowerUrl.contains("instagram.com")
                || lowerUrl.contains("youtube.com");
    }

    private boolean isOfficialWebsite(String url, ResearchTarget target) {
        if (target == null || target.canonicalUrl() == null) {
            return false;
        }
        try {
            URI targetUri = URI.create(target.canonicalUrl());
            URI sourceUri = URI.create(url);
            String targetHost = extractHost(targetUri);
            String sourceHost = extractHost(sourceUri);
            return !targetHost.isBlank() && targetHost.equalsIgnoreCase(sourceHost);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isGovernment(String lowerUrl) {
        return lowerUrl.contains(".gov") || lowerUrl.contains(".mil");
    }

    private boolean isNews(String lowerUrl) {
        return lowerUrl.contains("reuters.com")
                || lowerUrl.contains("bloomberg.com")
                || lowerUrl.contains("techcrunch.com")
                || lowerUrl.contains("nytimes.com")
                || lowerUrl.contains("wsj.com")
                || lowerUrl.contains("bbc.com")
                || lowerUrl.contains("theverge.com")
                || lowerUrl.contains("forbes.com")
                || lowerUrl.contains("wired.com")
                || lowerUrl.contains("/news/");
    }

    private boolean isBlog(String lowerUrl) {
        return lowerUrl.contains("blog.") || lowerUrl.contains("/blog");
    }

    private String resolveCandidateType(String candidateType) {
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
