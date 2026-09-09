package com.subdual.dataset_service.enrichment.model;

import java.util.List;

public record RecommendedApproach(
        ApproachType approachType,
        String summary,
        String rationale,
        List<String> suggestedTalkingPoints
) {
    public RecommendedApproach {
        if (approachType == null) approachType = ApproachType.NETWORKING_CONVERSATION;
        if (summary == null || summary.isBlank()) summary = "General professional connection.";
        if (rationale == null || rationale.isBlank()) rationale = "Ground outreach in verified professional background.";
        if (suggestedTalkingPoints == null) suggestedTalkingPoints = List.of();
    }

    public enum ApproachType {
        RECRUITER_OUTREACH,
        TECHNICAL_GUIDANCE,
        REFERRAL_REQUEST,
        MENTORSHIP_REQUEST,
        HIRING_CONVERSATION,
        NETWORKING_CONVERSATION,
        NOT_RECOMMENDED
    }
}
