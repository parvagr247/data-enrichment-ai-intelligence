package com.subdual.dataset_service.enrichment;

import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.entity.dto.PersistEntityRequest;
import com.subdual.dataset_service.entity.dto.EntityDetailResponse;

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
