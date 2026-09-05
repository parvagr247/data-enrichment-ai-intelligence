package com.subdual.research_service.extraction.support;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes target fields requested by the client, resolves aliases, synthesizes UNKNOWN tuples
 * for unverified fields, and prunes unrequested attributes.
 */
@Component
public class TargetFieldNormalizer {

    public void applyTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target == null || target.targetFields() == null || target.targetFields().isEmpty() || attributes == null) {
            return;
        }

        resolveFieldAliases(target.targetFields(), attributes);

        for (String field : target.targetFields()) {
            String cleanField = field.trim();
            if (!hasEstablishedField(attributes, cleanField)) {
                attributes.put(cleanField, createUnknownEvidenceTuple(cleanField));
            }
        }

        Set<String> requested = target.targetFields().stream()
                .map(f -> f.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());
        attributes.keySet().removeIf(k -> !requested.contains(k.toLowerCase(Locale.ROOT)) && !"name".equals(k));
    }

    private void resolveFieldAliases(List<String> targetFields, Map<String, EvidenceTuple> attributes) {
        for (String field : targetFields) {
            String lower = field.toLowerCase(Locale.ROOT).trim();
            if (hasEstablishedField(attributes, field) || hasEstablishedField(attributes, lower)) {
                continue;
            }
            if ((lower.equals("currentrole") || lower.equals("role")) && hasEstablishedField(attributes, "role")) {
                attributes.put(field, attributes.get("role"));
            } else if ((lower.equals("organization") || lower.equals("company") || lower.equals("current_organization")) && hasEstablishedField(attributes, "current_organization")) {
                attributes.put(field, attributes.get("current_organization"));
            } else if ((lower.equals("headquarters") || lower.equals("location")) && hasEstablishedField(attributes, "headquarters")) {
                attributes.put(field, attributes.get("headquarters"));
            } else if ((lower.equals("summary") || lower.equals("description")) && hasEstablishedField(attributes, "description")) {
                attributes.put(field, attributes.get("description"));
            }
        }
    }

    private boolean hasEstablishedField(Map<String, EvidenceTuple> attributes, String field) {
        EvidenceTuple tuple = attributes.get(field);
        if (tuple == null) {
            tuple = findByCaseInsensitiveKey(attributes, field);
        }
        return tuple != null && tuple.value() != null && !"UNKNOWN".equalsIgnoreCase(tuple.value().trim());
    }

    private EvidenceTuple findByCaseInsensitiveKey(Map<String, EvidenceTuple> attributes, String field) {
        for (Map.Entry<String, EvidenceTuple> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private EvidenceTuple createUnknownEvidenceTuple(String field) {
        return new EvidenceTuple(
                "UNKNOWN",
                null,
                "No reliable evidence found across researched sources for field: " + field,
                ConfidenceTier.UNKNOWN,
                List.of(),
                false
        );
    }
}
