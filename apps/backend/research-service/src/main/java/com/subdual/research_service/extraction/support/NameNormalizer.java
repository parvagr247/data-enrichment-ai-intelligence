package com.subdual.research_service.extraction.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Standardized entity name normalizer (Task 42).
 * Strips honorifics, academic titles, punctuation, emojis, and normalizes whitespace.
 */
public final class NameNormalizer {

    private static final Pattern EMOJI_AND_SPECIALS = Pattern.compile("[^a-zA-Z0-9\\s.'-]");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private static final Set<String> HONORIFICS = Set.of(
            "dr.", "dr", "mr.", "mr", "ms.", "ms", "mrs.", "mrs", "prof.", "prof",
            "sir", "madam", "phd", "md", "esq.", "esq"
    );

    private NameNormalizer() {}

    public static String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }

        String cleaned = EMOJI_AND_SPECIALS.matcher(rawName).replaceAll(" ");
        cleaned = MULTI_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        String[] tokens = cleaned.split(" ");
        StringBuilder sb = new StringBuilder();

        for (String token : tokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (!HONORIFICS.contains(lower)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(token);
            }
        }

        return sb.toString().trim();
    }

    public static String toComparisonKey(String rawName) {
        String norm = normalize(rawName);
        return norm.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
