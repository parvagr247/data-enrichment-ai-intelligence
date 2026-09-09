package com.subdual.ai_intelligent_service.planning.service.helper;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps natural language requirement phrases to standardized field keys.
 */
@Component
public class RequirementNormalizer {

    private static final Map<String, String> PHRASE_TO_FIELD_MAP = new LinkedHashMap<>();

    static {
        // Role / Title mappings
        List.of("job title", "job", "current role", "role", "position", "occupation", "headline", "designation", "profession")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "role"));

        // Organization / Employer mappings
        List.of("employer", "current company", "company", "organization", "firm", "business", "workplace", "corporation")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "organization"));

        // Education / School mappings
        List.of("where they studied", "where studied", "university", "college", "school", "degree", "alma mater", "education", "graduated")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "education"));

        // Location mappings
        List.of("city", "location", "country", "state", "headquarters", "hq", "where based", "based in", "geo", "address")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "location"));

        // Skills / Tech stack mappings
        List.of("tech stack", "technologies", "technology stack", "skills", "languages", "programming language", "frameworks")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "skills"));

        // Summary / Biography mappings
        List.of("summary", "bio", "biography", "about", "overview", "profile overview")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "summary"));

        // Funding / Investment mappings
        List.of("funding", "funding rounds", "investors", "lead investors", "series", "capital raised", "valuation")
                .forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "funding"));

        // Repository specific mappings
        List.of("stars", "stargazers", "github stars").forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "stars"));
        List.of("license", "open source license", "software license").forEach(k -> PHRASE_TO_FIELD_MAP.put(k, "license"));
    }

    public String normalizeFieldKey(String rawPhrase) {
        if (rawPhrase == null || rawPhrase.isBlank()) {
            return "UNKNOWN";
        }

        String cleaned = rawPhrase.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s_]", "")
                .replaceAll("\\s+", " ")
                .trim();

        // 1. Direct match in dictionary
        if (PHRASE_TO_FIELD_MAP.containsKey(cleaned)) {
            return PHRASE_TO_FIELD_MAP.get(cleaned);
        }

        // 2. Substring match
        for (Map.Entry<String, String> entry : PHRASE_TO_FIELD_MAP.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 3. Fallback to snake_case format
        return cleaned.replace(" ", "_");
    }

    public Set<String> extractNormalizedFieldsFromText(String naturalLanguageText) {
        if (naturalLanguageText == null || naturalLanguageText.isBlank()) {
            return Set.of();
        }

        String lower = naturalLanguageText.toLowerCase(Locale.ROOT);
        Set<String> fields = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : PHRASE_TO_FIELD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                fields.add(entry.getValue());
            }
        }

        return fields;
    }
}
