package com.subdual.ai_intelligent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.dto.EnrichedAttributeResult;
import com.subdual.ai_intelligent_service.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.dto.FactEvidenceDto;
import com.subdual.ai_intelligent_service.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.normalization.AiOutputNormalizer;
import com.subdual.ai_intelligent_service.prompt.PromptTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SpringAiEnrichmentService implements EnrichmentAIService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;

    public SpringAiEnrichmentService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
    }

    @Override
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        String requirement = request != null ? request.requirement() : null;
        String entityType = request != null && request.entityType() != null ? request.entityType().toUpperCase(Locale.ROOT) : "PERSON";

        if (requirement == null || requirement.isBlank()) {
            return buildDefaultScope(entityType);
        }

        if (!isMockMode() && chatModel != null) {
            try {
                String promptText = String.format(PromptTemplates.REQUIREMENT_INTERPRETATION_PROMPT, entityType, requirement.trim());
                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
                return parseRequirementResponse(responseText);
            } catch (Exception ex) {
                log.warn("Spring AI requirement interpretation failed ({}), falling back to deterministic extraction", ex.getMessage());
            }
        }

        return interpretDeterministically(requirement, entityType);
    }

    @Override
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

    @Override
    public AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request) {
        long startTime = System.currentTimeMillis();
        String entityName = request != null && request.displayName() != null ? request.displayName() : "Unknown";
        String entityType = request != null && request.entityType() != null ? request.entityType() : "OTHER";
        String canonicalUrl = request != null && request.canonicalUrl() != null ? request.canonicalUrl() : "";

        if (!isMockMode() && chatModel != null) {
            try {
                String promptText = buildSynthesisPromptText(request);
                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
                AIEnrichmentResult result = parseSynthesisResponse(responseText, request, startTime);
                if (result != null) {
                    return result;
                }
            } catch (Exception ex) {
                log.warn("Spring AI enrichment synthesis failed ({}), falling back to deterministic synthesis", ex.getMessage());
            }
        }

        return synthesizeDeterministically(request, startTime);
    }

    private boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");
    }

    private RequirementInterpretationResponse buildDefaultScope(String entityType) {
        List<String> defaultFields = switch (entityType) {
            case "PERSON" -> List.of("currentOrganization", "currentRole", "education", "skills", "location");
            case "ORGANIZATION" -> List.of("description", "industry", "headquarters", "products", "employeeCount");
            case "PRODUCT" -> List.of("description", "vendor", "features", "pricing", "license");
            case "REPOSITORY" -> List.of("description", "owner", "license", "language", "stars");
            default -> List.of("description", "overview", "category");
        };
        return new RequirementInterpretationResponse(defaultFields, "Default automated enrichment scope for " + entityType, true);
    }

    private RequirementInterpretationResponse interpretDeterministically(String requirement, String entityType) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        List<String> fields = new ArrayList<>();

        if (lower.contains("company") || lower.contains("organization") || lower.contains("employer")) {
            fields.add("currentOrganization");
        }
        if (lower.contains("role") || lower.contains("title") || lower.contains("position") || lower.contains("job")) {
            fields.add("currentRole");
        }
        if (lower.contains("education") || lower.contains("degree") || lower.contains("university") || lower.contains("college") || lower.contains("studied")) {
            fields.add("education");
        }
        if (lower.contains("skill") || lower.contains("tech") || lower.contains("stack") || lower.contains("expertise")) {
            fields.add("skills");
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

    private String buildSynthesisPromptText(EnrichmentSynthesisRequest request) {
        String rawInputJson = toJsonSafe(request.rawInput());
        String targetFieldsJson = toJsonSafe(request.targetFields());
        String evidenceJson = toJsonSafe(request.researchEvidence());

        return String.format(PromptTemplates.ENRICHMENT_SYNTHESIS_PROMPT,
                request.displayName(),
                request.entityType(),
                request.canonicalUrl(),
                rawInputJson,
                request.userRequirement() != null ? request.userRequirement() : "Automatic enrichment",
                targetFieldsJson,
                evidenceJson,
                request.displayName(),
                request.entityType(),
                request.canonicalUrl()
        );
    }

    private String toJsonSafe(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private RequirementInterpretationResponse parseRequirementResponse(String responseText) {
        try {
            String cleaned = cleanJsonBlocks(responseText);
            JsonNode root = MAPPER.readTree(cleaned);
            List<String> fields = new ArrayList<>();
            if (root.has("requestedFields") && root.get("requestedFields").isArray()) {
                root.get("requestedFields").forEach(n -> fields.add(n.asText()));
            }
            String scope = root.has("scopeDescription") ? root.get("scopeDescription").asText() : "Interpreted scope";
            return new RequirementInterpretationResponse(fields, scope, false);
        } catch (Exception e) {
            log.warn("Failed to parse requirement interpretation response: {}", e.getMessage());
            return null;
        }
    }

    private AIEnrichmentResult parseSynthesisResponse(String responseText, EnrichmentSynthesisRequest request, long startTime) {
        try {
            String cleaned = cleanJsonBlocks(responseText);
            JsonNode root = MAPPER.readTree(cleaned);

            String displayName = root.has("displayName") ? root.get("displayName").asText() : request.displayName();
            String entityType = root.has("entityType") ? root.get("entityType").asText() : request.entityType();
            String canonicalUrl = root.has("canonicalUrl") ? root.get("canonicalUrl").asText() : request.canonicalUrl();
            double confidence = root.has("overallConfidence") ? root.get("overallConfidence").asDouble() : 0.85;

            Map<String, EnrichedAttributeResult> attributes = new LinkedHashMap<>();
            if (root.has("attributes") && root.get("attributes").isObject()) {
                root.get("attributes").fields().forEachRemaining(entry -> {
                    JsonNode node = entry.getValue();
                    String field = entry.getKey();
                    String val = node.has("value") ? node.get("value").asText() : "";
                    String origVal = node.has("originalValue") && !node.get("originalValue").isNull() ? node.get("originalValue").asText() : null;
                    String conf = node.has("confidence") ? node.get("confidence").asText() : "MEDIUM";
                    String status = node.has("status") ? node.get("status").asText() : "VERIFIED";
                    String evidence = node.has("evidence") ? node.get("evidence").asText() : "";
                    String notes = node.has("notes") ? node.get("notes").asText() : "";
                    List<String> sources = new ArrayList<>();
                    if (node.has("sources") && node.get("sources").isArray()) {
                        node.get("sources").forEach(s -> sources.add(s.asText()));
                    }
                    EnrichedAttributeResult norm = AiOutputNormalizer.normalizeAttribute(
                            new EnrichedAttributeResult(field, val, origVal, conf, status, sources, evidence, notes)
                    );
                    attributes.put(field, norm);
                });
            }

            List<String> unresolved = new ArrayList<>();
            if (root.has("unresolvedFields") && root.get("unresolvedFields").isArray()) {
                root.get("unresolvedFields").forEach(n -> unresolved.add(n.asText()));
            }

            List<String> conflicts = new ArrayList<>();
            if (root.has("conflicts") && root.get("conflicts").isArray()) {
                root.get("conflicts").forEach(n -> conflicts.add(n.asText()));
            }

            long elapsed = System.currentTimeMillis() - startTime;
            return new AIEnrichmentResult(displayName, entityType, canonicalUrl, attributes, unresolved, conflicts, confidence, properties.model(), elapsed);
        } catch (Exception e) {
            log.warn("Failed to parse AI synthesis response: {}", e.getMessage());
            return null;
        }
    }

    private AIEnrichmentResult synthesizeDeterministically(EnrichmentSynthesisRequest request, long startTime) {
        Map<String, EnrichedAttributeResult> attributes = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        Map<String, FactEvidenceDto> researchEvidence = request.researchEvidence();
        List<String> targets = request.targetFields() != null && !request.targetFields().isEmpty()
                ? request.targetFields()
                : new ArrayList<>(researchEvidence.keySet());

        Map<String, String> rawInput = request.rawInput();

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
                request.displayName(),
                request.entityType(),
                request.canonicalUrl(),
                attributes,
                unresolved,
                conflicts,
                confidence,
                "deterministic-grounding-engine",
                elapsed
        );
    }

    private FactEvidenceDto findMatchingEvidence(Map<String, FactEvidenceDto> evidence, String field) {
        if (evidence == null) return null;
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
            if ((clean.equals("currentrole") || clean.equals("role") || clean.equals("position")) && (candidate.equals("currentrole") || candidate.equals("role") || candidate.equals("position"))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String findOriginalValue(Map<String, String> rawInput, String field) {
        if (rawInput == null) return null;
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

    private String cleanJsonBlocks(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
    }
}
