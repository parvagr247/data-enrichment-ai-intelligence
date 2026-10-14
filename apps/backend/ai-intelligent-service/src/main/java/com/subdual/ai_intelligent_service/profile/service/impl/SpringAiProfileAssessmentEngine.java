package com.subdual.ai_intelligent_service.profile.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.ai.helper.AiPromptExecutor;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.DimensionalScore;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.PriorityTier;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach.ApproachType;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding.FindingType;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ActivityItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ExperienceItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ProjectItem;
import com.subdual.ai_intelligent_service.profile.service.ProfileAssessmentEngine;
import com.subdual.ai_intelligent_service.profile.service.helper.ProfileExtractionHelper;
import com.subdual.ai_intelligent_service.prompt.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Clean, minimalistic implementation of ProfileAssessmentEngine delegating prompt execution
 * to AiPromptExecutor and fallback to DeterministicProfileAssessmentEngine.
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class SpringAiProfileAssessmentEngine implements ProfileAssessmentEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiPromptExecutor promptExecutor;
    private final PromptTemplateService promptTemplateService;
    private final DeterministicProfileAssessmentEngine deterministicFallback;
    private final ProfileExtractionHelper extractionHelper;

    @Override
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        if (request == null) {
            return deterministicFallback.assessProfile(request);
        }

        long startMs = System.currentTimeMillis();
        String promptText = buildPromptText(request);
        Optional<String> aiResponse = promptExecutor.executePrompt("PROFILE_ASSESSMENT", promptText, request.displayName());

        if (aiResponse.isPresent()) {
            ProfileAssessmentResponse response = parseProfileResponse(aiResponse.get(), request, startMs);
            if (response != null) {
                return response;
            }
        }

        return deterministicFallback.assessProfile(request);
    }

    private String buildPromptText(ProfileAssessmentRequest request) {
        ResearchObjective obj = request.objective() != null ? request.objective() : ResearchObjective.blank();
        Map<String, Object> vars = new HashMap<>();
        vars.put("displayName", request.displayName() != null ? request.displayName() : "Unknown");
        vars.put("entityType", request.entityType() != null ? request.entityType() : "PERSON");
        vars.put("canonicalUrl", request.canonicalUrl() != null ? request.canonicalUrl() : "");
        vars.put("objective", obj.rawObjective() != null ? obj.rawObjective() : "");
        vars.put("rawInputJson", toJsonSafe(request.rawInput()));
        vars.put("evidenceJson", toJsonSafe(request.researchEvidence()));
        vars.put("sourceSnippetsJson", toJsonSafe(request.sourceSnippets()));

        return promptTemplateService.render("research-profile-assessment", vars);
    }

    private String toJsonSafe(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private ProfileAssessmentResponse parseProfileResponse(String responseText, ProfileAssessmentRequest request, long startMs) {
        try {
            JsonNode root = MAPPER.readTree(responseText);

            ResearchProfile profile = parseProfile(root.get("profile"));
            ObjectiveAssessment assessment = parseAssessment(root.get("assessment"));
            RecommendedApproach recommendation = parseRecommendation(root.get("recommendation"));
            List<ResearchFinding> findings = parseFindings(root);

            long elapsed = System.currentTimeMillis() - startMs;
            AiExecutionMetrics metrics = new AiExecutionMetrics(promptExecutor.getModelName(), 0, 0, 0, elapsed, 1, 0, "SUCCESS");

            return new ProfileAssessmentResponse(
                    request.displayName(), request.canonicalUrl(), request.entityType(),
                    profile, assessment, recommendation, findings, metrics
            );
        } catch (Exception ex) {
            log.warn("Failed to parse Spring AI profile assessment JSON response: {}", ex.getMessage());
            return null;
        }
    }

    private ResearchProfile parseProfile(JsonNode profileNode) {
        String currentRole = profileNode != null && profileNode.has("currentRole") ? profileNode.get("currentRole").asText() : "UNKNOWN";
        String currentOrg = profileNode != null && profileNode.has("currentOrganization") ? profileNode.get("currentOrganization").asText() : "UNKNOWN";
        String location = profileNode != null && profileNode.has("location") ? profileNode.get("location").asText() : "UNKNOWN";
        String summary = profileNode != null && profileNode.has("professionalSummary") ? profileNode.get("professionalSummary").asText() : "";
        String career = profileNode != null && profileNode.has("careerBackground") ? profileNode.get("careerBackground").asText() : "";

        List<String> expertise = new ArrayList<>();
        if (profileNode != null && profileNode.has("technicalExpertise") && profileNode.get("technicalExpertise").isArray()) {
            profileNode.get("technicalExpertise").forEach(n -> expertise.add(n.asText()));
        }

        List<ExperienceItem> experience = new ArrayList<>();
        if (profileNode != null && profileNode.has("relevantExperience") && profileNode.get("relevantExperience").isArray()) {
            profileNode.get("relevantExperience").forEach(n -> experience.add(new ExperienceItem(
                    n.has("role") ? n.get("role").asText() : "Engineer",
                    n.has("organization") ? n.get("organization").asText() : "Company",
                    n.has("duration") ? n.get("duration").asText() : "",
                    n.has("summary") ? n.get("summary").asText() : "",
                    n.has("evidenceQuote") ? n.get("evidenceQuote").asText() : ""
            )));
        }

        List<ProjectItem> projects = new ArrayList<>();
        if (profileNode != null && profileNode.has("relevantProjects") && profileNode.get("relevantProjects").isArray()) {
            profileNode.get("relevantProjects").forEach(n -> {
                List<String> tech = new ArrayList<>();
                if (n.has("technologies") && n.get("technologies").isArray()) {
                    n.get("technologies").forEach(t -> tech.add(t.asText()));
                }
                projects.add(new ProjectItem(
                        n.has("title") ? n.get("title").asText() : "Project",
                        n.has("description") ? n.get("description").asText() : "",
                        tech,
                        n.has("sourceUrl") ? n.get("sourceUrl").asText() : ""
                ));
            });
        }

        List<ActivityItem> activity = new ArrayList<>();
        if (profileNode != null && profileNode.has("publicActivity") && profileNode.get("publicActivity").isArray()) {
            profileNode.get("publicActivity").forEach(n -> activity.add(new ActivityItem(
                    n.has("title") ? n.get("title").asText() : "Activity",
                    n.has("activityType") ? n.get("activityType").asText() : "AUTHORED",
                    n.has("summary") ? n.get("summary").asText() : "",
                    n.has("classification") ? n.get("classification").asText() : "GENERAL_UPDATE",
                    n.has("sourceUrl") ? n.get("sourceUrl").asText() : ""
            )));
        }

        return new ResearchProfile(currentRole, currentOrg, location, summary, career, expertise, experience, projects, activity);
    }

    private ObjectiveAssessment parseAssessment(JsonNode assessNode) {
        int score = assessNode != null && assessNode.has("overallScore") ? assessNode.get("overallScore").asInt() : 0;
        String priorityStr = assessNode != null && assessNode.has("priorityTier") ? assessNode.get("priorityTier").asText() : "NONE";
        PriorityTier tier;
        try {
            tier = PriorityTier.valueOf(priorityStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            tier = PriorityTier.NONE;
        }
        String whyRelevant = assessNode != null && assessNode.has("whyRelevant") ? assessNode.get("whyRelevant").asText() : "";

        Map<String, DimensionalScore> dimensions = new LinkedHashMap<>();
        if (assessNode != null && assessNode.has("dimensions") && assessNode.get("dimensions").isObject()) {
            assessNode.get("dimensions").fields().forEachRemaining(entry -> {
                JsonNode dNode = entry.getValue();
                int dScore = dNode.has("score") ? dNode.get("score").asInt() : 50;
                String dRat = dNode.has("rationale") ? dNode.get("rationale").asText() : "";
                dimensions.put(entry.getKey(), new DimensionalScore(dScore, dRat));
            });
        }

        List<String> strengths = new ArrayList<>();
        if (assessNode != null && assessNode.has("keyStrengths") && assessNode.get("keyStrengths").isArray()) {
            assessNode.get("keyStrengths").forEach(n -> strengths.add(n.asText()));
        }

        List<String> gaps = new ArrayList<>();
        if (assessNode != null && assessNode.has("limitationsOrGaps") && assessNode.get("limitationsOrGaps").isArray()) {
            assessNode.get("limitationsOrGaps").forEach(n -> gaps.add(n.asText()));
        }

        return new ObjectiveAssessment(score, tier, whyRelevant, dimensions, strengths, gaps);
    }

    private RecommendedApproach parseRecommendation(JsonNode recNode) {
        String approachStr = recNode != null && recNode.has("approachType") ? recNode.get("approachType").asText() : "NETWORKING_CONVERSATION";
        ApproachType appType;
        try {
            appType = ApproachType.valueOf(approachStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            appType = ApproachType.NETWORKING_CONVERSATION;
        }
        String recSummary = recNode != null && recNode.has("summary") ? recNode.get("summary").asText() : "General professional outreach.";
        String recRat = recNode != null && recNode.has("rationale") ? recNode.get("rationale").asText() : "Grounded in profile analysis.";

        List<String> talkingPoints = new ArrayList<>();
        if (recNode != null && recNode.has("suggestedTalkingPoints") && recNode.get("suggestedTalkingPoints").isArray()) {
            recNode.get("suggestedTalkingPoints").forEach(n -> talkingPoints.add(n.asText()));
        }

        return new RecommendedApproach(appType, recSummary, recRat, talkingPoints);
    }

    private List<ResearchFinding> parseFindings(JsonNode root) {
        List<ResearchFinding> findings = new ArrayList<>();
        if (root.has("findings") && root.get("findings").isArray()) {
            root.get("findings").forEach(n -> {
                String claim = n.has("claim") ? n.get("claim").asText() : "";
                String fTypeStr = n.has("findingType") ? n.get("findingType").asText() : "FACT_SOURCE_DERIVED";
                FindingType fType = "INFERRED_ASSESSMENT".equalsIgnoreCase(fTypeStr) ? FindingType.INFERRED_ASSESSMENT : FindingType.FACT_SOURCE_DERIVED;
                String conf = n.has("confidence") ? n.get("confidence").asText() : "HIGH";
                String sUrl = n.has("sourceUrl") ? n.get("sourceUrl").asText() : "";
                String snippet = n.has("evidenceSnippet") ? n.get("evidenceSnippet").asText() : "";
                String title = n.has("sourceTitle") ? n.get("sourceTitle").asText() : "";
                findings.add(new ResearchFinding(claim, fType, conf, sUrl, snippet, title));
            });
        }
        return findings;
    }
}
