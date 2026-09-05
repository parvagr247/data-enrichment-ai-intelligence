package com.subdual.research_service.response;

import com.subdual.research_service.diagnostics.ResearchDiagnostics;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;
import com.subdual.research_service.dto.response.ResearchResponse;
import com.subdual.research_service.dto.response.ResearchResult;
import com.subdual.research_service.dto.response.SourceItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory responsible for creating ResearchResponse DTOs,
 * mapping domain representations to response views, and calculating status.
 */
@Component
public class ResearchResponseFactory {

    public ResearchResponse createResponse(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            Map<String, EvidenceTuple> attributes,
            int totalDiscovered,
            long executionTimeMs,
            ResearchDiagnostics diagnostics,
            String provider
    ) {
        List<SourceItem> sourceItems = rankedSources != null
                ? rankedSources.stream().map(this::toSourceItem).toList()
                : List.of();

        ResearchResult result = new ResearchResult(
                target.displayName(),
                target.entityType(),
                target.canonicalUrl(),
                attributes != null ? attributes : Map.of()
        );

        Map<String, Object> metadata = buildMetadata(
                provider,
                totalDiscovered,
                sourceItems.size(),
                result.attributes().size(),
                diagnostics
        );

        boolean hasDegradedSources = diagnostics != null && diagnostics.hasDegradedSources();
        ResearchStatus status = hasDegradedSources
                ? ResearchStatus.PARTIAL
                : ResearchStatus.COMPLETED;

        List<String> warnings = diagnostics != null
                ? diagnostics.warnings()
                : List.of();

        return new ResearchResponse(
                status,
                target.entityId(),
                result,
                sourceItems,
                executionTimeMs,
                metadata,
                warnings
        );
    }

    public SourceItem toSourceItem(ResearchSource source) {
        return new SourceItem(
                source.url(),
                source.title(),
                source.snippet(),
                source.sourceType(),
                source.domain(),
                source.provider(),
                source.retrievedAt(),
                source.relevance()
        );
    }

    private Map<String, Object> buildMetadata(
            String provider,
            int totalDiscovered,
            int rankedCount,
            int attributeCount,
            ResearchDiagnostics diagnostics
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider != null ? provider : "unknown");
        metadata.put("totalSourcesDiscovered", totalDiscovered);
        metadata.put("totalSourcesRanked", rankedCount);
        metadata.put("attributesExtracted", attributeCount);
        metadata.put("warningsCount", diagnostics != null ? diagnostics.warningCount() : 0);
        return metadata;
    }
}
