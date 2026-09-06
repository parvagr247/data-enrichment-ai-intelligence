package com.subdual.research_service.extraction.support;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.research.model.ConfidenceTier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles attribute merging, corroboration boosting, and conflict resolution across sources.
 */
@Component
public class EvidenceMerger {

    public void mergeAttribute(
            Map<String, EvidenceTuple> attributes,
            String key,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier
    ) {
        mergeAttribute(attributes, key, value, sourceUrl, snippet, tier, null, null);
    }

    public void mergeAttribute(
            Map<String, EvidenceTuple> attributes,
            String key,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier,
            String sourceType,
            String extractionMethod
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        ConfidenceTier effectiveTier = tier;
        if ("SEARCH_SNIPPET".equalsIgnoreCase(extractionMethod)) {
            // Snippets must not be treated as high-confidence direct source evidence
            if (effectiveTier == ConfidenceTier.HIGH) {
                effectiveTier = ConfidenceTier.MEDIUM;
            }
        }

        EvidenceTuple existing = attributes.get(key);
        if (existing == null) {
            attributes.put(key, new EvidenceTuple(
                    value,
                    sourceUrl,
                    snippet,
                    effectiveTier,
                    sourceUrl != null ? List.of(sourceUrl) : List.of(),
                    false,
                    null,
                    sourceType,
                    extractionMethod
            ));
            return;
        }

        List<String> sources = buildCorroboratingSources(existing, sourceUrl);
        if (isAgreement(existing.value(), value)) {
            attributes.put(key, corroborateAgreement(existing, value, snippet, sources, sourceType, extractionMethod));
        } else {
            attributes.put(key, resolveDisagreement(existing, value, sourceUrl, snippet, effectiveTier, sources, sourceType, extractionMethod));
        }
    }

    private List<String> buildCorroboratingSources(EvidenceTuple existing, String sourceUrl) {
        List<String> sources = new ArrayList<>(existing.corroboratingSources() != null ? existing.corroboratingSources() : List.of());
        if (sourceUrl != null && !sources.contains(sourceUrl)) {
            sources.add(sourceUrl);
        }
        return sources;
    }

    private EvidenceTuple corroborateAgreement(
            EvidenceTuple existing,
            String value,
            String snippet,
            List<String> sources,
            String sourceType,
            String extractionMethod
    ) {
        ConfidenceTier current = existing.confidence() != null ? existing.confidence() : ConfidenceTier.LOW;
        ConfidenceTier boostedTier = switch (current) {
            case UNKNOWN, LOW -> ConfidenceTier.MEDIUM;
            case MEDIUM, HIGH -> ConfidenceTier.HIGH;
        };

        String combinedSnippet = existing.evidenceSnippet() != null ? existing.evidenceSnippet() : snippet;
        if (snippet != null && !combinedSnippet.contains(snippet)) {
            combinedSnippet += " | Corroborating: " + snippet;
        }

        String mergedMethod = isDirectSource(existing.extractionMethod())
                ? existing.extractionMethod()
                : (extractionMethod != null ? extractionMethod : existing.extractionMethod());

        return new EvidenceTuple(
                existing.value(),
                existing.sourceUrl(),
                combinedSnippet,
                boostedTier,
                sources,
                existing.conflictDetected(),
                existing.conflictDescription(),
                existing.sourceType() != null ? existing.sourceType() : sourceType,
                mergedMethod
        );
    }

    private EvidenceTuple resolveDisagreement(
            EvidenceTuple existing,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier,
            List<String> sources,
            String sourceType,
            String extractionMethod
    ) {
        boolean existingIsDirect = isDirectSource(existing.extractionMethod());
        boolean newIsDirect = isDirectSource(extractionMethod);
        int comp;
        if (existingIsDirect && !newIsDirect) {
            comp = -1; // Existing direct source takes precedence over snippet
        } else if (!existingIsDirect && newIsDirect) {
            comp = 1;  // Incoming direct source takes precedence over existing snippet
        } else {
            comp = compareConfidence(tier, existing.confidence());
        }

        String conflictDesc = String.format("Conflict detected between '%s' (%s) and '%s' (%s)",
                existing.value(), existing.sourceUrl(), value, sourceUrl);

        if (comp > 0) {
            String conflictSnippet = snippet + " (Alternative '" + existing.value() + "' found in " + existing.sourceUrl() + ")";
            ConfidenceTier resolvedTier = tier == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW;
            return new EvidenceTuple(value, sourceUrl, conflictSnippet, resolvedTier, sources, true, conflictDesc, sourceType, extractionMethod);
        }

        String conflictSnippet = existing.evidenceSnippet() + " (Conflict: alternative '" + value + "' reported in " + sourceUrl + ")";
        ConfidenceTier resolvedTier = (comp == 0 && existing.confidence() == ConfidenceTier.HIGH)
                ? ConfidenceTier.MEDIUM
                : existing.confidence();
        return new EvidenceTuple(existing.value(), existing.sourceUrl(), conflictSnippet, resolvedTier, sources, true, conflictDesc, existing.sourceType(), existing.extractionMethod());
    }

    private boolean isDirectSource(String method) {
        if (method == null || method.isBlank()) {
            return false;
        }
        String m = method.toUpperCase(Locale.ROOT);
        return m.contains("DIRECT") || m.contains("FULL_PAGE");
    }

    private boolean isAgreement(String v1, String v2) {
        if (v1 == null || v2 == null) {
            return false;
        }
        String s1 = v1.trim().toLowerCase(Locale.ROOT);
        String s2 = v2.trim().toLowerCase(Locale.ROOT);
        return s1.equals(s2) || (s1.length() > 10 && s2.length() > 10 && (s1.contains(s2) || s2.contains(s1)));
    }

    private int compareConfidence(ConfidenceTier t1, ConfidenceTier t2) {
        if (t1 == t2) {
            return 0;
        }
        if (t1 == ConfidenceTier.HIGH) {
            return 1;
        }
        if (t2 == ConfidenceTier.HIGH) {
            return -1;
        }
        if (t1 == ConfidenceTier.MEDIUM) {
            return 1;
        }
        return -1;
    }
}
