package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductEvidenceExtractor {

    private static final Pattern CATEGORY_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Category|Product Type):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );

    private final EvidenceMerger evidenceMerger;

    public ProductEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        populateProductDefaults(target, attributes);
        extractCategoriesFromDocuments(documents, resolutions, attributes);
    }

    public void extractWebsiteAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Website name",
                    ConfidenceTier.HIGH
            ));
        }
        if (target != null && target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("url", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical URL",
                    ConfidenceTier.HIGH
            ));
        }
    }

    private void populateProductDefaults(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Product name",
                    ConfidenceTier.HIGH
            ));
        }
        if (target != null && target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("website", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical product website",
                    ConfidenceTier.HIGH
            ));
        }
    }

    private void extractCategoriesFromDocuments(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        for (ExtractedDocument doc : documents) {
            if (shouldProcessDocument(doc, resolutions)) {
                extractCategoryFromDocument(doc, attributes);
            }
        }
    }

    private boolean shouldProcessDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)
                && doc.cleanText() != null
                && !doc.cleanText().isBlank();
    }

    private void extractCategoryFromDocument(ExtractedDocument doc, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = CATEGORY_LABEL_PATTERN.matcher(doc.cleanText());
        if (matcher.find()) {
            String cat = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "category", cat, doc.url(), "Category pattern: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
        }
    }
}
