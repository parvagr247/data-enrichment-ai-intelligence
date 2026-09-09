package com.subdual.ai_intelligent_service.planning.service.helper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates requested enrichment fields and guards against unbounded operations.
 */
@Component
public class RequirementValidator {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{1,35}$");
    private static final int MAX_FIELDS_PER_PLAN = 15;

    private static final Set<String> DISALLOWED_PATTERNS = Set.of(
            "password", "secret", "credit_card", "ssn", "token", "auth", "credential", "private_key"
    );

    public List<String> validateAndFilterFields(Collection<String> candidateFields, List<String> validationNotes) {
        if (candidateFields == null || candidateFields.isEmpty()) {
            return List.of();
        }

        List<String> validFields = new ArrayList<>();

        for (String field : candidateFields) {
            if (field == null || field.isBlank()) continue;
            String clean = field.trim().toLowerCase(Locale.ROOT).replace("-", "_");

            if (!SAFE_IDENTIFIER.matcher(clean).matches()) {
                validationNotes.add("Filtered invalid field token: '" + field + "' (must match [a-z][a-z0-9_]{1,35})");
                continue;
            }

            boolean isDisallowed = false;
            for (String dis : DISALLOWED_PATTERNS) {
                if (clean.contains(dis)) {
                    validationNotes.add("Rejected restricted field category: '" + clean + "'");
                    isDisallowed = true;
                    break;
                }
            }
            if (isDisallowed) continue;

            if (!validFields.contains(clean)) {
                validFields.add(clean);
            }

            if (validFields.size() >= MAX_FIELDS_PER_PLAN) {
                validationNotes.add("Capped total requested fields to maximum limit (" + MAX_FIELDS_PER_PLAN + ")");
                break;
            }
        }

        return validFields;
    }
}
