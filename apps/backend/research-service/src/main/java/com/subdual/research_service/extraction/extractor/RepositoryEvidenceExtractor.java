package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RepositoryEvidenceExtractor {

    private static final String[] TECH_KEYWORDS = {
            "Java", "Kotlin", "TypeScript", "JavaScript", "Python", "Go", "Rust", "Spring Boot", "Docker", "Kubernetes"
    };

    private final EvidenceMerger evidenceMerger;

    public RepositoryEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public EvidenceTuple extractRepositoryInfo(ResearchTarget target, List<ExtractedDocument> documents) {
        if (target == null || target.canonicalUrl() == null) {
            return null;
        }
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

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        populateNameIfPresent(target, attributes);
        extractTechnologiesFromDocuments(documents, resolutions, attributes);
    }

    private void populateNameIfPresent(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Repository name",
                    ConfidenceTier.HIGH
            ));
        }
    }

    private void extractTechnologiesFromDocuments(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        for (ExtractedDocument doc : documents) {
            if (shouldProcessDocument(doc, resolutions)) {
                extractTechnologiesFromDocument(doc, attributes);
            }
        }
    }

    private boolean shouldProcessDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)
                && doc.cleanText() != null
                && !doc.cleanText().isBlank();
    }

    private void extractTechnologiesFromDocument(ExtractedDocument doc, Map<String, EvidenceTuple> attributes) {
        List<String> techs = detectTechnologies(doc.cleanText());
        if (!techs.isEmpty() && !attributes.containsKey("technologies")) {
            String joined = String.join(", ", techs);
            evidenceMerger.mergeAttribute(attributes, "technologies", joined, doc.url(), "Technologies mentioned in documentation: " + joined, ConfidenceTier.MEDIUM);
        }
    }

    public List<String> detectTechnologies(String text) {
        List<String> detected = new ArrayList<>();
        if (text == null) {
            return detected;
        }
        for (String kw : TECH_KEYWORDS) {
            if (text.contains(kw)) {
                detected.add(kw);
            }
        }
        return detected;
    }
}
