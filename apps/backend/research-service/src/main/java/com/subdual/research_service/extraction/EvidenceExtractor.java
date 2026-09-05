package com.subdual.research_service.extraction;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.ai.AiEvidenceEnricher;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.extractor.CommonEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.OrganizationEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.PersonEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.ProductEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.RepositoryEvidenceExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.extraction.support.TargetFieldNormalizer;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level coordinator that orchestrates multi-source evidence extraction across
 * document signals, entity heuristics, AI grounding, and target field normalization.
 */
@Component
public class EvidenceExtractor {

    private final EvidenceMerger evidenceMerger;
    private final CommonEvidenceExtractor commonExtractor;
    private final PersonEvidenceExtractor personExtractor;
    private final OrganizationEvidenceExtractor organizationExtractor;
    private final RepositoryEvidenceExtractor repositoryExtractor;
    private final ProductEvidenceExtractor productExtractor;
    private final AiEvidenceEnricher aiEvidenceEnricher;
    private final TargetFieldNormalizer targetFieldNormalizer;

    @Autowired
    public EvidenceExtractor(
            EvidenceMerger evidenceMerger,
            CommonEvidenceExtractor commonExtractor,
            PersonEvidenceExtractor personExtractor,
            OrganizationEvidenceExtractor organizationExtractor,
            RepositoryEvidenceExtractor repositoryExtractor,
            ProductEvidenceExtractor productExtractor,
            AiEvidenceEnricher aiEvidenceEnricher,
            TargetFieldNormalizer targetFieldNormalizer
    ) {
        this.evidenceMerger = evidenceMerger;
        this.commonExtractor = commonExtractor;
        this.personExtractor = personExtractor;
        this.organizationExtractor = organizationExtractor;
        this.repositoryExtractor = repositoryExtractor;
        this.productExtractor = productExtractor;
        this.aiEvidenceEnricher = aiEvidenceEnricher;
        this.targetFieldNormalizer = targetFieldNormalizer;
    }

    public EvidenceExtractor(AiExtractionClient aiExtractionClient) {
        this.evidenceMerger = new EvidenceMerger();
        this.commonExtractor = new CommonEvidenceExtractor();
        this.personExtractor = new PersonEvidenceExtractor(this.evidenceMerger);
        this.organizationExtractor = new OrganizationEvidenceExtractor(this.evidenceMerger);
        this.repositoryExtractor = new RepositoryEvidenceExtractor(this.evidenceMerger);
        this.productExtractor = new ProductEvidenceExtractor(this.evidenceMerger);
        this.aiEvidenceEnricher = new AiEvidenceEnricher(aiExtractionClient != null ? aiExtractionClient : new NoOpAiExtractionClient(), this.evidenceMerger);
        this.targetFieldNormalizer = new TargetFieldNormalizer();
    }

    public EvidenceExtractor() {
        this((AiExtractionClient) null);
    }

    public Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> sources,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        if (target == null || documents == null || documents.isEmpty()) {
            return attributes;
        }

        putIfPresent(attributes, "description", commonExtractor.extractDescription(target, documents, sources, resolutions));
        putIfPresent(attributes, "title", commonExtractor.extractTitle(target, documents, sources, resolutions));
        putIfPresent(attributes, "site_name", commonExtractor.extractSiteName(documents, resolutions));

        if (target.entityType() == EntityType.PERSON) {
            personExtractor.extractAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.ORGANIZATION) {
            organizationExtractor.extractAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.REPOSITORY) {
            putIfPresent(attributes, "repository", repositoryExtractor.extractRepositoryInfo(target, documents));
            repositoryExtractor.extractAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.PRODUCT) {
            productExtractor.extractAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.WEBSITE) {
            productExtractor.extractWebsiteAttributes(target, documents, attributes, resolutions);
        }

        aiEvidenceEnricher.enrichWithAiExtraction(target, documents, resolutions, attributes);

        if (target.targetFields() != null && !target.targetFields().isEmpty()) {
            targetFieldNormalizer.applyTargetFields(target, attributes);
        }

        return attributes;
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
