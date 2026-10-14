package com.subdual.dataset_service.enrichment.facade;

import com.subdual.dataset_service.enrichment.api.dto.request.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.entity.api.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.entity.api.dto.response.EntityDetailResponse;
import com.subdual.dataset_service.entity.service.EntityPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Concrete implementation of EnrichmentEngineFacade delegating to downstream REST clients
 * and the JPA entity persistence service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
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
