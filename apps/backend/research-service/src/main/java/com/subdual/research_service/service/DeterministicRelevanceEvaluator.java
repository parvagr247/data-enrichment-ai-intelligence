package com.subdual.research_service.service;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Deterministic relevance evaluator calculating an explainable composite score
 * combining provider relevance, domain authority, and target entity alignment.
 */
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
        double boundedProvider = Math.max(0.0, Math.min(1.0, providerRelevance));

        double authorityWeight = switch (sourceType != null ? sourceType : "") {
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

        // Combine base provider relevance and domain authority equally
        double combined = (boundedProvider * 0.50) + (authorityWeight * 0.50);

        // Alignment check: host matches canonical target host
        if (target != null && target.canonicalUrl() != null && url != null) {
            try {
                URI targetUri = URI.create(target.canonicalUrl());
                URI sourceUri = URI.create(url);
                String targetHost = targetUri.getHost() != null ? targetUri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
                String sourceHost = sourceUri.getHost() != null ? sourceUri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
                if (!targetHost.isBlank() && targetHost.equalsIgnoreCase(sourceHost) && !"OFFICIAL_WEBSITE".equals(sourceType)) {
                    combined = Math.min(1.00, combined + 0.05);
                }
            } catch (Exception ignored) {}
        }

        return Math.round(combined * 100.0) / 100.0;
    }
}
