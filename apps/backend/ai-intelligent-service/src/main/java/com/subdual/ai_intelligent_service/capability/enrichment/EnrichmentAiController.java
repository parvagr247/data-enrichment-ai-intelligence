package com.subdual.ai_intelligent_service.capability.enrichment;

import com.subdual.ai_intelligent_service.capability.enrichment.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing enrichment operations backed directly by EnrichmentAIService.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class EnrichmentAiController {

    private final EnrichmentAIService enrichmentAIService;

    @PostMapping(value = "/requirement", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequirementInterpretationResponse> interpretRequirement(
            @Valid @RequestBody RequirementInterpretationRequest request
    ) {
        return ResponseEntity.ok(enrichmentAIService.interpretRequirement(request));
    }

    @PostMapping(value = "/clean", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputCleansingResponse> cleanInput(
            @Valid @RequestBody InputCleansingRequest request
    ) {
        return ResponseEntity.ok(enrichmentAIService.cleanInput(request));
    }

    @PostMapping(value = "/enrich", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AIEnrichmentResult> synthesizeEnrichment(
            @Valid @RequestBody EnrichmentSynthesisRequest request
    ) {
        return ResponseEntity.ok(enrichmentAIService.synthesizeEnrichment(request));
    }
}
