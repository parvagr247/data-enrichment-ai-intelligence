package com.subdual.ai_intelligent_service.profile.service;

import com.subdual.ai_intelligent_service.profile.api.dto.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.ProfileAssessmentResponse;

public interface ProfileAssessmentEngine {
    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);
}
