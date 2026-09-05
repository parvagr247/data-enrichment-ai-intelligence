package com.subdual.research_service.extraction;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class EvidenceExtractor {

    private final AiExtractionClient aiExtractionClient;

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

        putIfPresent(attributes, "description", extractDescription(target, documents, sources, resolutions));
        putIfPresent(attributes, "title", extractTitle(target, documents, sources, resolutions));
        putIfPresent(attributes, "site_name", extractSiteName(documents, resolutions));

        if (target.entityType() == EntityType.REPOSITORY) {
            putIfPresent(attributes, "repository", extractRepositoryInfo(target, documents));
        }

        if (aiExtractionClient != null) {
            enrichWithAiExtraction(target, documents, resolutions, attributes);
        }

        return attributes;
    }

    private void putIfPresent(Map<String, EvidenceTuple> attributes, String key, EvidenceTuple evidence) {
        if (evidence != null) {
            attributes.put(key, evidence);
        }
    }

    private void enrichWithAiExtraction(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        for (ExtractedDocument doc : documents) {
            if (!isMatchedDocument(doc, resolutions)) {
                continue;
            }

            Map<String, AiExtractedFact> facts = aiExtractionClient.extractFacts(
                    target.displayName(),
                    target.entityType() != null ? target.entityType().name() : "OTHER",
                    doc.url(),
                    doc.cleanText(),
                    List.of("role", "organization", "description", "summary", "headquarters", "technologies")
            );

            mergeAiFacts(facts, doc.url(), attributes);
        }
    }

    private boolean isMatchedDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
        return res.matched() && doc.cleanText() != null && !doc.cleanText().isBlank();
    }

    private void mergeAiFacts(Map<String, AiExtractedFact> facts, String sourceUrl, Map<String, EvidenceTuple> attributes) {
        if (facts == null) {
            return;
        }

        facts.forEach((factKey, fact) -> {
            if (fact != null && fact.value() != null && !fact.value().isBlank()) {
                ConfidenceTier tier = resolveAiConfidenceTier(fact.confidenceScore());
                String snippet = resolveAiSnippet(fact.exactQuote());
                mergeAttribute(attributes, factKey, fact.value(), sourceUrl, snippet, tier);
            }
        });
    }

    private ConfidenceTier resolveAiConfidenceTier(double score) {
        if (score >= 0.8) return ConfidenceTier.HIGH;
        if (score >= 0.5) return ConfidenceTier.MEDIUM;
        return ConfidenceTier.LOW;
    }

    private String resolveAiSnippet(String exactQuote) {
        return (exactQuote != null && !exactQuote.isBlank())
                ? "AI Quote: \"" + exactQuote + "\""
                : "AI Structured Extraction";
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

        List<String> sources = buildCorroboratingSources(existing, sourceUrl);
        if (isAgreement(existing.value(), value)) {
            attributes.put(key, corroborateAgreement(existing, value, snippet, sources));
        } else {
            attributes.put(key, resolveDisagreement(existing, value, sourceUrl, snippet, tier, sources));
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
            List<String> sources
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

        return new EvidenceTuple(
                existing.value(),
                existing.sourceUrl(),
                combinedSnippet,
                boostedTier,
                sources,
                existing.conflictDetected()
        );
    }

    private EvidenceTuple resolveDisagreement(
            EvidenceTuple existing,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier,
            List<String> sources
    ) {
        int comp = compareConfidence(tier, existing.confidence());
        if (comp > 0) {
            String conflictSnippet = snippet + " (Alternative '" + existing.value() + "' found in " + existing.sourceUrl() + ")";
            ConfidenceTier resolvedTier = tier == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW;
            return new EvidenceTuple(value, sourceUrl, conflictSnippet, resolvedTier, sources, true);
        }

        String conflictSnippet = existing.evidenceSnippet() + " (Conflict: alternative '" + value + "' reported in " + sourceUrl + ")";
        ConfidenceTier resolvedTier = (comp == 0 && existing.confidence() == ConfidenceTier.HIGH)
                ? ConfidenceTier.MEDIUM
                : existing.confidence();
        return new EvidenceTuple(existing.value(), existing.sourceUrl(), conflictSnippet, resolvedTier, sources, true);
    }

    private boolean isAgreement(String v1, String v2) {
        if (v1 == null || v2 == null) return false;
        String s1 = v1.trim().toLowerCase(Locale.ROOT);
        String s2 = v2.trim().toLowerCase(Locale.ROOT);
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
        EvidenceTuple metaDesc = extractMetaDescription(documents, resolutions);
        if (metaDesc != null) {
            return metaDesc;
        }

        EvidenceTuple excerpt = extractParagraphExcerpt(documents, resolutions);
        if (excerpt != null) {
            return excerpt;
        }

        return extractSearchSnippet(sources, resolutions);
    }

    private EvidenceTuple extractMetaDescription(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.metaDescription() != null && !doc.metaDescription().isBlank()) {
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : (res.confidence() == ConfidenceTier.MEDIUM ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW);
                    return new EvidenceTuple(doc.metaDescription(), doc.url(), "Meta description: \"" + doc.metaDescription() + "\"", tier);
                }
            }
        }
        return null;
    }

    private EvidenceTuple extractParagraphExcerpt(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
            if (res.matched() && doc.cleanText() != null && doc.cleanText().length() > 40) {
                String text = doc.cleanText();
                int endIdx = Math.min(text.length(), 200);
                int periodIdx = text.indexOf('.', 40);
                if (periodIdx > 0 && periodIdx <= endIdx) {
                    endIdx = periodIdx + 1;
                }
                String excerpt = text.substring(0, endIdx).trim();
                ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW;
                return new EvidenceTuple(excerpt, doc.url(), "Excerpt: \"" + excerpt + "\"", tier);
            }
        }
        return null;
    }

    private EvidenceTuple extractSearchSnippet(
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (sources == null) {
            return null;
        }
        for (ResearchSource src : sources) {
            EntityResolver.ResolutionResult res = getResolution(src.url(), resolutions);
            if (res.matched() && src.snippet() != null && !src.snippet().isBlank()) {
                return new EvidenceTuple(
                        src.snippet(),
                        src.url(),
                        "Search snippet: \"" + src.snippet() + "\"",
                        ConfidenceTier.LOW
                );
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
        EvidenceTuple docTitle = extractDocumentTitle(documents, resolutions);
        if (docTitle != null) {
            return docTitle;
        }
        return extractSourceTitle(sources, resolutions);
    }

    private EvidenceTuple extractDocumentTitle(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.title() != null && !doc.title().isBlank()) {
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : ConfidenceTier.MEDIUM;
                    return new EvidenceTuple(doc.title(), doc.url(), "Page title: \"" + doc.title() + "\"", tier);
                }
            }
        }
        return null;
    }

    private EvidenceTuple extractSourceTitle(
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (sources == null) {
            return null;
        }
        for (ResearchSource src : sources) {
            EntityResolver.ResolutionResult res = getResolution(src.url(), resolutions);
            if (res.matched() && src.title() != null && !src.title().isBlank()) {
                return new EvidenceTuple(src.title(), src.url(), "Source title: \"" + src.title() + "\"", ConfidenceTier.LOW);
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
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
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

    private EntityResolver.ResolutionResult getResolution(String url, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return resolutions != null
                ? resolutions.getOrDefault(url, new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"))
                : new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown");
    }
}
