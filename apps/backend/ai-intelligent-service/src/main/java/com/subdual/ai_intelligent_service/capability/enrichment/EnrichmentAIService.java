package com.subdual.ai_intelligent_service.capability.enrichment;

import com.subdual.ai_intelligent_service.capability.enrichment.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationResponse;

public interface EnrichmentAIService {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request);
}
