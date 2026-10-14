package com.subdual.ai_intelligent_service.facade;

import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;

/**
 * GoF Facade Pattern: Unified, higher-level interface to the AI Intelligent Service subsystems
 * (cleansing, requirement interpretation, grounded extraction, requirement planning, and profile assessment).
 */
public interface AiServiceFacade {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request);

    ExtractionResponse extractFacts(ExtractionRequest request);

    EnrichmentPlan planEnrichment(EnrichmentRequirement requirement);

    ResearchObjective parseObjective(String rawObjective);

    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);
}
