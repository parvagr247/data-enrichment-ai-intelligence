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
 * Extracts attributes specific to ORGANIZATION entities such as headquarters, industry, products, and leadership.
 */
@Component
public class OrganizationEvidenceExtractor {

    private static final Pattern HQ_PATTERN = Pattern.compile(
            "(?i)(?:headquarters|headquartered in|based in):?\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );
    private static final Pattern INDUSTRY_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Industry|Sector):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );
    private static final Pattern PRODUCTS_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Products|Services|Key Products):\\s*([A-Za-z0-9\\s,.-]{2,50})"
    );
    private static final Pattern LEADERSHIP_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Founders?|Founded by|CEO|Leadership):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );

    private final EvidenceMerger evidenceMerger;

    @Autowired
    public OrganizationEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public OrganizationEvidenceExtractor() {
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
                    "Display name of organization",
                    ConfidenceTier.HIGH
            ));
        }
        if (target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("website", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical organization website",
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

            Matcher hqMatcher = HQ_PATTERN.matcher(text);
            if (hqMatcher.find()) {
                String hq = hqMatcher.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "headquarters", hq, doc.url(), "Headquarters pattern: \"" + hqMatcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }

            Matcher indMatch = INDUSTRY_LABEL_PATTERN.matcher(text);
            if (indMatch.find()) {
                String ind = indMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "industry", ind, doc.url(), "Industry label: \"" + indMatch.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }

            Matcher prodMatch = PRODUCTS_LABEL_PATTERN.matcher(text);
            if (prodMatch.find()) {
                String prod = prodMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "products", prod, doc.url(), "Products label: \"" + prodMatch.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }

            Matcher leadMatch = LEADERSHIP_LABEL_PATTERN.matcher(text);
            if (leadMatch.find()) {
                String lead = leadMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "leadership", lead, doc.url(), "Leadership label: \"" + leadMatch.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }
        }
    }
}
