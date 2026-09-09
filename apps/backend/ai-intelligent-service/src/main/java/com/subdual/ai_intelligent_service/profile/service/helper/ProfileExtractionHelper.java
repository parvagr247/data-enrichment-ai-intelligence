package com.subdual.ai_intelligent_service.profile.service.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.enrichment.api.dto.FactEvidenceDto;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding.FindingType;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ActivityItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ExperienceItem;
import com.subdual.ai_intelligent_service.profile.model.ResearchProfile.ProjectItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProfileExtractionHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String synthesizeProfessionalSummary(
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

    public String synthesizeCareerBackground(
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

    public List<ResearchFinding> compileGroundedFindings(
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

    public List<String> extractSkillsList(Map<String, FactEvidenceDto> evidence) {
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

    public List<ExperienceItem> extractExperienceItems(Map<String, FactEvidenceDto> evidence, String currentRole, String currentOrg) {
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

    public List<ProjectItem> extractProjectItems(Map<String, FactEvidenceDto> evidence) {
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

    public List<ActivityItem> extractActivityItems(Map<String, FactEvidenceDto> evidence, List<String> sourceSnippets) {
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

    public String classifyActivityTitle(String title) {
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

    public String extractField(Map<String, FactEvidenceDto> evidence, Map<String, String> raw, String... fieldCandidates) {
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

    public String resolveString(String val, String fallback) {
        return val != null && !val.isBlank() ? val.trim() : fallback;
    }

    public String cleanJsonBlocks(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
