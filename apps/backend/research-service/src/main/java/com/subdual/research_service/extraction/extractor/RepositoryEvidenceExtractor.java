package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts attributes specific to REPOSITORY entities such as repository coordinates and detected technologies.
 */
@Component
public class RepositoryEvidenceExtractor {

    private static final String[] TECH_KEYWORDS = {
            "Java", "Kotlin", "TypeScript", "JavaScript", "Python", "Go", "Rust", "Spring Boot", "Docker", "Kubernetes"
    };

    private final EvidenceMerger evidenceMerger;

    @Autowired
    public RepositoryEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public RepositoryEvidenceExtractor() {
        this(new EvidenceMerger());
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
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Repository name",
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

            List<String> techs = detectTechnologies(text);
            if (!techs.isEmpty() && !attributes.containsKey("technologies")) {
                String joined = String.join(", ", techs);
                evidenceMerger.mergeAttribute(attributes, "technologies", joined, doc.url(), "Technologies mentioned in documentation: " + joined, ConfidenceTier.MEDIUM);
            }
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
