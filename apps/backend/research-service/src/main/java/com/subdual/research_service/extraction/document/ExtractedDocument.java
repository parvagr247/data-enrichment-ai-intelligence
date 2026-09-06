package com.subdual.research_service.extraction.document;

import java.time.Instant;

/**
 * Immutable representation of a parsed and cleaned document retrieved during research discovery.
 */
public record ExtractedDocument(
        String url,
        String title,
        String metaDescription,
        String siteName,
        String cleanText,
        Instant extractedAt,
        String extractionMethod
) {

    public ExtractedDocument {
        if (extractionMethod == null || extractionMethod.isBlank()) {
            extractionMethod = "FULL_PAGE";
        }
    }

    public ExtractedDocument(String url, String title, String metaDescription, String siteName, String cleanText, Instant extractedAt) {
        this(url, title, metaDescription, siteName, cleanText, extractedAt, "FULL_PAGE");
    }

    public ExtractedDocument(String url, String title, String cleanText, String metaDescription, String siteName) {
        this(url, title, metaDescription, siteName, cleanText, Instant.now(), "FULL_PAGE");
    }

    public ExtractedDocument(String url, String title, String cleanText, String metaDescription, String siteName, String extractionMethod) {
        this(url, title, metaDescription, siteName, cleanText, Instant.now(), extractionMethod);
    }

    public String textContent() {
        return cleanText != null ? cleanText : "";
    }

    public String ogSiteName() {
        return siteName;
    }

    public String ogTitle() {
        return title;
    }
}
