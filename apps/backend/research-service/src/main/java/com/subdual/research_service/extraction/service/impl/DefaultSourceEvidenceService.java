package com.subdual.research_service.extraction.service.impl;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.service.SourceEvidenceService;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.extraction.document.ContentExtractor;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.extractor.EvidenceExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.integration.web.FetchedContent;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultSourceEvidenceService implements SourceEvidenceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSourceEvidenceService.class);

    private final WebContentFetcher webContentFetcher;
    private final ContentExtractor contentExtractor;
    private final EntityResolver entityResolver;
    private final EvidenceExtractor evidenceExtractor;
    private final ResearchPipelineProperties pipelineProperties;
    private final com.subdual.research_service.discovery.cache.ThreadSafeSourceCache sourceCache;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultSourceEvidenceService(
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties,
            com.subdual.research_service.discovery.cache.@org.jspecify.annotations.Nullable ThreadSafeSourceCache sourceCache
    ) {
        this.webContentFetcher = webContentFetcher;
        this.contentExtractor = contentExtractor;
        this.entityResolver = entityResolver;
        this.evidenceExtractor = evidenceExtractor;
        this.pipelineProperties = pipelineProperties;
        this.sourceCache = sourceCache != null ? sourceCache : new com.subdual.research_service.discovery.cache.ThreadSafeSourceCache();
    }

    public DefaultSourceEvidenceService(
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties
    ) {
        this(webContentFetcher, contentExtractor, entityResolver, evidenceExtractor, pipelineProperties, null);
    }

    @Override // Extracts structured evidence from ranked sources and extracted documents.
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

    @Override // Filters extracted evidence attributes to requested target fields.
    public void applyTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        evidenceExtractor.applyTargetFields(target, attributes);
    }

    private void processSource(
            ResearchTarget target,
            ResearchSource source,
            List<ExtractedDocument> extractedDocuments,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            ResearchDiagnostics diagnostics
    ) {
        ExtractedDocument doc = fetchOrFallbackDocument(source, target, diagnostics);
        if (doc == null) {
            return;
        }

        EntityResolver.ResolutionResult resolution = entityResolver.resolve(target, doc);
        recordResolution(source, doc, resolution, resolutions);
        filterAndAddDocument(doc, source.url(), resolution, extractedDocuments);
    }

    private ExtractedDocument fetchOrFallbackDocument(
            ResearchSource source,
            ResearchTarget target,
            ResearchDiagnostics diagnostics
    ) {
        FetchedContent fetched = null;
        if (sourceCache != null) {
            fetched = sourceCache.getFetchedContent(source.url()).orElse(null);
        }
        if (fetched == null) {
            fetched = webContentFetcher.fetch(source.url());
            if (fetched != null && fetched.success() && sourceCache != null) {
                sourceCache.putFetchedContent(source.url(), fetched);
            }
        }

        if (fetched != null && fetched.success()) {
            int maxLength = pipelineProperties != null ? pipelineProperties.maxContentLength() : 50000;
            return contentExtractor.extract(fetched, maxLength);
        }

        handleInaccessibleSource(target, source, fetched, diagnostics);
        return resolveSnippetFallbackDocument(source);
    }

    private ExtractedDocument resolveSnippetFallbackDocument(ResearchSource source) {
        if (source.snippet() != null && source.snippet().trim().length() > 20) {
            log.info("Using search provider snippet fallback for blocked source '{}'", source.url());
            return new ExtractedDocument(source.url(), source.title(), source.snippet(), null, source.domain(), "SEARCH_SNIPPET");
        }
        return null;
    }

    private void recordResolution(
            ResearchSource source,
            ExtractedDocument doc,
            EntityResolver.ResolutionResult resolution,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        resolutions.put(source.url(), resolution);
        if (doc.url() != null) {
            resolutions.put(doc.url(), resolution);
        }
    }

    private void filterAndAddDocument(
            ExtractedDocument doc,
            String sourceUrl,
            EntityResolver.ResolutionResult resolution,
            List<ExtractedDocument> extractedDocuments
    ) {
        if (resolution != null && (resolution.matched() || resolution.status() == EntityResolver.MatchStatus.AMBIGUOUS)) {
            extractedDocuments.add(doc);
        } else {
            String reason = resolution != null ? resolution.reason() : "resolution failed";
            log.info("Document '{}' rejected by entity resolution: {}", sourceUrl, reason);
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
}
