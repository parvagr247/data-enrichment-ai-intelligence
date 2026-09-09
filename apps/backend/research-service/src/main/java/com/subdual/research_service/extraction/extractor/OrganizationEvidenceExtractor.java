package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
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

    public OrganizationEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        populateIdentityDefaults(target, attributes);

        for (ExtractedDocument doc : documents) {
            if (shouldProcessDocument(doc, resolutions)) {
                extractFromDocument(doc, attributes);
            }
        }
    }

    private void populateIdentityDefaults(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Display name of organization",
                    ConfidenceTier.HIGH
            ));
        }
        if (target != null && target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("website", new EvidenceTuple(
                    target.canonicalUrl(),
                    target.canonicalUrl(),
                    "Canonical organization website",
                    ConfidenceTier.HIGH
            ));
        }
    }

    private boolean shouldProcessDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)
                && doc.cleanText() != null
                && !doc.cleanText().isBlank();
    }

    private void extractFromDocument(ExtractedDocument doc, Map<String, EvidenceTuple> attributes) {
        String text = doc.cleanText();
        String url = doc.url();

        extractHeadquarters(text, url, attributes);
        extractIndustry(text, url, attributes);
        extractProducts(text, url, attributes);
        extractLeadership(text, url, attributes);
    }

    private void extractHeadquarters(String text, String url, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = HQ_PATTERN.matcher(text);
        if (matcher.find()) {
            String hq = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "headquarters", hq, url, "Headquarters pattern: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
        }
    }

    private void extractIndustry(String text, String url, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = INDUSTRY_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String ind = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "industry", ind, url, "Industry label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
        }
    }

    private void extractProducts(String text, String url, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = PRODUCTS_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String prod = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "products", prod, url, "Products label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
        }
    }

    private void extractLeadership(String text, String url, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = LEADERSHIP_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String lead = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "leadership", lead, url, "Leadership label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
        }
    }
}
