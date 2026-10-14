package com.subdual.ai_intelligent_service.capability.profile;

import com.subdual.ai_intelligent_service.capability.profile.dto.ObjectiveParseRequest;
import com.subdual.ai_intelligent_service.capability.profile.dto.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.capability.profile.dto.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.capability.profile.model.ResearchObjective;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing profile assessment and objective parsing operations backed directly by ProfileAssessmentEngine.
 */
@RestController
@RequestMapping("/api/v2/ai")
@RequiredArgsConstructor
public class ProfileAssessmentController {

    private final ProfileAssessmentEngine profileAssessmentEngine;

    @PostMapping(value = "/objective/parse", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchObjective> parseObjective(@RequestBody ObjectiveParseRequest request) {
        String raw = request != null ? request.objective() : null;
        return ResponseEntity.ok(ResearchObjective.from(raw));
    }

    @PostMapping(value = "/profile/assess", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileAssessmentResponse> assessProfile(@Valid @RequestBody ProfileAssessmentRequest request) {
        return ResponseEntity.ok(profileAssessmentEngine.assessProfile(request));
    }
}
