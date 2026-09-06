package com.subdual.ai_intelligent_service.service.ai;

import com.subdual.ai_intelligent_service.dto.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.dto.ProfileAssessmentResponse;

public interface ProfileAssessmentEngine {
    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);
}
