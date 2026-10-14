package com.subdual.ai_intelligent_service.capability.extraction;

import com.subdual.ai_intelligent_service.capability.extraction.model.ExtractionRequest;
import com.subdual.ai_intelligent_service.capability.extraction.model.ExtractionResponse;

public interface ExtractionService {
    ExtractionResponse extractFacts(ExtractionRequest request);
}
