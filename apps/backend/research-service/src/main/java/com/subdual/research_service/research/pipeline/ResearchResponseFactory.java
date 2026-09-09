package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.research.api.ResearchResponse;
import com.subdual.research_service.research.api.ResearchResult;
import com.subdual.research_service.research.api.SourceItem;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<SourceItem> sourceItems = mapToSourceItems(rankedSources);
        ResearchResult result = buildResearchResult(target, attributes);
        Map<String, Object> metadata = buildMetadata(
                provider,
                totalDiscovered,
                sourceItems.size(),
                result.attributes().size(),
                diagnostics
        );
        ResearchStatus status = determineResearchStatus(target, rankedSources, diagnostics, attributes);
        List<String> warnings = extractWarnings(diagnostics);

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

    private List<SourceItem> mapToSourceItems(List<ResearchSource> rankedSources) {
        if (rankedSources == null) {
            return List.of();
        }
        return rankedSources.stream().map(this::toSourceItem).toList();
    }

    private ResearchResult buildResearchResult(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        return new ResearchResult(
                target.displayName(),
                target.entityType(),
                target.canonicalUrl(),
                attributes != null ? attributes : Map.of()
        );
    }

    private ResearchStatus determineResearchStatus(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            ResearchDiagnostics diagnostics,
            Map<String, EvidenceTuple> attributes
    ) {
        boolean hasSources = rankedSources != null && !rankedSources.isEmpty();
        boolean hasExtractedAttributes = attributes != null && !attributes.isEmpty();

        if (diagnostics != null && diagnostics.hasDegradedSources() && !hasExtractedAttributes) {
            return ResearchStatus.PARTIAL;
        }

        if (!hasSources && !hasExtractedAttributes) {
            return ResearchStatus.FAILED;
        }

        return hasExtractedAttributes ? ResearchStatus.COMPLETED : ResearchStatus.PARTIAL;
    }

    private List<String> extractWarnings(ResearchDiagnostics diagnostics) {
        return diagnostics != null ? diagnostics.warnings() : List.of();
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
