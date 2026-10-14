package com.subdual.dataset_service.enrichment.facade;

import com.subdual.dataset_service.enrichment.api.dto.request.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.entity.api.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.entity.api.dto.response.EntityDetailResponse;

import java.util.Map;

/**
 * GoF Facade Pattern: Unified facade for the downstream mesh integrations and relational persistence.
 * Shields batch row processors and job orchestrators from downstream communication complexities.
 */
public interface EnrichmentEngineFacade {

    ResearchServiceClient.ResearchCallResponse executeResearch(ResearchServiceClient.ResearchCallRequest request);

    AiServiceClient.RequirementCallResponse interpretRequirement(String requirement, String entityType, Map<String, String> sampleRow);

    AiServiceClient.SynthesisCallResponse synthesizeEnrichment(AiServiceClient.SynthesisCallRequest request);

    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);

    EntityDetailResponse persistOrUpdateEntity(PersistEntityRequest request, String userId);
}
