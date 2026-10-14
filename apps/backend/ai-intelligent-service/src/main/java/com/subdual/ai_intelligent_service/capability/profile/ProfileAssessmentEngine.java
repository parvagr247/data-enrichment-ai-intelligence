package com.subdual.ai_intelligent_service.capability.profile;

import com.subdual.ai_intelligent_service.capability.profile.dto.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.capability.profile.dto.ProfileAssessmentResponse;

public interface ProfileAssessmentEngine {
    ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request);
}
