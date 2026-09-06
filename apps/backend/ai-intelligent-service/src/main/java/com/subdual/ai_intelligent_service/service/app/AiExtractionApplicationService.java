package com.subdual.ai_intelligent_service.service.app;

import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.service.ai.AiIntelligence;
import org.springframework.stereotype.Service;

/**
 * Application service for grounded structured extraction (Task 73).
 */
@Service
public class AiExtractionApplicationService {

    private final AiIntelligence aiIntelligence;

    public AiExtractionApplicationService(AiIntelligence aiIntelligence) {
        this.aiIntelligence = aiIntelligence;
    }

    public ExtractionResponse extract(ExtractionRequest request) {
        return aiIntelligence.extractFacts(request);
    }
}
