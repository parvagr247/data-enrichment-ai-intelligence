package com.subdual.research_service.extraction.factory;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.extractor.OrganizationEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.PersonEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.ProductEvidenceExtractor;
import com.subdual.research_service.extraction.extractor.RepositoryEvidenceExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory that registers and provides entity-type specific evidence extractors.
 */
@Component
public class DefaultEntityEvidenceExtractorFactory implements EntityEvidenceExtractorFactory {

    private final PersonEvidenceExtractor personExtractor;
    private final OrganizationEvidenceExtractor organizationExtractor;
    private final RepositoryEvidenceExtractor repositoryExtractor;
    private final ProductEvidenceExtractor productExtractor;

    public DefaultEntityEvidenceExtractorFactory(
            PersonEvidenceExtractor personExtractor,
            OrganizationEvidenceExtractor organizationExtractor,
            RepositoryEvidenceExtractor repositoryExtractor,
            ProductEvidenceExtractor productExtractor
    ) {
        this.personExtractor = personExtractor;
        this.organizationExtractor = organizationExtractor;
        this.repositoryExtractor = repositoryExtractor;
        this.productExtractor = productExtractor;
    }

    @Override
    public Optional<EntityEvidenceExtractor> getExtractor(EntityType entityType) {
        if (entityType == null) {
            return Optional.empty();
        }
        return switch (entityType) {
            case PERSON -> Optional.of(new EntityEvidenceExtractor() {
                @Override
                public boolean supports(EntityType type) {
                    return type == EntityType.PERSON;
                }
                @Override
                public void extract(ResearchTarget target, List<ExtractedDocument> docs, Map<String, EvidenceTuple> attrs, Map<String, EntityResolver.ResolutionResult> res) {
                    personExtractor.extractAttributes(target, docs, attrs, res);
                }
            });
            case ORGANIZATION -> Optional.of(new EntityEvidenceExtractor() {
                @Override
                public boolean supports(EntityType type) {
                    return type == EntityType.ORGANIZATION;
                }
                @Override
                public void extract(ResearchTarget target, List<ExtractedDocument> docs, Map<String, EvidenceTuple> attrs, Map<String, EntityResolver.ResolutionResult> res) {
                    organizationExtractor.extractAttributes(target, docs, attrs, res);
                }
            });
            case REPOSITORY -> Optional.of(new EntityEvidenceExtractor() {
                @Override
                public boolean supports(EntityType type) {
                    return type == EntityType.REPOSITORY;
                }
                @Override
                public void extract(ResearchTarget target, List<ExtractedDocument> docs, Map<String, EvidenceTuple> attrs, Map<String, EntityResolver.ResolutionResult> res) {
                    EvidenceTuple repoInfo = repositoryExtractor.extractRepositoryInfo(target, docs);
                    if (repoInfo != null) {
                        attrs.put("repository", repoInfo);
                    }
                    repositoryExtractor.extractAttributes(target, docs, attrs, res);
                }
            });
            case PRODUCT -> Optional.of(new EntityEvidenceExtractor() {
                @Override
                public boolean supports(EntityType type) {
                    return type == EntityType.PRODUCT;
                }
                @Override
                public void extract(ResearchTarget target, List<ExtractedDocument> docs, Map<String, EvidenceTuple> attrs, Map<String, EntityResolver.ResolutionResult> res) {
                    productExtractor.extractAttributes(target, docs, attrs, res);
                }
            });
            case WEBSITE -> Optional.of(new EntityEvidenceExtractor() {
                @Override
                public boolean supports(EntityType type) {
                    return type == EntityType.WEBSITE;
                }
                @Override
                public void extract(ResearchTarget target, List<ExtractedDocument> docs, Map<String, EvidenceTuple> attrs, Map<String, EntityResolver.ResolutionResult> res) {
                    productExtractor.extractWebsiteAttributes(target, docs, attrs, res);
                }
            });
            default -> Optional.empty();
        };
    }
}
