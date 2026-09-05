package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts attributes specific to PRODUCT and WEBSITE entities.
 */
@Component
public class ProductEvidenceExtractor {

    private static final Pattern CATEGORY_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Category|Product Type):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );

    private final EvidenceMerger evidenceMerger;

    @Autowired
    public ProductEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public ProductEvidenceExtractor() {
        this(new EvidenceMerger());
    }

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Product name",
                    ConfidenceTier.HIGH
            ));
        }
        if (target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("website", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical product website",
                    ConfidenceTier.HIGH
            ));
        }

        for (ExtractedDocument doc : documents) {
            if (!CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)) {
                continue;
            }
            String text = doc.cleanText();
            if (text == null || text.isBlank()) {
                continue;
            }

            Matcher catMatch = CATEGORY_LABEL_PATTERN.matcher(text);
            if (catMatch.find()) {
                String cat = catMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "category", cat, doc.url(), "Category pattern: \"" + catMatch.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }
        }
    }

    public void extractWebsiteAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Website name",
                    ConfidenceTier.HIGH
            ));
        }
        if (target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("url", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical URL",
                    ConfidenceTier.HIGH
            ));
        }
    }
}
