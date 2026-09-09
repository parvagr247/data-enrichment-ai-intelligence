package com.subdual.ai_intelligent_service.enrichment.api.controller;

import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.enrichment.service.EnrichmentAIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentAiController {

    private final EnrichmentAIService enrichmentAIService;

    @PostMapping(value = "/requirement", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequirementInterpretationResponse> interpretRequirement(
            @Valid @RequestBody RequirementInterpretationRequest request
    ) {
        log.info("Interpreting user requirement for entityType: '{}'", request != null ? request.entityType() : "null");
        RequirementInterpretationResponse response = enrichmentAIService.interpretRequirement(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/clean", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputCleansingResponse> cleanInput(
            @Valid @RequestBody InputCleansingRequest request
    ) {
        log.info("Cleaning raw entity input");
        InputCleansingResponse response = enrichmentAIService.cleanInput(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/enrich", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AIEnrichmentResult> synthesizeEnrichment(
            @Valid @RequestBody EnrichmentSynthesisRequest request
    ) {
        log.info("Synthesizing structured enrichment for entity: '{}'", request != null ? request.displayName() : "null");
        AIEnrichmentResult response = enrichmentAIService.synthesizeEnrichment(request);
        return ResponseEntity.ok(response);
    }
}
