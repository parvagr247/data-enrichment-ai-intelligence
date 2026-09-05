package com.subdual.research_service.extraction;

import com.subdual.research_service.source.ContentExtractor;
import com.subdual.research_service.source.ExtractedDocument;
import com.subdual.research_service.source.FetchedContent;
import com.subdual.research_service.source.WebContentFetcher;
import com.subdual.research_service.configuration.ResearchPipelineProperties;
import com.subdual.research_service.diagnostics.ResearchDiagnostics;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of SourceEvidenceService coordinating web fetching,
 * document extraction, entity resolution, and grounded evidence extraction.
 */
@Component
public class DefaultSourceEvidenceService implements SourceEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSourceEvidenceService.class);

    private final WebContentFetcher webContentFetcher;
    private final ContentExtractor contentExtractor;
    private final EntityResolver entityResolver;
    private final EvidenceExtractor evidenceExtractor;
    private final ResearchPipelineProperties pipelineProperties;

    @Autowired
    public DefaultSourceEvidenceService(
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties
    ) {
        this.webContentFetcher = webContentFetcher;
        this.contentExtractor = contentExtractor;
        this.entityResolver = entityResolver;
        this.evidenceExtractor = evidenceExtractor;
        this.pipelineProperties = pipelineProperties;
    }

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
