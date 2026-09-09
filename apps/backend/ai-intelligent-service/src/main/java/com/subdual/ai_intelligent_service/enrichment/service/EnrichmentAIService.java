package com.subdual.ai_intelligent_service.enrichment.service;

import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;

public interface EnrichmentAIService {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request);
}
