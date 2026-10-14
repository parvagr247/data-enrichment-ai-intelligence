package com.subdual.ai_intelligent_service.facade;

import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.enrichment.service.EnrichmentAIService;
import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.service.ExtractionService;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.planning.service.EnrichmentPlanningService;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;
import com.subdual.ai_intelligent_service.profile.service.ProfileAssessmentEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Concrete implementation of AiServiceFacade coordinating subsystem components.
 */
@Component
@RequiredArgsConstructor
public class DefaultAiServiceFacade implements AiServiceFacade {

    private final EnrichmentAIService enrichmentAIService;
    private final ExtractionService extractionService;
    private final EnrichmentPlanningService planningService;
    private final ProfileAssessmentEngine profileAssessmentEngine;

    @Override
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        return enrichmentAIService.interpretRequirement(request);
    }

    @Override
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        return enrichmentAIService.cleanInput(request);
    }

    @Override
    public AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request) {
        return enrichmentAIService.synthesizeEnrichment(request);
    }

    @Override
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        return extractionService.extractFacts(request);
    }

    @Override
    public EnrichmentPlan planEnrichment(EnrichmentRequirement requirement) {
        return planningService.planEnrichment(requirement);
    }

    @Override
    public ResearchObjective parseObjective(String rawObjective) {
        return ResearchObjective.from(rawObjective);
    }

    @Override
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        return profileAssessmentEngine.assessProfile(request);
    }
}
