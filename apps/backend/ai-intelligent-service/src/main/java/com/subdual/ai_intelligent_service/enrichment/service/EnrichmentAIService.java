package com.subdual.ai_intelligent_service.enrichment.service;

import com.subdual.ai_intelligent_service.enrichment.api.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationResponse;

public interface EnrichmentAIService {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request);
}
