package com.subdual.dataset_service.enrichment;

import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.entity.dto.PersistEntityRequest;
import com.subdual.dataset_service.entity.dto.EntityDetailResponse;
import com.subdual.dataset_service.entity.EntityPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Concrete implementation of EnrichmentEngineFacade delegating to downstream REST clients
 * and the JPA entity persistence service.
 */
@Component
@RequiredArgsConstructor
public class DefaultEnrichmentEngineFacade implements EnrichmentEngineFacade {

    private final ResearchServiceClient researchServiceClient;
    private final AiServiceClient aiServiceClient;
    private final EntityPersistenceService persistenceService;

    @Override
    public ResearchServiceClient.ResearchCallResponse executeResearch(ResearchServiceClient.ResearchCallRequest request) {
        return researchServiceClient.executeResearch(request);
    }

    @Override
    public AiServiceClient.RequirementCallResponse interpretRequirement(String requirement, String entityType, Map<String, String> sampleRow) {
        return aiServiceClient.interpretRequirement(requirement, entityType, sampleRow);
    }

    @Override
    public AiServiceClient.SynthesisCallResponse synthesizeEnrichment(AiServiceClient.SynthesisCallRequest request) {
        return aiServiceClient.synthesizeEnrichment(request);
    }

    @Override
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        return aiServiceClient.assessProfile(request);
    }

    @Override
    public EntityDetailResponse persistOrUpdateEntity(PersistEntityRequest request, String userId) {
        return (userId != null && !userId.isBlank())
                ? persistenceService.persistOrUpdate(request, userId)
                : persistenceService.persistOrUpdate(request);
    }
}
