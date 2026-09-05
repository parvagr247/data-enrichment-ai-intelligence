package com.subdual.research_service.service;

import com.subdual.research_service.domain.ConfidenceTier;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.EvidenceTuple;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceExtractor {

    public Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> sources,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        if (target == null || documents == null || documents.isEmpty()) {
            return attributes;
        }

        // 1. Extract Description Attribute
        EvidenceTuple descriptionEvidence = extractDescription(target, documents, sources, resolutions);
        if (descriptionEvidence != null) {
            attributes.put("description", descriptionEvidence);
        }

        // 2. Extract Title / Display Name Attribute
        EvidenceTuple titleEvidence = extractTitle(target, documents, sources, resolutions);
        if (titleEvidence != null) {
            attributes.put("title", titleEvidence);
        }

        // 3. Extract Site / Publisher Name Attribute
        EvidenceTuple siteNameEvidence = extractSiteName(documents, resolutions);
        if (siteNameEvidence != null) {
            attributes.put("site_name", siteNameEvidence);
        }

        // 4. Extract Entity-Specific Attributes
        if (target.entityType() == EntityType.REPOSITORY) {
            EvidenceTuple repoEvidence = extractRepositoryInfo(target, documents);
            if (repoEvidence != null) {
                attributes.put("repository", repoEvidence);
            }
        }

        return attributes;
    }

    private EvidenceTuple extractDescription(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        // Look for strongest meta description or content excerpt from matched sources
        for (ExtractedDocument doc : documents) {
            if (doc.metaDescription() != null && !doc.metaDescription().isBlank()) {
                EntityResolver.ResolutionResult res = resolutions.getOrDefault(doc.url(),
                        new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));

                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : (res.confidence() == ConfidenceTier.MEDIUM ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW);

                    String snippet = "Meta description: \"" + doc.metaDescription() + "\"";
                    return new EvidenceTuple(doc.metaDescription(), doc.url(), snippet, tier);
                }
            }
        }

        // Fallback 1: extract first substantial paragraph from a matched document
        for (ExtractedDocument doc : documents) {
            EntityResolver.ResolutionResult res = resolutions.getOrDefault(doc.url(),
                    new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));
            if (res.matched() && doc.cleanText() != null && doc.cleanText().length() > 40) {
                String text = doc.cleanText();
                int endIdx = Math.min(text.length(), 200);
                int periodIdx = text.indexOf('.', 40);
                if (periodIdx > 0 && periodIdx <= endIdx) {
                    endIdx = periodIdx + 1;
                }
                String excerpt = text.substring(0, endIdx).trim();

                ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                        ? ConfidenceTier.MEDIUM
                        : ConfidenceTier.LOW;

                return new EvidenceTuple(excerpt, doc.url(), "Excerpt: \"" + excerpt + "\"", tier);
            }
        }

        // Fallback 2: Check if any matched source has a search snippet
        if (sources != null) {
            for (ResearchSource src : sources) {
                EntityResolver.ResolutionResult res = resolutions.getOrDefault(src.url(),
                        new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));
                if (res.matched() && src.snippet() != null && !src.snippet().isBlank()) {
                    return new EvidenceTuple(
                            src.snippet(),
                            src.url(),
                            "Search snippet: \"" + src.snippet() + "\"",
                            ConfidenceTier.LOW
                    );
                }
            }
        }

        return null;
    }

    private EvidenceTuple extractTitle(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.title() != null && !doc.title().isBlank()) {
                EntityResolver.ResolutionResult res = resolutions.getOrDefault(doc.url(),
                        new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));

                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : ConfidenceTier.MEDIUM;
                    return new EvidenceTuple(doc.title(), doc.url(), "Page title: \"" + doc.title() + "\"", tier);
                }
            }
        }

        if (sources != null) {
            for (ResearchSource src : sources) {
                EntityResolver.ResolutionResult res = resolutions.getOrDefault(src.url(),
                        new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));
                if (res.matched() && src.title() != null && !src.title().isBlank()) {
                    return new EvidenceTuple(src.title(), src.url(), "Source title: \"" + src.title() + "\"", ConfidenceTier.LOW);
                }
            }
        }

        return null;
    }

    private EvidenceTuple extractSiteName(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.siteName() != null && !doc.siteName().isBlank()) {
                EntityResolver.ResolutionResult res = resolutions.getOrDefault(doc.url(),
                        new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));

                if (res.matched()) {
                    return new EvidenceTuple(
                            doc.siteName(),
                            doc.url(),
                            "OpenGraph site_name: \"" + doc.siteName() + "\"",
                            ConfidenceTier.MEDIUM
                    );
                }
            }
        }

        return null;
    }

    private EvidenceTuple extractRepositoryInfo(ResearchTarget target, List<ExtractedDocument> documents) {
        try {
            URI uri = URI.create(target.canonicalUrl());
            if (uri.getHost() != null && uri.getHost().contains("github.com")) {
                String path = uri.getPath() != null ? uri.getPath().replaceAll("^/|/$", "") : "";
                if (path.contains("/")) {
                    return new EvidenceTuple(
                            path,
                            target.canonicalUrl(),
                            "Parsed repository coordinates from canonical target URL",
                            ConfidenceTier.HIGH
                    );
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
