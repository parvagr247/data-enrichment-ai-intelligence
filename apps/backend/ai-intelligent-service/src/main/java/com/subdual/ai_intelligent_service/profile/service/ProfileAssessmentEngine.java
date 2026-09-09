package com.subdual.ai_intelligent_service.profile.service;

import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;

public interface ProfileAssessmentEngine {
    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);
}
