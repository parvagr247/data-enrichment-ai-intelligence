package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.ai.AiEvidenceEnricher;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.extractor.field.FieldExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.extraction.support.TargetFieldNormalizer;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cohesive evidence extractor following Single Responsibility and Dependency Inversion Principles.
 * Coordinates document-level, entity-specific, and field-specific evidence extraction.
 */
@Component
@RequiredArgsConstructor
public class EvidenceExtractor {

    private final EvidenceMerger evidenceMerger;
    private final CommonEvidenceExtractor commonExtractor;
    private final PersonEvidenceExtractor personExtractor;
    private final OrganizationEvidenceExtractor organizationExtractor;
    private final RepositoryEvidenceExtractor repositoryExtractor;
    private final ProductEvidenceExtractor productExtractor;
    private final AiEvidenceEnricher aiEvidenceEnricher;
    private final TargetFieldNormalizer targetFieldNormalizer;

    @Autowired(required = false)
    private List<FieldExtractor> fieldExtractors = Collections.emptyList();

    public Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> sources,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (!hasExtractableContent(target, documents)) {
            return new LinkedHashMap<>();
        }

        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();
        extractCommonAttributes(target, sources, documents, resolutions, attributes);
        extractEntityTypeAttributes(target, documents, resolutions, attributes);
        extractFieldSpecificAttributes(target, documents, resolutions, attributes);
        enrichWithAiIfAvailable(target, documents, resolutions, attributes);
        applyTargetFieldsIfRequested(target, attributes);

        return attributes;
    }

    private void extractFieldSpecificAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        if (fieldExtractors == null || fieldExtractors.isEmpty() || documents == null) {
            return;
        }

        List<String> targetFields = target.targetFields();
        for (ExtractedDocument doc : documents) {
            if (!CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)) {
                continue;
            }
            for (FieldExtractor fe : fieldExtractors) {
                boolean isRequested = targetFields == null || targetFields.isEmpty()
                        || targetFields.stream().anyMatch(tf -> fe.supports(tf, target.entityType()));
                if (isRequested) {
                    EvidenceTuple tuple = fe.extract(doc, target);
                    if (tuple != null && tuple.value() != null && !tuple.value().isBlank()) {
                        evidenceMerger.mergeAttribute(
                                attributes,
                                fe.fieldKey(),
                                tuple.value(),
                                tuple.sourceUrl(),
                                tuple.evidenceSnippet(),
                                tuple.confidence(),
                                tuple.sourceType(),
                                tuple.extractionMethod()
                        );
                    }
                }
            }
        }
    }

    private boolean hasExtractableContent(ResearchTarget target, List<ExtractedDocument> documents) {
        return target != null && documents != null && !documents.isEmpty();
    }

    private void extractCommonAttributes(
            ResearchTarget target,
            List<ResearchSource> sources,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        putIfPresent(attributes, "description", commonExtractor.extractDescription(target, documents, sources, resolutions));
        putIfPresent(attributes, "title", commonExtractor.extractTitle(target, documents, sources, resolutions));
        putIfPresent(attributes, "site_name", commonExtractor.extractSiteName(documents, resolutions));
    }

    private void extractEntityTypeAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        EntityType type = target.entityType();
        if (type == null) {
            return;
        }

        switch (type) {
            case PERSON -> personExtractor.extractAttributes(target, documents, attributes, resolutions);
            case ORGANIZATION -> organizationExtractor.extractAttributes(target, documents, attributes, resolutions);
            case REPOSITORY -> {
                putIfPresent(attributes, "repository", repositoryExtractor.extractRepositoryInfo(target, documents));
                repositoryExtractor.extractAttributes(target, documents, attributes, resolutions);
            }
            case PRODUCT -> productExtractor.extractAttributes(target, documents, attributes, resolutions);
            case WEBSITE -> productExtractor.extractWebsiteAttributes(target, documents, attributes, resolutions);
            default -> {}
        }
    }

    private void enrichWithAiIfAvailable(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        aiEvidenceEnricher.enrichWithAiExtraction(target, documents, resolutions, attributes);
    }

    private void applyTargetFieldsIfRequested(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target.targetFields() != null && !target.targetFields().isEmpty()) {
            targetFieldNormalizer.applyTargetFields(target, attributes);
        }
    }

    public void applyTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        targetFieldNormalizer.applyTargetFields(target, attributes);
    }

    public boolean isFactGroundedInSource(AiExtractedFact fact, String docText) {
        return aiEvidenceEnricher.isFactGroundedInSource(fact, docText);
    }

    public void mergeAttribute(
            Map<String, EvidenceTuple> attributes,
            String key,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier
    ) {
        evidenceMerger.mergeAttribute(attributes, key, value, sourceUrl, snippet, tier);
    }

    private void putIfPresent(Map<String, EvidenceTuple> attributes, String key, EvidenceTuple evidence) {
        if (evidence != null) {
            attributes.put(key, evidence);
        }
    }
}
