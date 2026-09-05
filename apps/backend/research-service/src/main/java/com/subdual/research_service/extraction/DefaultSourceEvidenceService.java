package com.subdual.research_service.extraction;

import com.subdual.research_service.integration.web.FetchedContent;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ContentExtractor;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
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
        ExtractedDocument doc;
        if (fetched == null || !fetched.success()) {
            handleInaccessibleSource(target, source, fetched, diagnostics);
            if (source.snippet() != null && source.snippet().trim().length() > 50) {
                log.info("Using search provider snippet fallback for blocked source '{}'", source.url());
                doc = new ExtractedDocument(source.url(), source.title(), source.snippet(), null, source.domain());
            } else {
                return;
            }
        } else {
            int maxLength = pipelineProperties != null ? pipelineProperties.maxContentLength() : 50000;
            doc = contentExtractor.extract(fetched, maxLength);
        }

        EntityResolver.ResolutionResult resolution = entityResolver.resolve(target, doc);

        resolutions.put(source.url(), resolution);
        if (doc.url() != null) {
            resolutions.put(doc.url(), resolution);
        }

        if (resolution != null && resolution.matched()) {
            extractedDocuments.add(doc);
        } else {
            String reason = resolution != null ? resolution.reason() : "resolution failed";
            log.info("Document '{}' rejected by entity resolution: {}", source.url(), reason);
        }
    }

    private void handleInaccessibleSource(
            ResearchTarget target,
            ResearchSource source,
            FetchedContent fetched,
            ResearchDiagnostics diagnostics
    ) {
        boolean isPrimary = isPrimaryAnchor(source, target);
        if (isPrimary) {
            String domain = source.domain() != null ? source.domain() : "primary source";
            int statusCode = fetched != null ? fetched.statusCode() : 0;
            String statusMsg = statusCode > 0 ? "HTTP " + statusCode : (fetched != null ? fetched.errorMessage() : "Inaccessible");
            log.info("Primary target source ({}) could not be directly fetched ({}); continuing with corroborating sources.", domain, statusMsg);
            if (diagnostics != null) {
                diagnostics.recordPrimaryInaccessible(domain, statusMsg);
            }
        } else {
            String errorMsg = fetched != null ? fetched.errorMessage() : "Inaccessible";
            log.debug("Skipping unretrievable source URL '{}': {}", source.url(), errorMsg);
            if (diagnostics != null) {
                diagnostics.recordSourceSkipped(source.domain(), errorMsg);
            }
        }
    }

    private boolean isPrimaryAnchor(ResearchSource source, ResearchTarget target) {
        if (source == null || target == null) {
            return false;
        }
        if ("PRIMARY_ANCHOR".equalsIgnoreCase(source.sourceType())) {
            return true;
        }
        if (target.canonicalUrl() != null && isSameUrl(source.url(), target.canonicalUrl())) {
            return true;
        }
        if (target.rawUrl() != null && isSameUrl(source.url(), target.rawUrl())) {
            return true;
        }
        return false;
    }

    private boolean isSameUrl(String u1, String u2) {
        if (u1 == null || u2 == null) {
            return false;
        }
        String s1 = u1.trim().replaceFirst("^https?://(www\\.)?", "").replaceFirst("/+$", "");
        String s2 = u2.trim().replaceFirst("^https?://(www\\.)?", "").replaceFirst("/+$", "");
        return s1.equalsIgnoreCase(s2);
    }

    @Override
    public void applyTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        evidenceExtractor.applyTargetFields(target, attributes);
    }
}
