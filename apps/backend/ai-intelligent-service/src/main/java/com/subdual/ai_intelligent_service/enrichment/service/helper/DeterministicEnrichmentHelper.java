package com.subdual.ai_intelligent_service.enrichment.service.helper;

import com.subdual.ai_intelligent_service.enrichment.api.dto.common.FactEvidenceDto;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.EnrichedAttributeResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.normalization.AiOutputNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeterministicEnrichmentHelper {

    public RequirementInterpretationResponse buildDefaultScope(String entityType) {
        List<String> defaultFields = switch (entityType) {
            case "PERSON" -> List.of("currentOrganization", "currentRole", "experience", "education", "skills", "projects", "activity", "location");
            case "ORGANIZATION" -> List.of("description", "industry", "headquarters", "products", "employeeCount");
            case "PRODUCT" -> List.of("description", "vendor", "features", "pricing", "license");
            case "REPOSITORY" -> List.of("description", "owner", "license", "language", "stars");
            default -> List.of("description", "overview", "category");
        };
        return new RequirementInterpretationResponse(defaultFields, "Default automated enrichment scope for " + entityType, true);
    }

    public RequirementInterpretationResponse interpretDeterministically(String requirement, String entityType) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        List<String> fields = new ArrayList<>();

        if (lower.contains("company") || lower.contains("organization") || lower.contains("employer")) {
            fields.add("currentOrganization");
        }
        if (lower.contains("role") || lower.contains("title") || lower.contains("position") || lower.contains("job")) {
            fields.add("currentRole");
        }
        if (lower.contains("experience") || lower.contains("career") || lower.contains("history") || lower.contains("employment") || lower.contains("background")) {
            fields.add("experience");
        }
        if (lower.contains("education") || lower.contains("degree") || lower.contains("university") || lower.contains("college") || lower.contains("studied")) {
            fields.add("education");
        }
        if (lower.contains("skill") || lower.contains("tech") || lower.contains("stack") || lower.contains("expertise") || lower.contains("proficiency")) {
            fields.add("skills");
        }
        if (lower.contains("project") || lower.contains("portfolio") || lower.contains("github") || lower.contains("repo") || lower.contains("code")) {
            fields.add("projects");
        }
        if (lower.contains("activity") || lower.contains("post") || lower.contains("article") || lower.contains("publication") || lower.contains("speaking")) {
            fields.add("activity");
        }
        if (lower.contains("location") || lower.contains("headquarter") || lower.contains("based") || lower.contains("city") || lower.contains("country")) {
            fields.add("location");
        }
        if (lower.contains("funding") || lower.contains("investment") || lower.contains("revenue") || lower.contains("valuation")) {
            fields.add("funding");
        }
        if (lower.contains("employee") || lower.contains("team") || lower.contains("size") || lower.contains("headcount")) {
            fields.add("employeeCount");
        }
        if (lower.contains("product") || lower.contains("service")) {
            fields.add("products");
        }
        if (lower.contains("license")) {
            fields.add("license");
        }
        if (lower.contains("industry") || lower.contains("domain")) {
            fields.add("industry");
        }

        if (fields.isEmpty() && requirement != null && !requirement.isBlank()) {
            String[] tokens = requirement.split("[,;\\n]|\\band\\b");
            for (String token : tokens) {
                String cleanToken = token.trim().replaceAll("[^a-zA-Z0-9_ ]", "");
                if (!cleanToken.isBlank() && cleanToken.length() <= 35) {
                    fields.add(toCamelCase(cleanToken));
                }
            }
        }

        if (fields.isEmpty()) {
            return buildDefaultScope(entityType);
        }

        return new RequirementInterpretationResponse(fields.stream().distinct().toList(), "Rule-interpreted user requirement", false);
    }

    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        Map<String, String> raw = request != null && request.rawInput() != null ? request.rawInput() : Map.of();
        Map<String, String> cleaned = new LinkedHashMap<>();
        Map<String, String> normalized = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().trim() : "";
            String val = entry.getValue() != null ? entry.getValue().trim() : "";
            cleaned.put(key, val);

            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (lowerKey.contains("name") && !lowerKey.contains("company") && !lowerKey.contains("org")) {
                normalized.put("name", val);
            } else if (lowerKey.contains("company") || lowerKey.contains("org") || lowerKey.contains("employer")) {
                normalized.put("organization", val);
            } else if (lowerKey.contains("role") || lowerKey.contains("position") || lowerKey.contains("title")) {
                normalized.put("role", val);
            } else if (lowerKey.contains("url") || lowerKey.contains("link") || lowerKey.contains("linkedin") || lowerKey.contains("website")) {
                normalized.put("url", val);
            }
        }

        notes.add("Cleaned " + raw.size() + " input fields without mutating original values.");
        return new InputCleansingResponse(cleaned, normalized, notes);
    }

    public AIEnrichmentResult synthesizeDeterministically(EnrichmentSynthesisRequest request, long startTime) {
        Map<String, EnrichedAttributeResult> attributes = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        Map<String, FactEvidenceDto> researchEvidence = request != null && request.researchEvidence() != null
                ? request.researchEvidence()
                : Map.of();
        List<String> targets = request != null && request.targetFields() != null && !request.targetFields().isEmpty()
                ? request.targetFields()
                : new ArrayList<>(researchEvidence.keySet());

        Map<String, String> rawInput = request != null ? request.rawInput() : Map.of();

        for (String targetField : targets) {
            FactEvidenceDto match = findMatchingEvidence(researchEvidence, targetField);
            String originalVal = findOriginalValue(rawInput, targetField);

            if (match != null && match.value() != null && !match.value().isBlank() && !"UNKNOWN".equalsIgnoreCase(match.value().trim())) {
                String status = match.conflictDetected() ? "CONFLICT" : "VERIFIED";
                String conf = match.conflictDetected() ? "MEDIUM" : (match.confidence() != null ? match.confidence() : "MEDIUM");
                if (match.conflictDetected()) {
                    conflicts.add("Conflicting evidence detected for field: " + targetField);
                }

                EnrichedAttributeResult norm = AiOutputNormalizer.normalizeAttribute(new EnrichedAttributeResult(
                        targetField,
                        match.value(),
                        originalVal,
                        conf,
                        status,
                        match.corroboratingSources() != null && !match.corroboratingSources().isEmpty()
                                ? match.corroboratingSources()
                                : (match.sourceUrl() != null ? List.of(match.sourceUrl()) : List.of()),
                        match.evidenceSnippet() != null ? match.evidenceSnippet() : "",
                        match.conflictDetected() ? "Conflicting evidence preserved across sources" : "Grounded in verified research evidence"
                ));
                attributes.put(targetField, norm);
            } else {
                unresolved.add(targetField);
                EnrichedAttributeResult norm = AiOutputNormalizer.normalizeAttribute(new EnrichedAttributeResult(
                        targetField,
                        "UNKNOWN",
                        originalVal,
                        "UNKNOWN",
                        "UNRESOLVED",
                        List.of(),
                        "",
                        "No verified evidence found in research sources"
                ));
                attributes.put(targetField, norm);
            }
        }

        double confidence = unresolved.isEmpty() ? 0.95 : (double) (targets.size() - unresolved.size()) / Math.max(1, targets.size());
        long elapsed = System.currentTimeMillis() - startTime;

        return new AIEnrichmentResult(
                request != null ? request.displayName() : null,
                request != null ? request.entityType() : null,
                request != null ? request.canonicalUrl() : null,
                attributes,
                unresolved,
                conflicts,
                confidence,
                "deterministic-grounding-engine",
                elapsed
        );
    }

    public FactEvidenceDto findMatchingEvidence(Map<String, FactEvidenceDto> evidence, String field) {
        if (evidence == null || field == null) return null;
        if (evidence.containsKey(field)) return evidence.get(field);

        String clean = field.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
        for (Map.Entry<String, FactEvidenceDto> entry : evidence.entrySet()) {
            String candidate = entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (candidate.equals(clean)) {
                return entry.getValue();
            }
            if ((clean.equals("currentorganization") || clean.equals("company")) && (candidate.equals("currentorganization") || candidate.equals("organization") || candidate.equals("company"))) {
                return entry.getValue();
            }
            if ((clean.equals("currentrole") || clean.equals("role") || clean.equals("position") || clean.equals("title")) && (candidate.equals("currentrole") || candidate.equals("role") || candidate.equals("position") || candidate.equals("title"))) {
                return entry.getValue();
            }
            if ((clean.equals("experience") || clean.equals("workexperience") || clean.equals("employment")) && (candidate.equals("experience") || candidate.equals("workexperience") || candidate.equals("employment"))) {
                return entry.getValue();
            }
            if ((clean.equals("skills") || clean.equals("technologies") || clean.equals("skill")) && (candidate.equals("skills") || candidate.equals("technologies") || candidate.equals("skill"))) {
                return entry.getValue();
            }
            if ((clean.equals("projects") || clean.equals("project") || clean.equals("portfolio")) && (candidate.equals("projects") || candidate.equals("project") || candidate.equals("portfolio"))) {
                return entry.getValue();
            }
            if ((clean.equals("activity") || clean.equals("activities") || clean.equals("posts") || clean.equals("publications")) && (candidate.equals("activity") || candidate.equals("activities") || candidate.equals("posts") || candidate.equals("publications"))) {
                return entry.getValue();
            }
            if ((clean.equals("education") || clean.equals("academic")) && (candidate.equals("education") || candidate.equals("academic"))) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String findOriginalValue(Map<String, String> rawInput, String field) {
        if (rawInput == null || field == null) return null;
        if (rawInput.containsKey(field)) return rawInput.get(field);

        String clean = field.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
        for (Map.Entry<String, String> entry : rawInput.entrySet()) {
            String k = entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (k.equals(clean) || (clean.contains("org") && k.contains("company")) || (clean.contains("role") && k.contains("position"))) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String cleanJsonBlocks(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
    }

    private String toCamelCase(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length == 0) return "";
        StringBuilder sb = new StringBuilder(words[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isBlank()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)))
                  .append(words[i].substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
