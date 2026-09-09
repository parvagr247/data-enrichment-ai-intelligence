package com.subdual.ai_intelligent_service.profile.service.impl;

import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.enrichment.api.dto.common.FactEvidenceDto;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.DimensionalScore;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.PriorityTier;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach.ApproachType;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ActivityItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ExperienceItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ProjectItem;
import com.subdual.ai_intelligent_service.profile.service.ProfileAssessmentEngine;
import com.subdual.ai_intelligent_service.profile.service.helper.ProfileExtractionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeterministicProfileAssessmentEngine implements ProfileAssessmentEngine {

    private final ProfileExtractionHelper extractionHelper;

    @Override // Evaluates candidate profile alignment against research objectives deterministically.
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        long startMs = System.currentTimeMillis();
        String name = extractionHelper.resolveString(request.displayName(), "Unknown");
        String entityType = extractionHelper.resolveString(request.entityType(), "PERSON");
        String canonicalUrl = extractionHelper.resolveString(request.canonicalUrl(), "");

        ResearchProfile profile = buildProfile(request, name);
        ResearchObjective objective = request.objective() != null ? request.objective() : ResearchObjective.blank();

        ObjectiveAssessment assessment = evaluateAssessment(profile, objective, request.researchEvidence(), profile.publicActivity());
        RecommendedApproach recommendation = evaluateRecommendation(profile, assessment, objective);
        List<ResearchFinding> findings = extractionHelper.compileGroundedFindings(request.researchEvidence(), profile, assessment);

        AiExecutionMetrics metrics = AiExecutionMetrics.deterministic(System.currentTimeMillis() - startMs);
        return new ProfileAssessmentResponse(name, canonicalUrl, entityType, profile, assessment, recommendation, findings, metrics);
    }

    private ResearchProfile buildProfile(ProfileAssessmentRequest request, String name) {
        Map<String, String> rawInput = request.rawInput();
        Map<String, FactEvidenceDto> evidence = request.researchEvidence();

        String role = extractionHelper.extractField(evidence, rawInput, "currentRole", "role", "position", "title");
        String organization = extractionHelper.extractField(evidence, rawInput, "currentOrganization", "organization", "company", "employer");
        String location = extractionHelper.extractField(evidence, rawInput, "location", "city", "country", "headquarters");
        String education = extractionHelper.extractField(evidence, rawInput, "education", "degree", "university", "college");

        List<String> technicalExpertise = extractionHelper.extractSkillsList(evidence);
        List<ExperienceItem> experienceItems = extractionHelper.extractExperienceItems(evidence, role, organization);
        List<ProjectItem> projectItems = extractionHelper.extractProjectItems(evidence);
        List<ActivityItem> activityItems = extractionHelper.extractActivityItems(evidence, request.sourceSnippets());

        String professionalSummary = extractionHelper.synthesizeProfessionalSummary(name, role, organization, location, education, technicalExpertise, activityItems);
        String careerBackground = extractionHelper.synthesizeCareerBackground(name, role, organization, education, experienceItems);

        return new ResearchProfile(
                role, organization, location, professionalSummary, careerBackground,
                technicalExpertise, experienceItems, projectItems, activityItems
        );
    }

    private ObjectiveAssessment evaluateAssessment(
            ResearchProfile profile, ResearchObjective objective,
            Map<String, FactEvidenceDto> evidence, List<ActivityItem> activity
    ) {
        if (objective.isBlank()) {
            return ObjectiveAssessment.neutral("General profile research completed. No specific research objective was provided.");
        }
        return evaluateObjectiveAlignment(profile, objective, evidence, activity);
    }

    private RecommendedApproach evaluateRecommendation(
            ResearchProfile profile, ObjectiveAssessment assessment, ResearchObjective objective
    ) {
        if (objective.isBlank()) {
            return new RecommendedApproach(
                    ApproachType.NETWORKING_CONVERSATION,
                    "Connect for general professional networking.",
                    "No objective filter was specified; connect based on shared professional domain.",
                    List.of(
                            "Discuss background at " + ("UNKNOWN".equals(profile.currentOrganization()) ? "their current company" : profile.currentOrganization()),
                            "Explore common interests in " + (profile.technicalExpertise().isEmpty() ? "technology" : profile.technicalExpertise().get(0))
                    )
            );
        }
        return deriveRecommendedApproach(profile, assessment, objective);
    }

    private ObjectiveAssessment evaluateObjectiveAlignment(
            ResearchProfile profile, ResearchObjective objective,
            Map<String, FactEvidenceDto> evidence, List<ActivityItem> activity
    ) {
        String roleLower = profile.currentRole().toLowerCase(Locale.ROOT);
        String summaryLower = profile.professionalSummary().toLowerCase(Locale.ROOT);
        String rawObjLower = objective.rawObjective().toLowerCase(Locale.ROOT);

        // 1. Technical Relevance (0 - 100)
        int techScore = 20;
        List<String> matchedTopics = new ArrayList<>();
        for (String topic : objective.relevantTopics()) {
            String topicLower = topic.toLowerCase(Locale.ROOT);
            boolean matched = profile.technicalExpertise().stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(topicLower))
                    || summaryLower.contains(topicLower);
            if (matched) {
                matchedTopics.add(topic);
                techScore += 30;
            }
        }
        techScore = Math.min(100, Math.max(10, techScore));
        String techRationale = matchedTopics.isEmpty()
                ? "Limited direct evidence of requested technical topics (" + String.join(", ", objective.relevantTopics()) + ")."
                : "Strong verified alignment with requested technologies: " + String.join(", ", matchedTopics) + ".";

        // 2. Role / Hiring Relevance (0 - 100)
        int roleScore = 20;
        String roleRationale = "Standard industry professional role.";
        boolean isRecruiter = roleLower.contains("recruiter") || roleLower.contains("talent acquisition") || roleLower.contains("sourcer");
        boolean isHiringManager = roleLower.contains("manager") || roleLower.contains("director") || roleLower.contains("lead") || roleLower.contains("head");
        boolean isFounder = roleLower.contains("founder") || roleLower.contains("ceo") || roleLower.contains("cto");
        boolean isEngineer = roleLower.contains("engineer") || roleLower.contains("developer") || roleLower.contains("architect");

        if (rawObjLower.contains("intern") || rawObjLower.contains("hire") || rawObjLower.contains("job")) {
            if (isRecruiter) {
                roleScore = 95;
                roleRationale = "Direct hiring gateway: Senior recruiter / talent acquisition professional responsible for candidate sourcing.";
            } else if (isHiringManager || isFounder) {
                roleScore = 90;
                roleRationale = "Decision maker: Engineering leadership role with direct hiring and team-building authority.";
            } else if (isEngineer) {
                roleScore = 75;
                roleRationale = "Practitioner peer: Can evaluate candidate technical caliber and submit internal referrals.";
            }
        } else if (rawObjLower.contains("mentor") || rawObjLower.contains("guidance")) {
            if (isHiringManager || isFounder || roleLower.contains("staff") || roleLower.contains("principal")) {
                roleScore = 95;
                roleRationale = "High-leverage mentor: Senior technical/executive leader with extensive career and architectural insight.";
            } else if (isEngineer) {
                roleScore = 80;
                roleRationale = "Technical mentor: Practical engineering practitioner with domain expertise.";
            }
        } else {
            if (isFounder || isHiringManager || isEngineer) {
                roleScore = 75;
                roleRationale = "Relevant professional profile matching target criteria.";
            }
        }

        boolean hasHiringPost = activity.stream().anyMatch(a -> a.classification().equals("HIRING_ANNOUNCEMENT") || a.classification().equals("INTERNSHIP_POST"));
        if (hasHiringPost) {
            roleScore = Math.min(100, roleScore + 15);
            roleRationale += " Verified recent public hiring announcement detected.";
        }

        // 3. Mentorship & Referral Leverage (0 - 100)
        int leverageScore = 30;
        if (roleLower.contains("staff") || roleLower.contains("principal") || roleLower.contains("architect") || isHiringManager || isFounder) {
            leverageScore = 90;
        } else if (isRecruiter) {
            leverageScore = 85;
        } else if (isEngineer) {
            leverageScore = 70;
        }

        // 4. Overall Weighted Score (0 - 100)
        int overallScore;
        if (isRecruiter) {
            overallScore = (int) Math.round((roleScore * 0.70) + (leverageScore * 0.20) + (techScore * 0.10));
        } else {
            overallScore = (int) Math.round((techScore * 0.40) + (roleScore * 0.40) + (leverageScore * 0.20));
        }
        overallScore = Math.min(100, Math.max(0, overallScore));

        PriorityTier tier;
        if (overallScore >= 75) {
            tier = PriorityTier.HIGH;
        } else if (overallScore >= 50) {
            tier = PriorityTier.MEDIUM;
        } else if (overallScore >= 25) {
            tier = PriorityTier.LOW;
        } else {
            tier = PriorityTier.NONE;
        }

        // 5. Why Relevant Narrative
        StringBuilder why = new StringBuilder();
        if (tier == PriorityTier.HIGH) {
            why.append("High priority match: ");
        } else if (tier == PriorityTier.MEDIUM) {
            why.append("Moderate priority match: ");
        } else {
            why.append("Lower priority candidate: ");
        }

        if (isRecruiter) {
            why.append(profile.currentRole()).append(" at ").append(profile.currentOrganization())
               .append(" is in talent acquisition with direct reach to hiring managers and open requisitions.");
        } else if (isHiringManager || isFounder) {
            why.append("Holding leadership as ").append(profile.currentRole()).append(" at ").append(profile.currentOrganization())
               .append(", they possess direct hiring oversight and referral leverage.");
        } else if (isEngineer) {
            why.append("Working as ").append(profile.currentRole()).append(" at ").append(profile.currentOrganization())
               .append(" with direct technical expertise in ").append(String.join(", ", matchedTopics.isEmpty() ? profile.technicalExpertise() : matchedTopics))
               .append(", suitable for peer technical evaluation and internal referrals.");
        } else {
            why.append("Profile contains background at ").append(profile.currentOrganization()).append(".");
        }

        List<String> strengths = new ArrayList<>();
        if (!"UNKNOWN".equals(profile.currentRole())) strengths.add("Established role as " + profile.currentRole());
        if (!"UNKNOWN".equals(profile.currentOrganization())) strengths.add("Affiliated with " + profile.currentOrganization());
        if (!matchedTopics.isEmpty()) strengths.add("Demonstrated expertise in " + String.join(", ", matchedTopics));
        if (hasHiringPost) strengths.add("Active public signals regarding hiring/internships");

        List<String> gaps = new ArrayList<>();
        if (matchedTopics.isEmpty() && !isRecruiter) gaps.add("Limited explicit evidence found for requested technical keywords");
        if (activity.isEmpty()) gaps.add("No recent public LinkedIn posts or articles retrieved");
        if ("UNKNOWN".equals(profile.location())) gaps.add("Location / office location could not be confirmed");

        Map<String, DimensionalScore> dimensions = new LinkedHashMap<>();
        dimensions.put("technicalRelevance", new DimensionalScore(techScore, techRationale));
        dimensions.put("roleRelevance", new DimensionalScore(roleScore, roleRationale));
        dimensions.put("leverageRelevance", new DimensionalScore(leverageScore, "Assessment of networking leverage, seniority, and organizational influence."));

        return new ObjectiveAssessment(overallScore, tier, why.toString(), dimensions, strengths, gaps);
    }

    private RecommendedApproach deriveRecommendedApproach(
            ResearchProfile profile, ObjectiveAssessment assessment, ResearchObjective objective
    ) {
        String roleLower = profile.currentRole().toLowerCase(Locale.ROOT);

        if (roleLower.contains("recruiter") || roleLower.contains("talent acquisition") || roleLower.contains("sourcer")) {
            return new RecommendedApproach(
                    ApproachType.RECRUITER_OUTREACH,
                    "Inquire directly regarding team openings and recruitment timelines.",
                    "As a talent acquisition professional at " + profile.currentOrganization() + ", they are the direct point of contact for candidate intake.",
                    List.of(
                            "Express interest in upcoming engineering/internship opportunities at " + profile.currentOrganization(),
                            "Highlight alignment with their current hiring scope",
                            "Inquire about the appropriate application channel or referral pipeline"
                    )
            );
        }

        if (roleLower.contains("manager") || roleLower.contains("director") || roleLower.contains("founder") || roleLower.contains("head")) {
            return new RecommendedApproach(
                    ApproachType.HIRING_CONVERSATION,
                    "Initiate an engineering team inquiry or mentorship/guidance dialogue.",
                    "As an engineering decision maker at " + profile.currentOrganization() + ", they have visibility into team growth and technical priorities.",
                    List.of(
                            "Reference their leadership role at " + profile.currentOrganization(),
                            "Mention your relevant technical background in " + (profile.technicalExpertise().isEmpty() ? "backend systems" : profile.technicalExpertise().get(0)),
                            "Ask for a brief introductory conversation or guidance on team expectations"
                    )
            );
        }

        if (roleLower.contains("staff") || roleLower.contains("principal") || roleLower.contains("architect")) {
            return new RecommendedApproach(
                    ApproachType.TECHNICAL_GUIDANCE,
                    "Reach out for architectural guidance or technical mentorship.",
                    "Senior engineering practitioners frequently provide high-value technical feedback and can champion exceptional candidates.",
                    List.of(
                            "Compliment specific systems work or technologies they use (" + String.join(", ", profile.technicalExpertise().subList(0, Math.min(2, profile.technicalExpertise().size()))) + ")",
                            "Ask a targeted technical question regarding production backend architecture",
                            "Inquire if their team currently accepts interns or referral applicants"
                    )
            );
        }

        return new RecommendedApproach(
                ApproachType.REFERRAL_REQUEST,
                "Request a peer informational conversation and potential referral.",
                "Engineering peers can evaluate technical skills and submit high-credibility internal employee referrals.",
                List.of(
                        "Share brief background matching their tech stack",
                        "Inquire about engineering culture and daily work at " + profile.currentOrganization(),
                        "Politely ask if they would consider submitting an internal referral"
                )
        );
    }
}
