package com.subdual.research_service.extraction.support;

/**
 * Quality categorization for evidence items based on acquisition source and mechanism.
 */
public enum EvidenceQuality {
    /**
     * Extracted directly from retrieved, verified webpage text (HTTP 200).
     * Highest evidence fidelity.
     */
    DIRECT_SOURCE,

    /**
     * Extracted from search engine snippet/preview text when direct fetch failed (e.g. 999/403/timeout).
     * Secondary evidence fidelity; confidence capped at MEDIUM.
     */
    SEARCH_SNIPPET,

    /**
     * Extracted from third-party APIs or external providers.
     */
    OTHER_PROVIDER_RESULT,

    /**
     * Synthesized or inferred by LLM without direct verbatim textual quote.
     */
    DERIVED_INFERRED;

    public static EvidenceQuality fromMethod(String method) {
        if (method == null || method.isBlank()) {
            return DIRECT_SOURCE;
        }
        String m = method.trim().toUpperCase();
        if (m.contains("SNIPPET")) {
            return SEARCH_SNIPPET;
        }
        if (m.contains("PROVIDER")) {
            return OTHER_PROVIDER_RESULT;
        }
        if (m.contains("INFERRED") || m.contains("DERIVED")) {
            return DERIVED_INFERRED;
        }
        return DIRECT_SOURCE;
    }
}
