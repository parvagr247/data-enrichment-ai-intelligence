package com.subdual.ai_intelligent_service.service;

import com.subdual.ai_intelligent_service.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationResponse;

public interface EnrichmentAIService {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request);
}
