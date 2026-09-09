package com.subdual.ai_intelligent_service.service.app;

import com.subdual.ai_intelligent_service.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.service.ai.AiIntelligence;
import org.springframework.stereotype.Service;

/**
 * Application service for requirement interpretation.
 */
@Service
public class RequirementUnderstandingService {

    private final AiIntelligence aiIntelligence;

    public RequirementUnderstandingService(AiIntelligence aiIntelligence) {
        this.aiIntelligence = aiIntelligence;
    }

    public RequirementInterpretationResponse interpret(RequirementInterpretationRequest request) {
        return aiIntelligence.interpretRequirement(request);
    }
}
