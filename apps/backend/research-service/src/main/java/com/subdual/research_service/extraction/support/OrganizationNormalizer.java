package com.subdual.research_service.extraction.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Standardized organization/company normalizer.
 * Strips corporate legal suffixes and normalizes formatting.
 */
public final class OrganizationNormalizer {

    private static final Pattern CORPORATE_SUFFIXES = Pattern.compile(
            "(?i)\\b(inc\\.?|incorporated|llc\\.?|l\\.l\\.c\\.?|ltd\\.?|limited|corp\\.?|corporation|co\\.?|company|gmbh|ag|pvt\\.?\\s*ltd\\.?|pty\\.?\\s*ltd\\.?)\\b"
    );
    private static final Pattern CLEAN_SYMBOLS = Pattern.compile("[,.'\"\\-]");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private OrganizationNormalizer() {}

    public static String normalize(String rawOrg) {
        if (rawOrg == null || rawOrg.isBlank()) {
            return "";
        }

        String cleaned = CORPORATE_SUFFIXES.matcher(rawOrg).replaceAll(" ");
        cleaned = CLEAN_SYMBOLS.matcher(cleaned).replaceAll(" ");
        cleaned = MULTI_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        return cleaned;
    }

    public static String toComparisonKey(String rawOrg) {
        String norm = normalize(rawOrg);
        return norm.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
