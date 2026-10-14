package com.subdual.ai_intelligent_service.enrichment.api.controller;

import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.facade.AiServiceFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller delegating enrichment operations to the unified AiServiceFacade.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentAiController {

    private final AiServiceFacade aiFacade;

    @PostMapping(value = "/requirement", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequirementInterpretationResponse> interpretRequirement(
            @Valid @RequestBody RequirementInterpretationRequest request
    ) {
        log.info("Interpreting user requirement for entityType: '{}'", request != null ? request.entityType() : "null");
        return ResponseEntity.ok(aiFacade.interpretRequirement(request));
    }

    @PostMapping(value = "/clean", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputCleansingResponse> cleanInput(
            @Valid @RequestBody InputCleansingRequest request
    ) {
        log.info("Cleaning raw entity input");
        return ResponseEntity.ok(aiFacade.cleanInput(request));
    }

    @PostMapping(value = "/enrich", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AIEnrichmentResult> synthesizeEnrichment(
            @Valid @RequestBody EnrichmentSynthesisRequest request
    ) {
        log.info("Synthesizing structured enrichment for entity: '{}'", request != null ? request.displayName() : "null");
        return ResponseEntity.ok(aiFacade.synthesizeEnrichment(request));
    }
}
