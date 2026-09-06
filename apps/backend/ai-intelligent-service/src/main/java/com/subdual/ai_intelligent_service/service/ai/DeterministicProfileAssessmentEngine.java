package com.subdual.ai_intelligent_service.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.dto.FactEvidenceDto;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class DeterministicProfileAssessmentEngine implements ProfileAssessmentEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        long startMs = System.currentTimeMillis();
        String name = resolveString(request.displayName(), "Unknown");
        String entityType = resolveString(request.entityType(), "PERSON");
        String canonicalUrl = resolveString(request.canonicalUrl(), "");
        Map<String, String> rawInput = request.rawInput();
        Map<String, FactEvidenceDto> evidence = request.researchEvidence();
        ResearchObjective objective = request.objective() != null ? request.objective() : ResearchObjective.blank();

        // 1. Extract Core Profile Attributes
        String role = extractField(evidence, rawInput, "currentRole", "role", "position", "title");
        String organization = extractField(evidence, rawInput, "currentOrganization", "organization", "company", "employer");
        String location = extractField(evidence, rawInput, "location", "city", "country", "headquarters");
        String education = extractField(evidence, rawInput, "education", "degree", "university", "college");

        // 2. Extract Technical Expertise & Skills
        List<String> technicalExpertise = extractSkillsList(evidence);

        // 3. Extract Experience & History
        List<ExperienceItem> experienceItems = extractExperienceItems(evidence, role, organization);

        // 4. Extract Projects
        List<ProjectItem> projectItems = extractProjectItems(evidence);

        // 5. Extract & Classify LinkedIn Activity & Posts
        List<ActivityItem> activityItems = extractActivityItems(evidence, request.sourceSnippets());

        // 6. Synthesize Multi-Sentence Professional Summary
        String professionalSummary = synthesizeProfessionalSummary(name, role, organization, location, education, technicalExpertise, activityItems);
        String careerBackground = synthesizeCareerBackground(name, role, organization, education, experienceItems);

        ResearchProfile profile = new ResearchProfile(
                role,
                organization,
                location,
                professionalSummary,
                careerBackground,
                technicalExpertise,
                experienceItems,
                projectItems,
                activityItems
        );

        // 7. Objective-Aware Assessment & Prioritization
        ObjectiveAssessment assessment;
        RecommendedApproach recommendation;

        if (objective.isBlank()) {
            assessment = ObjectiveAssessment.neutral("General profile research completed. No specific research objective was provided.");
            recommendation = new RecommendedApproach(
                    ApproachType.NETWORKING_CONVERSATION,
                    "Connect for general professional networking.",
                    "No objective filter was specified; connect based on shared professional domain.",
                    List.of(
                            "Discuss background at " + ("UNKNOWN".equals(organization) ? "their current company" : organization),
                            "Explore common interests in " + (technicalExpertise.isEmpty() ? "technology" : technicalExpertise.get(0))
                    )
            );
        } else {
            assessment = evaluateObjectiveAlignment(profile, objective, evidence, activityItems);
            recommendation = deriveRecommendedApproach(profile, assessment, objective);
        }

        // 8. Grounded Research Findings
        List<ResearchFinding> findings = compileGroundedFindings(evidence, profile, assessment);

        long durationMs = System.currentTimeMillis() - startMs;
        AiExecutionMetrics metrics = AiExecutionMetrics.deterministic(durationMs);

        return new ProfileAssessmentResponse(
                name,
                canonicalUrl,
                entityType,
                profile,
                assessment,
                recommendation,
                findings,
                metrics
        );
    }

    private String synthesizeProfessionalSummary(
            String name, String role, String org, String location, String education,
            List<String> skills, List<ActivityItem> activity
    ) {
        StringBuilder sb = new StringBuilder();
        if (!"UNKNOWN".equals(role) && !"UNKNOWN".equals(org)) {
            sb.append(name).append(" is a ").append(role).append(" at ").append(org);
            if (!"UNKNOWN".equals(location)) {
                sb.append(" based in ").append(location);
            }
            sb.append(". ");
        } else if (!"UNKNOWN".equals(role)) {
            sb.append(name).append(" is a ").append(role).append(". ");
        } else {
            sb.append(name).append(" is an established professional. ");
        }

        if (!"UNKNOWN".equals(education)) {
            sb.append("They hold an educational background in ").append(education).append(". ");
        }

        if (!skills.isEmpty()) {
            sb.append("Their core technical expertise spans ").append(String.join(", ", skills.subList(0, Math.min(4, skills.size())))).append(". ");
        }

        if (!activity.isEmpty()) {
            ActivityItem top = activity.get(0);
            sb.append("Public professional activity indicates active engagement in ").append(top.classification().toLowerCase(Locale.ROOT).replace("_", " ")).append(". ");
        }

        return sb.toString().trim();
    }

    private String synthesizeCareerBackground(
            String name, String role, String org, String education, List<ExperienceItem> experience
    ) {
        StringBuilder sb = new StringBuilder();
        if (!experience.isEmpty()) {
            sb.append("Career progression includes roles such as ");
            for (int i = 0; i < Math.min(3, experience.size()); i++) {
                ExperienceItem exp = experience.get(i);
                if (i > 0) sb.append(", ");
                sb.append(exp.role()).append(" at ").append(exp.organization());
            }
            sb.append(". ");
        } else if (!"UNKNOWN".equals(role) && !"UNKNOWN".equals(org)) {
            sb.append("Currently serving as ").append(role).append(" at ").append(org).append(". ");
        }

        if (!"UNKNOWN".equals(education)) {
            sb.append("Academic foundation includes ").append(education).append(". ");
        }

        return sb.length() > 0 ? sb.toString().trim() : "No detailed prior career records available.";
    }

    private ObjectiveAssessment evaluateObjectiveAlignment(
            ResearchProfile profile, ResearchObjective objective,
            Map<String, FactEvidenceDto> evidence, List<ActivityItem> activity
    ) {
        String roleLower = profile.currentRole().toLowerCase(Locale.ROOT);
        String orgLower = profile.currentOrganization().toLowerCase(Locale.ROOT);
        String summaryLower = profile.professionalSummary().toLowerCase(Locale.ROOT);
        String rawObjLower = objective.rawObjective().toLowerCase(Locale.ROOT);

        // 1. Technical Relevance (0 - 100)
        int techScore = 20; // baseline
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

        // Check if public activity contains hiring announcements
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
        // If objective is primarily hiring/internship, role and tech are co-weighted
        int overallScore;
        if (isRecruiter) {
            // For recruiters, role and hiring matter most; direct technical hands-on skill is secondary
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

        return new ObjectiveAssessment(
                overallScore,
                tier,
                why.toString(),
                dimensions,
                strengths,
                gaps
        );
    }

    private RecommendedApproach deriveRecommendedApproach(
            ResearchProfile profile, ObjectiveAssessment assessment, ResearchObjective objective
    ) {
        String roleLower = profile.currentRole().toLowerCase(Locale.ROOT);
        String rawObj = objective.rawObjective().toLowerCase(Locale.ROOT);

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

    private List<ResearchFinding> compileGroundedFindings(
            Map<String, FactEvidenceDto> evidence, ResearchProfile profile, ObjectiveAssessment assessment
    ) {
        List<ResearchFinding> findings = new ArrayList<>();

        if (evidence != null) {
            evidence.forEach((key, fact) -> {
                if (fact != null && fact.value() != null && !"UNKNOWN".equalsIgnoreCase(fact.value().trim())) {
                    findings.add(new ResearchFinding(
                            key + ": " + fact.value(),
                            FindingType.FACT_SOURCE_DERIVED,
                            fact.confidence() != null ? fact.confidence() : "HIGH",
                            fact.sourceUrl(),
                            fact.evidenceSnippet(),
                            "Research Evidence for " + key
                    ));
                }
            });
        }

        // Add the synthesized assessment as an explicit INFERRED_ASSESSMENT claim
        findings.add(new ResearchFinding(
                "Objective Assessment: " + assessment.whyRelevant(),
                FindingType.INFERRED_ASSESSMENT,
                assessment.priorityTier().name(),
                profile.currentOrganization(),
                "Synthesized from verified multi-source attributes and objective criteria.",
                "AI Objective Analysis"
        ));

        return findings;
    }

    private List<String> extractSkillsList(Map<String, FactEvidenceDto> evidence) {
        if (evidence == null) return List.of();
        FactEvidenceDto skillsFact = evidence.get("skills");
        if (skillsFact == null) skillsFact = evidence.get("tech");
        if (skillsFact == null) skillsFact = evidence.get("technologies");

        if (skillsFact != null && skillsFact.value() != null && !"UNKNOWN".equalsIgnoreCase(skillsFact.value().trim())) {
            String val = skillsFact.value().trim();
            if (val.startsWith("[") && val.endsWith("]")) {
                try {
                    List<?> list = MAPPER.readValue(val, List.class);
                    return list.stream().map(String::valueOf).toList();
                } catch (Exception ignored) {}
            }
            return Arrays.stream(val.split("[,;•|]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }

    private List<ExperienceItem> extractExperienceItems(Map<String, FactEvidenceDto> evidence, String currentRole, String currentOrg) {
        List<ExperienceItem> items = new ArrayList<>();
        if (!"UNKNOWN".equals(currentRole) && !"UNKNOWN".equals(currentOrg)) {
            items.add(new ExperienceItem(
                    currentRole,
                    currentOrg,
                    "Present",
                    currentRole + " at " + currentOrg,
                    "Verified current position"
            ));
        }

        FactEvidenceDto expFact = evidence != null ? evidence.get("experience") : null;
        if (expFact != null && expFact.value() != null && !"UNKNOWN".equalsIgnoreCase(expFact.value().trim())) {
            String val = expFact.value().trim();
            if (val.startsWith("[") && val.endsWith("]")) {
                try {
                    List<Map<String, Object>> list = MAPPER.readValue(val, List.class);
                    for (Map<String, Object> map : list) {
                        String r = String.valueOf(map.getOrDefault("role", "Engineer"));
                        String o = String.valueOf(map.getOrDefault("organization", "Company"));
                        String d = String.valueOf(map.getOrDefault("duration", ""));
                        items.add(new ExperienceItem(r, o, d, r + " at " + o, expFact.evidenceSnippet()));
                    }
                } catch (Exception ignored) {}
            }
        }

        return items;
    }

    private List<ProjectItem> extractProjectItems(Map<String, FactEvidenceDto> evidence) {
        List<ProjectItem> projects = new ArrayList<>();
        FactEvidenceDto projFact = evidence != null ? evidence.get("projects") : null;
        if (projFact != null && projFact.value() != null && !"UNKNOWN".equalsIgnoreCase(projFact.value().trim())) {
            String val = projFact.value().trim();
            if (val.startsWith("[") && val.endsWith("]")) {
                try {
                    List<Map<String, Object>> list = MAPPER.readValue(val, List.class);
                    for (Map<String, Object> map : list) {
                        String title = String.valueOf(map.getOrDefault("title", "Project"));
                        String desc = String.valueOf(map.getOrDefault("description", ""));
                        projects.add(new ProjectItem(title, desc, List.of(), projFact.sourceUrl()));
                    }
                } catch (Exception ignored) {}
            } else {
                projects.add(new ProjectItem("Featured Work", val, List.of(), projFact.sourceUrl()));
            }
        }
        return projects;
    }

    private List<ActivityItem> extractActivityItems(Map<String, FactEvidenceDto> evidence, List<String> sourceSnippets) {
        List<ActivityItem> activities = new ArrayList<>();
        FactEvidenceDto actFact = evidence != null ? evidence.get("activity") : null;

        if (actFact != null && actFact.value() != null && !"UNKNOWN".equalsIgnoreCase(actFact.value().trim())) {
            String val = actFact.value().trim();
            String[] entries = val.split("\\|");
            for (String entry : entries) {
                String clean = entry.trim();
                if (clean.isBlank()) continue;
                String type = clean.startsWith("[") && clean.contains("]") ? clean.substring(1, clean.indexOf("]")) : "AUTHORED";
                String title = clean.contains("]") ? clean.substring(clean.indexOf("]") + 1).trim() : clean;
                String classification = classifyActivityTitle(title);
                activities.add(new ActivityItem(title, type, title, classification, actFact.sourceUrl()));
            }
        }

        if (activities.isEmpty() && sourceSnippets != null) {
            for (String snippet : sourceSnippets) {
                String lower = snippet.toLowerCase(Locale.ROOT);
                if (lower.contains("hiring") || lower.contains("intern") || lower.contains("job") || lower.contains("join our team")) {
                    activities.add(new ActivityItem(
                            snippet.substring(0, Math.min(100, snippet.length())),
                            "AUTHORED",
                            snippet,
                            "HIRING_ANNOUNCEMENT",
                            null
                    ));
                    break;
                }
            }
        }

        return activities;
    }

    private String classifyActivityTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("hiring") || lower.contains("join our team") || lower.contains("open role") || lower.contains("we are hiring")) {
            return "HIRING_ANNOUNCEMENT";
        }
        if (lower.contains("intern") || lower.contains("internship") || lower.contains("student")) {
            return "INTERNSHIP_POST";
        }
        if (lower.contains("mentoring") || lower.contains("career advice") || lower.contains("guide") || lower.contains("tips")) {
            return "MENTORSHIP_GUIDANCE";
        }
        if (lower.contains("microservices") || lower.contains("architecture") || lower.contains("spring") || lower.contains("java") || lower.contains("cloud") || lower.contains("system")) {
            return "TECHNICAL_DISCUSSION";
        }
        if (lower.contains("leader") || lower.contains("founder") || lower.contains("strategy") || lower.contains("startup")) {
            return "LEADERSHIP_INSIGHT";
        }
        return "GENERAL_UPDATE";
    }

    private String extractField(Map<String, FactEvidenceDto> evidence, Map<String, String> raw, String... fieldCandidates) {
        if (evidence != null) {
            for (String cand : fieldCandidates) {
                FactEvidenceDto f = evidence.get(cand);
                if (f != null && f.value() != null && !f.value().isBlank() && !"UNKNOWN".equalsIgnoreCase(f.value().trim())) {
                    return f.value().trim();
                }
            }
        }
        if (raw != null) {
            for (String cand : fieldCandidates) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(cand) && entry.getValue() != null && !entry.getValue().isBlank()) {
                        return entry.getValue().trim();
                    }
                }
            }
        }
        return "UNKNOWN";
    }

    private String resolveString(String val, String fallback) {
        return val != null && !val.isBlank() ? val.trim() : fallback;
    }
}
