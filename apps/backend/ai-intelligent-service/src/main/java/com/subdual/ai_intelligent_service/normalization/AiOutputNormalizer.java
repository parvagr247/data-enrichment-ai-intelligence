package com.subdual.ai_intelligent_service.normalization;

import com.subdual.ai_intelligent_service.dto.EnrichedAttributeResult;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AiOutputNormalizer {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Set<String> UNKNOWN_TOKENS = Set.of(
            "unknown", "n/a", "na", "none", "not found", "not available",
            "no information", "no info", "unresolved", "undefined",
            "null", "-", "--", "nil"
    );

    private AiOutputNormalizer() {}

    /**
     * Normalizes a raw string attribute value:
     * - Strips HTML tags
     * - Trims and cleans multiple spaces
     * - Normalizes UNKNOWN variants into standard "UNKNOWN"
     * - Deduplicates comma-separated or semicolon-separated items
     */
    public static String normalizeValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "UNKNOWN";
        }

        String cleaned = HTML_TAG_PATTERN.matcher(rawValue).replaceAll(" ");
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();

        if (cleaned.isBlank()) {
            return "UNKNOWN";
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (UNKNOWN_TOKENS.contains(lower)) {
            return "UNKNOWN";
        }

        if ((cleaned.startsWith("[") && cleaned.endsWith("]")) || (cleaned.startsWith("{") && cleaned.endsWith("}"))) {
            return cleaned;
        }

        if (cleaned.contains(",") || cleaned.contains(";")) {
            return deduplicateList(cleaned);
        }

        return cleaned;
    }

    private static String deduplicateList(String listString) {
        String delimiter = listString.contains(";") ? ";" : ",";
        String[] parts = listString.split(Pattern.quote(delimiter));
        Set<String> seen = new LinkedHashSet<>();

        for (String part : parts) {
            String item = part.trim();
            if (!item.isBlank() && !UNKNOWN_TOKENS.contains(item.toLowerCase(Locale.ROOT))) {
                seen.add(item);
            }
        }

        if (seen.isEmpty()) {
            return "UNKNOWN";
        }

        return String.join(delimiter + " ", seen);
    }

    public static EnrichedAttributeResult normalizeAttribute(EnrichedAttributeResult attr) {
        if (attr == null) return null;

        String normVal = normalizeValue(attr.value());
        String normEvidence = attr.evidence() != null
                ? HTML_TAG_PATTERN.matcher(attr.evidence()).replaceAll(" ").trim()
                : "";
        String normNotes = attr.notes() != null
                ? HTML_TAG_PATTERN.matcher(attr.notes()).replaceAll(" ").trim()
                : "";

        boolean isUnknown = "UNKNOWN".equalsIgnoreCase(normVal);
        String status = isUnknown ? "UNRESOLVED" : attr.status();
        String confidence = isUnknown ? "UNKNOWN" : attr.confidence();

        List<String> sources = attr.sources() != null
                ? attr.sources().stream().filter(s -> s != null && !s.isBlank()).distinct().toList()
                : List.of();

        return new EnrichedAttributeResult(
                attr.field(),
                normVal,
                attr.originalValue(),
                confidence,
                status,
                sources,
                normEvidence,
                normNotes
        );
    }
}
