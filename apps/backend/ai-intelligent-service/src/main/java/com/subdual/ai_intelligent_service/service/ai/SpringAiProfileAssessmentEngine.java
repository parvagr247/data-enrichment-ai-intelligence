package com.subdual.ai_intelligent_service.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.dto.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.dto.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.objective.model.ResearchObjective;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.DimensionalScore;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.PriorityTier;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach.ApproachType;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding.FindingType;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ActivityItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ExperienceItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ProjectItem;
import com.subdual.ai_intelligent_service.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Primary
@Slf4j
public class SpringAiProfileAssessmentEngine implements ProfileAssessmentEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 2;

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final PromptTemplateService promptTemplateService;
    private final DeterministicProfileAssessmentEngine deterministicFallback;
    private final String geminiApiKey;

    public SpringAiProfileAssessmentEngine(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            PromptTemplateService promptTemplateService,
            DeterministicProfileAssessmentEngine deterministicFallback,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.promptTemplateService = promptTemplateService;
        this.deterministicFallback = deterministicFallback;
        this.geminiApiKey = geminiApiKey;
    }

    @Override
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        if (isMockMode() || chatModel == null) {
            return deterministicFallback.assessProfile(request);
        }

        long startMs = System.currentTimeMillis();
        int retries = 0;

        while (retries <= MAX_RETRIES) {
            try {
                ResearchObjective obj = request.objective() != null ? request.objective() : ResearchObjective.blank();
                Map<String, Object> vars = new HashMap<>();
                vars.put("displayName", request.displayName() != null ? request.displayName() : "Unknown");
                vars.put("entityType", request.entityType() != null ? request.entityType() : "PERSON");
                vars.put("canonicalUrl", request.canonicalUrl() != null ? request.canonicalUrl() : "");
                vars.put("objective", obj.rawObjective() != null ? obj.rawObjective() : "");
                vars.put("rawInputJson", toJsonSafe(request.rawInput()));
                vars.put("evidenceJson", toJsonSafe(request.researchEvidence()));
                vars.put("sourceSnippetsJson", toJsonSafe(request.sourceSnippets()));

                String promptText = promptTemplateService.render("research-profile-assessment", vars);
                log.info("[AI_MODEL_CALL] model='{}' provider='google-genai' stage='PROFILE_ASSESSMENT' entity='{}' attempt={}/{}",
                        properties.model(), request.displayName(), retries + 1, MAX_RETRIES + 1);
                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

                ProfileAssessmentResponse parsed = parseProfileResponse(responseText, request, startMs);
                if (parsed != null) {
                    log.info("[AI_MODEL_SUCCESS] model='{}' provider='google-genai' stage='PROFILE_ASSESSMENT' entity='{}' durationMs={}",
                            properties.model(), request.displayName(), System.currentTimeMillis() - startMs);
                    return parsed;
                }
            } catch (Exception ex) {
                log.warn("[AI_MODEL_RETRY] model='{}' provider='google-genai' stage='PROFILE_ASSESSMENT' entity='{}' attempt={}/{} error='{}'",
                        properties.model(), request.displayName(), retries + 1, MAX_RETRIES + 1, ex.getMessage());
            }
            retries++;
        }

        log.warn("[AI_MODEL_DEGRADED] model='{}' provider='google-genai' stage='PROFILE_ASSESSMENT' entity='{}' falling back to deterministic assessment",
                properties.model(), request.displayName());
        return deterministicFallback.assessProfile(request);
    }

    private boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");
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
            String cleaned = cleanJsonBlocks(responseText);
            JsonNode root = MAPPER.readTree(cleaned);

            // Parse Profile
            JsonNode profileNode = root.get("profile");
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

            ResearchProfile profile = new ResearchProfile(
                    currentRole, currentOrg, location, summary, career, expertise, experience, projects, activity
            );

            // Parse Assessment
            JsonNode assessNode = root.get("assessment");
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

            ObjectiveAssessment assessment = new ObjectiveAssessment(
                    score, tier, whyRelevant, dimensions, strengths, gaps
            );

            // Parse Recommendation
            JsonNode recNode = root.get("recommendation");
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

            RecommendedApproach recommendation = new RecommendedApproach(
                    appType, recSummary, recRat, talkingPoints
            );

            // Parse Findings
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

            long elapsed = System.currentTimeMillis() - startMs;
            AiExecutionMetrics metrics = new AiExecutionMetrics(properties.model(), 0, 0, 0, elapsed, 1, 0, "SUCCESS");

            return new ProfileAssessmentResponse(
                    request.displayName(),
                    request.canonicalUrl(),
                    request.entityType(),
                    profile,
                    assessment,
                    recommendation,
                    findings,
                    metrics
            );
        } catch (Exception ex) {
            log.warn("Failed to parse Spring AI profile assessment JSON response: {}", ex.getMessage());
            return null;
        }
    }

    private String cleanJsonBlocks(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
