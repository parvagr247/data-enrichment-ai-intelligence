package com.subdual.dataset_service.enrichment.model;

import java.util.List;

public record ResearchProfile(
        String currentRole,
        String currentOrganization,
        String location,
        String professionalSummary,
        String careerBackground,
        List<String> technicalExpertise,
        List<ExperienceItem> relevantExperience,
        List<ProjectItem> relevantProjects,
        List<ActivityItem> publicActivity
) {
    public ResearchProfile {
        if (currentRole == null || currentRole.isBlank()) currentRole = "UNKNOWN";
        if (currentOrganization == null || currentOrganization.isBlank()) currentOrganization = "UNKNOWN";
        if (location == null || location.isBlank()) location = "UNKNOWN";
        if (professionalSummary == null || professionalSummary.isBlank()) professionalSummary = "No detailed profile summary available.";
        if (careerBackground == null || careerBackground.isBlank()) careerBackground = "No career background records available.";
        if (technicalExpertise == null) technicalExpertise = List.of();
        if (relevantExperience == null) relevantExperience = List.of();
        if (relevantProjects == null) relevantProjects = List.of();
        if (publicActivity == null) publicActivity = List.of();
    }

    public record ExperienceItem(
            String role,
            String organization,
            String duration,
            String summary,
            String evidenceQuote
    ) {}

    public record ProjectItem(
            String title,
            String description,
            List<String> technologies,
            String sourceUrl
    ) {}

    public record ActivityItem(
            String title,
            String activityType,
            String summary,
            String classification,
            String sourceUrl
    ) {}
}
