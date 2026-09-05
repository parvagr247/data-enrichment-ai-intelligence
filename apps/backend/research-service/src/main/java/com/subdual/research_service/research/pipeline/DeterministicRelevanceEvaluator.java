package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class DeterministicRelevanceEvaluator implements RelevanceEvaluator {

    @Override
    public double evaluateRelevance(DiscoveredSource source, String classifiedType, ResearchTarget target) {
        if (source == null) {
            return 0.50;
        }
        double providerScore = source.relevance() != null ? source.relevance() : 0.80;
        return evaluateRelevance(providerScore, classifiedType, source.url(), source.title(), target);
    }

    @Override
    public double evaluateRelevance(double providerRelevance, String sourceType, String url, String title, ResearchTarget target) {
        if (isAnchorUrl(url, target) || "PRIMARY_ANCHOR".equalsIgnoreCase(sourceType)) {
            return 1.00;
        }

        double boundedProvider = Math.max(0.0, Math.min(1.0, providerRelevance));
        double authorityWeight = calculateAuthorityWeight(sourceType, target);
        double combined = (boundedProvider * 0.50) + (authorityWeight * 0.50);
        double finalScore = applyHostAlignmentBonus(combined, target, url, sourceType);

        return roundScore(finalScore);
    }

    private double calculateAuthorityWeight(String sourceType, ResearchTarget target) {
        return switch (sourceType != null ? sourceType : "") {
            case "PRIMARY_ANCHOR", "OFFICIAL_WEBSITE" -> 1.00;
            case "DOCUMENTATION" -> 0.90;
            case "GOVERNMENT" -> 0.85;
            case "GITHUB" -> (target != null && target.entityType() == EntityType.REPOSITORY) ? 0.95 : 0.80;
            case "SOCIAL_PROFILE" -> 0.75;
            case "NEWS" -> 0.70;
            case "BLOG" -> 0.65;
            case "SEARCH_RESULT" -> 0.60;
            default -> 0.50;
        };
    }

    private double applyHostAlignmentBonus(double score, ResearchTarget target, String url, String sourceType) {
        if (target == null || target.canonicalUrl() == null || url == null) {
            return score;
        }

        try {
            URI targetUri = URI.create(target.canonicalUrl());
            URI sourceUri = URI.create(url);

            double scoreWithHost = applySameHostBonus(score, targetUri, sourceUri, sourceType);
            return applySlugMatchBonus(scoreWithHost, targetUri, url);
        } catch (Exception ignored) {
            return score;
        }
    }

    private double applySameHostBonus(double score, URI targetUri, URI sourceUri, String sourceType) {
        String targetHost = extractHost(targetUri);
        String sourceHost = extractHost(sourceUri);
        if (!targetHost.isBlank() && targetHost.equalsIgnoreCase(sourceHost) && !"OFFICIAL_WEBSITE".equals(sourceType)) {
            return Math.min(1.00, score + 0.05);
        }
        return score;
    }

    private double applySlugMatchBonus(double score, URI targetUri, String url) {
        String targetSlug = extractSlug(targetUri.getPath());
        if (targetSlug != null && !targetSlug.isBlank() && url.toLowerCase(Locale.ROOT).contains(targetSlug.toLowerCase(Locale.ROOT))) {
            return Math.min(1.00, score + 0.10);
        }
        return score;
    }

    private boolean isAnchorUrl(String url, ResearchTarget target) {
        if (target == null || url == null) {
            return false;
        }
        if (target.canonicalUrl() != null && isSameUrl(url, target.canonicalUrl())) {
            return true;
        }
        if (target.rawUrl() != null && isSameUrl(url, target.rawUrl())) {
            return true;
        }
        return false;
    }

    private boolean isSameUrl(String u1, String u2) {
        return com.subdual.research_service.util.UrlNormalizer.isSameUrl(u1, u2);
    }

    private String extractSlug(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i].trim();
            if (!seg.isBlank() && !seg.equalsIgnoreCase("in") && !seg.equalsIgnoreCase("profile")) {
                return seg;
            }
        }
        return null;
    }

    private String extractHost(URI uri) {
        return uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }
}
