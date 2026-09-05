package com.subdual.research_service.extraction;

import com.subdual.research_service.domain.ConfidenceTier;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.dto.AiExtractedFact;
import com.subdual.research_service.source.ExtractedDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceExtractor {

    private final AiExtractionClient aiExtractionClient;

    public EvidenceExtractor() {
        this(new NoOpAiExtractionClient());
    }

    @Autowired
    public EvidenceExtractor(AiExtractionClient aiExtractionClient) {
        this.aiExtractionClient = aiExtractionClient != null ? aiExtractionClient : new NoOpAiExtractionClient();
    }

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

        // 5. Enhance with Structured AI Extraction from Matched Documents
        if (aiExtractionClient != null) {
            enrichWithAiExtraction(target, documents, resolutions, attributes);
        }

        return attributes;
    }

    private void enrichWithAiExtraction(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        for (ExtractedDocument doc : documents) {
            EntityResolver.ResolutionResult res = resolutions.getOrDefault(doc.url(),
                    new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"));

            if (res.matched() && doc.cleanText() != null && !doc.cleanText().isBlank()) {
                Map<String, AiExtractedFact> facts = aiExtractionClient.extractFacts(
                        target.displayName(),
                        target.entityType() != null ? target.entityType().name() : "OTHER",
                        doc.url(),
                        doc.cleanText(),
                        List.of("role", "organization", "description", "summary", "headquarters", "technologies")
                );

                if (facts != null) {
                    facts.forEach((factKey, fact) -> {
                        if (fact != null && fact.value() != null && !fact.value().isBlank()) {
                            ConfidenceTier tier = fact.confidenceScore() >= 0.8
                                    ? ConfidenceTier.HIGH
                                    : (fact.confidenceScore() >= 0.5 ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW);

                            String snippet = (fact.exactQuote() != null && !fact.exactQuote().isBlank())
                                    ? "AI Quote: \"" + fact.exactQuote() + "\""
                                    : "AI Structured Extraction";

                            mergeAttribute(attributes, factKey, fact.value(), doc.url(), snippet, tier);
                        }
                    });
                }
            }
        }
    }

    private void mergeAttribute(
            Map<String, EvidenceTuple> attributes,
            String key,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        EvidenceTuple existing = attributes.get(key);
        if (existing == null) {
            attributes.put(key, new EvidenceTuple(value, sourceUrl, snippet, tier));
            return;
        }

        // Multi-Source Corroboration & Conflict Resolution
        List<String> sources = new java.util.ArrayList<>(existing.corroboratingSources() != null ? existing.corroboratingSources() : List.of());
        if (sourceUrl != null && !sources.contains(sourceUrl)) {
            sources.add(sourceUrl);
        }

        if (isAgreement(existing.value(), value)) {
            // Agreement detected -> boost confidence
            ConfidenceTier current = existing.confidence() != null ? existing.confidence() : ConfidenceTier.LOW;
            ConfidenceTier boostedTier = switch (current) {
                case UNKNOWN, LOW -> ConfidenceTier.MEDIUM;
                case MEDIUM, HIGH -> ConfidenceTier.HIGH;
            };

            String combinedSnippet = existing.evidenceSnippet() != null ? existing.evidenceSnippet() : snippet;
            if (snippet != null && !combinedSnippet.contains(snippet)) {
                combinedSnippet += " | Corroborating: " + snippet;
            }

            attributes.put(key, new EvidenceTuple(
                    existing.value(),
                    existing.sourceUrl(),
                    combinedSnippet,
                    boostedTier,
                    sources,
                    existing.conflictDetected()
            ));
        } else {
            // Disagreement detected -> resolve based on tier precedence
            int comp = compareConfidence(tier, existing.confidence());
            if (comp > 0) {
                // New incoming evidence is stronger
                String conflictSnippet = snippet + " (Alternative '" + existing.value() + "' found in " + existing.sourceUrl() + ")";
                attributes.put(key, new EvidenceTuple(
                        value,
                        sourceUrl,
                        conflictSnippet,
                        tier == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW,
                        sources,
                        true
                ));
            } else {
                // Existing evidence is stronger or equal
                String conflictSnippet = existing.evidenceSnippet() + " (Conflict: alternative '" + value + "' reported in " + sourceUrl + ")";
                ConfidenceTier resolvedTier = comp == 0 && existing.confidence() == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : existing.confidence();
                attributes.put(key, new EvidenceTuple(
                        existing.value(),
                        existing.sourceUrl(),
                        conflictSnippet,
                        resolvedTier,
                        sources,
                        true
                ));
            }
        }
    }

    private boolean isAgreement(String v1, String v2) {
        if (v1 == null || v2 == null) return false;
        String s1 = v1.trim().toLowerCase(java.util.Locale.ROOT);
        String s2 = v2.trim().toLowerCase(java.util.Locale.ROOT);
        return s1.equals(s2) || (s1.length() > 10 && s2.length() > 10 && (s1.contains(s2) || s2.contains(s1)));
    }

    private int compareConfidence(ConfidenceTier t1, ConfidenceTier t2) {
        if (t1 == t2) return 0;
        if (t1 == ConfidenceTier.HIGH) return 1;
        if (t2 == ConfidenceTier.HIGH) return -1;
        if (t1 == ConfidenceTier.MEDIUM) return 1;
        return -1;
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
