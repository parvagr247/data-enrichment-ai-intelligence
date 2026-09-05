package com.subdual.research_service.extraction;

import com.subdual.research_service.integration.web.FetchedContent;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.api.dto.EvidenceTuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultSourceEvidenceService implements SourceEvidenceService {

    private final WebContentFetcher webContentFetcher;
    private final ContentExtractor contentExtractor;
    private final EntityResolver entityResolver;
    private final EvidenceExtractor evidenceExtractor;
    private final ResearchPipelineProperties pipelineProperties;

    @Override
    public Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            ResearchDiagnostics diagnostics
    ) {
        log.info("[Pipeline: EVIDENCE_EXTRACTION] Extracting evidence from {} ranked sources for entityId: '{}'",
                rankedSources != null ? rankedSources.size() : 0,
                target != null ? target.entityId() : "unknown");

        if (target == null || rankedSources == null || rankedSources.isEmpty()) {
            return Map.of();
        }

        List<ExtractedDocument> extractedDocuments = new ArrayList<>();
        Map<String, EntityResolver.ResolutionResult> resolutions = new HashMap<>();

        for (ResearchSource source : rankedSources) {
            processSource(target, source, extractedDocuments, resolutions, diagnostics);
        }

        return evidenceExtractor.extractEvidence(target, rankedSources, extractedDocuments, resolutions);
    }

    private void processSource(
            ResearchTarget target,
            ResearchSource source,
            List<ExtractedDocument> extractedDocuments,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            ResearchDiagnostics diagnostics
    ) {
        FetchedContent fetched = webContentFetcher.fetch(source.url());
        if (fetched == null || !fetched.success()) {
            handleInaccessibleSource(source, fetched, diagnostics);
            return;
        }

        int maxLength = pipelineProperties != null ? pipelineProperties.maxContentLength() : 50000;
        ExtractedDocument doc = contentExtractor.extract(fetched, maxLength);
        extractedDocuments.add(doc);

        EntityResolver.ResolutionResult resolution = entityResolver.resolve(target, doc);
        resolutions.put(source.url(), resolution);
    }

    private void handleInaccessibleSource(
            ResearchSource source,
            FetchedContent fetched,
            ResearchDiagnostics diagnostics
    ) {
        String errorMsg = fetched != null ? fetched.errorMessage() : "Inaccessible";
        log.debug("Skipping unretrievable source URL '{}': {}", source.url(), errorMsg);
        if (diagnostics != null) {
            diagnostics.recordSourceSkipped(source.domain(), errorMsg);
        }
    }
}
