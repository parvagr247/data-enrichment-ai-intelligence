package com.subdual.ai_intelligent_service.profile.api.controller;

import com.subdual.ai_intelligent_service.facade.AiServiceFacade;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ObjectiveParseRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;
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
 * Controller delegating profile assessment and objective parsing to the unified AiServiceFacade.
 */
@RestController
@RequestMapping("/api/v2/ai")
@RequiredArgsConstructor
@Slf4j
public class ProfileAssessmentController {

    private final AiServiceFacade aiFacade;

    @PostMapping(value = "/objective/parse", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchObjective> parseObjective(@RequestBody ObjectiveParseRequest request) {
        String raw = request != null ? request.objective() : null;
        log.info("Parsing research objective: '{}'", raw != null && raw.length() > 50 ? raw.substring(0, 50) + "..." : raw);
        return ResponseEntity.ok(aiFacade.parseObjective(raw));
    }

    @PostMapping(value = "/profile/assess", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileAssessmentResponse> assessProfile(@Valid @RequestBody ProfileAssessmentRequest request) {
        log.info("Executing objective-aware profile assessment for entity: '{}'", request != null ? request.displayName() : "null");
        return ResponseEntity.ok(aiFacade.assessProfile(request));
    }
}
