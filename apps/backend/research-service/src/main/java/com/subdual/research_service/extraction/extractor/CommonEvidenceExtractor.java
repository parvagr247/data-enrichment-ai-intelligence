package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Extracts common, document-level evidence tuples such as descriptions, titles, and site names.
 */
@Component
public class CommonEvidenceExtractor {

    public EvidenceTuple extractDescription(
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

    public EvidenceTuple extractMetaDescription(
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

    public EvidenceTuple extractParagraphExcerpt(
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

    public EvidenceTuple extractSearchSnippet(
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

    public EvidenceTuple extractTitle(
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

    public EvidenceTuple extractDocumentTitle(
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

    public EvidenceTuple extractSourceTitle(
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

    public EvidenceTuple extractSiteName(
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

    public static boolean isMatchedDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
        return res.matched() && doc.cleanText() != null && !doc.cleanText().isBlank();
    }

    public static EntityResolver.ResolutionResult getResolution(String url, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return resolutions != null
                ? resolutions.getOrDefault(url, new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"))
                : new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown");
    }
}
