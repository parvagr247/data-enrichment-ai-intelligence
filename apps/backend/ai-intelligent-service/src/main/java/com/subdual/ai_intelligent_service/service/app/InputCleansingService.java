package com.subdual.ai_intelligent_service.service.app;

import com.subdual.ai_intelligent_service.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.service.ai.AiIntelligence;
import org.springframework.stereotype.Service;

/**
 * Application service for input cleansing.
 */
@Service
public class InputCleansingService {

    private final AiIntelligence aiIntelligence;

    public InputCleansingService(AiIntelligence aiIntelligence) {
        this.aiIntelligence = aiIntelligence;
    }

    public InputCleansingResponse clean(InputCleansingRequest request) {
        return aiIntelligence.cleanInput(request);
    }
}
