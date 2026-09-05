package com.subdual.research_service.extraction;

import java.time.Instant;

public record ExtractedDocument(
        String url, String title, String metaDescription,
        String siteName, String cleanText, Instant extractedAt ) {

    public ExtractedDocument(String url, String title, String cleanText, String metaDescription, String siteName) {
        this(url, title, metaDescription, siteName, cleanText, Instant.now());
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
