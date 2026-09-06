package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.discovery.model.QueryIntent;
import com.subdual.research_service.extraction.support.NameNormalizer;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight source ranking mechanism based on relevance, source type, entity match, and reliability (Task 56).
 */
public final class SourceRanker {

    private SourceRanker() {}

    public static List<DiscoveredSource> rankSources(
            List<DiscoveredSource> sources,
            ResearchTarget target,
            QueryIntent intent
    ) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<ScoredSource> scored = new ArrayList<>(sources.size());
        for (DiscoveredSource src : sources) {
            double score = computeSourceScore(src, target, intent);
            scored.add(new ScoredSource(src, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredSource::score).reversed());

        List<DiscoveredSource> ranked = new ArrayList<>(scored.size());
        for (ScoredSource s : scored) {
            DiscoveredSource original = s.source();
            ranked.add(new DiscoveredSource(
                    original.url(),
                    original.title(),
                    original.sourceType(),
                    original.retrievedAt(),
                    Math.min(1.0, Math.max(0.0, s.score())),
                    original.snippet()
            ));
        }
        return ranked;
    }

    private static double computeSourceScore(DiscoveredSource src, ResearchTarget target, QueryIntent intent) {
        double score = src.relevance() != null ? src.relevance() : 0.50;

        String targetCanonical = target != null ? target.canonicalUrl() : null;
        SourceType type = SourceTypeClassifier.classify(src.url(), targetCanonical);
        SourceReliability reliability = SourceTypeClassifier.determineReliability(type);

        score += switch (type) {
            case OFFICIAL_WEBSITE -> 0.30;
            case REPOSITORY -> (target != null && target.entityType() == EntityType.REPOSITORY) ? 0.25 : 0.15;
            case PROFESSIONAL_PROFILE -> (target != null && target.entityType() == EntityType.PERSON) ? 0.25 : 0.15;
            case DOCUMENTATION -> 0.20;
            case NEWS -> 0.15;
            case DIRECTORY -> 0.10;
            case SOCIAL_PROFILE -> 0.05;
            case SEARCH_RESULT, OTHER -> 0.0;
        };

        score += switch (reliability) {
            case HIGH -> 0.15;
            case MEDIUM -> 0.05;
            case LOW -> -0.10;
            case UNKNOWN -> 0.0;
        };

        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            String lowerName = target.displayName().toLowerCase(Locale.ROOT);
            String normName = NameNormalizer.normalize(target.displayName()).toLowerCase(Locale.ROOT);

            String title = src.title() != null ? src.title().toLowerCase(Locale.ROOT) : "";
            String snippet = src.snippet() != null ? src.snippet().toLowerCase(Locale.ROOT) : "";

            if (title.contains(lowerName) || (!normName.isBlank() && title.contains(normName))) {
                score += 0.20;
            }
            if (snippet.contains(lowerName) || (!normName.isBlank() && snippet.contains(normName))) {
                score += 0.10;
            }
        }

        return score;
    }

    private record ScoredSource(DiscoveredSource source, double score) {}
}
