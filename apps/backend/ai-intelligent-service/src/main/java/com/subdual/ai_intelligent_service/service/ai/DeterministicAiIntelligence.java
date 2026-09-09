package com.subdual.ai_intelligent_service.service.ai;

import com.subdual.ai_intelligent_service.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based fallback implementation of AiIntelligence.
 * Provides reliable, fast heuristics when AI model is in mock mode or experiences transient failures.
 */
@Component
public class DeterministicAiIntelligence implements AiIntelligence {

    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:is|as|works as)\\s+(?:a|an)?\\s+([A-Z][a-zA-Z\\s]{2,35}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO))"
    );
    private static final Pattern ORG_PATTERN = Pattern.compile(
            "(?i)\\b(?:at|for)\\s+([A-Z][a-zA-Z0-9&\\s]{2,30})\\b"
    );
    private static final Pattern EDU_PATTERN = Pattern.compile(
            "(?i)\\b(?:graduated from|degree from|studied at)\\s+([A-Z][a-zA-Z0-9&\\s]{2,35})\\b"
    );
    private static final Pattern LOC_PATTERN = Pattern.compile(
            "(?i)\\b(?:based in|located in|headquartered in)\\s+([A-Z][a-zA-Z0-9,\\s]{2,35})\\b"
    );
    private static final Pattern SKILLS_PATTERN = Pattern.compile(
            "(?i)\\b(?:skills:|expertise in|proficient in)\\s+([a-zA-Z0-9,\\s/+-]{2,50})\\b"
    );

    private volatile AiExecutionMetrics latestMetrics = AiExecutionMetrics.deterministic(0L);

    @Override
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        long start = System.currentTimeMillis();
        String req = request != null && request.requirement() != null ? request.requirement() : "";
        String entityType = request != null && request.entityType() != null ? request.entityType() : "PERSON";

        List<String> fields = new ArrayList<>();
        String lower = req.toLowerCase(Locale.ROOT);

        if (lower.contains("role") || lower.contains("title") || lower.contains("position")) fields.add("currentRole");
        if (lower.contains("company") || lower.contains("organization") || lower.contains("employer")) fields.add("currentOrganization");
        if (lower.contains("education") || lower.contains("degree") || lower.contains("university") || lower.contains("school")) fields.add("education");
        if (lower.contains("location") || lower.contains("city") || lower.contains("country")) fields.add("location");
        if (lower.contains("skill") || lower.contains("tech") || lower.contains("language")) fields.add("skills");

        if (fields.isEmpty()) {
            fields.addAll(List.of("currentRole", "currentOrganization", "location"));
        }

        long duration = System.currentTimeMillis() - start;
        latestMetrics = AiExecutionMetrics.deterministic(duration);
        return new RequirementInterpretationResponse(fields, "Determined " + fields.size() + " fields for " + entityType, false);
    }

    @Override
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        long start = System.currentTimeMillis();
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();

        if (request != null && request.textContent() != null && !request.textContent().isBlank()) {
            String text = request.textContent();
            extractRegexFact(text, ROLE_PATTERN, "role", facts);
            extractRegexFact(text, ORG_PATTERN, "organization", facts);
            extractRegexFact(text, EDU_PATTERN, "education", facts);
            extractRegexFact(text, LOC_PATTERN, "location", facts);
            extractRegexFact(text, SKILLS_PATTERN, "skills", facts);
        }

        long duration = System.currentTimeMillis() - start;
        latestMetrics = AiExecutionMetrics.deterministic(duration);

        return new ExtractionResponse(
                request != null ? request.entityName() : "",
                request != null ? request.sourceUrl() : "",
                facts,
                "deterministic-rule-engine",
                duration
        );
    }

    @Override
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        Map<String, String> raw = request != null && request.rawInput() != null ? request.rawInput() : Map.of();
        Map<String, String> cleaned = new LinkedHashMap<>();
        Map<String, String> normalized = new LinkedHashMap<>();

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
            } else if (lowerKey.contains("url") || lowerKey.contains("link")) {
                normalized.put("url", val);
            }
        }

        return new InputCleansingResponse(cleaned, normalized, List.of("Cleaned " + raw.size() + " fields."));
    }

    @Override
    public AiExecutionMetrics getLatestMetrics() {
        return latestMetrics;
    }

    private void extractRegexFact(String text, Pattern pattern, String fieldKey, Map<String, ExtractedFact> facts) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String value = m.group(1).trim();
            int start = Math.max(0, text.lastIndexOf('.', m.start()) + 1);
            int end = text.indexOf('.', m.end());
            if (end == -1) end = Math.min(text.length(), m.end() + 80);
            else end = Math.min(text.length(), end + 1);

            String quote = text.substring(start, end).trim();
            facts.put(fieldKey, new ExtractedFact(value, quote, 0.85));
        }
    }
}
