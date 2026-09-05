package com.subdual.research_service.extraction.ai;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.extractor.CommonEvidenceExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AiEvidenceEnricher {

    private static final List<String> DEFAULT_TARGET_FIELDS = List.of(
            "role", "organization", "description", "summary", "headquarters", "technologies"
    );

    private final AiExtractionClient aiExtractionClient;
    private final EvidenceMerger evidenceMerger;

    public AiEvidenceEnricher(AiExtractionClient aiExtractionClient, EvidenceMerger evidenceMerger) {
        this.aiExtractionClient = aiExtractionClient != null ? aiExtractionClient : new NoOpAiExtractionClient();
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public void enrichWithAiExtraction(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        if (!canEnrich(documents)) {
            return;
        }

        List<String> targetFields = resolveTargetFields(target);
        for (ExtractedDocument doc : documents) {
            if (CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)) {
                processDocumentAiFacts(target, doc, targetFields, attributes);
            }
        }
    }

    private boolean canEnrich(List<ExtractedDocument> documents) {
        return aiExtractionClient != null && documents != null && !documents.isEmpty();
    }

    private List<String> resolveTargetFields(ResearchTarget target) {
        if (target != null && target.targetFields() != null && !target.targetFields().isEmpty()) {
            return target.targetFields();
        }
        return DEFAULT_TARGET_FIELDS;
    }

    private void processDocumentAiFacts(
            ResearchTarget target,
            ExtractedDocument doc,
            List<String> targetFields,
            Map<String, EvidenceTuple> attributes
    ) {
        String entityType = target != null && target.entityType() != null ? target.entityType().name() : "OTHER";
        String displayName = target != null ? target.displayName() : "";

        Map<String, AiExtractedFact> facts = aiExtractionClient.extractFacts(
                displayName,
                entityType,
                doc.url(),
                doc.cleanText(),
                targetFields
        );

        mergeAiFacts(facts, doc.url(), doc.cleanText(), attributes);
    }

    public void mergeAiFacts(Map<String, AiExtractedFact> facts, String sourceUrl, String docText, Map<String, EvidenceTuple> attributes) {
        if (facts == null) {
            return;
        }

        facts.forEach((factKey, fact) -> {
            if (fact != null && fact.value() != null && !fact.value().isBlank()) {
                if (!isFactGroundedInSource(fact, docText)) {
                    return;
                }
                ConfidenceTier tier = resolveAiConfidenceTier(fact.confidenceScore());
                String snippet = resolveAiSnippet(fact.exactQuote());
                evidenceMerger.mergeAttribute(attributes, factKey, fact.value(), sourceUrl, snippet, tier);
            }
        });
    }

    public boolean isFactGroundedInSource(AiExtractedFact fact, String docText) {
        if (fact == null || docText == null || docText.isBlank()) {
            return false;
        }
        String lowerDoc = docText.toLowerCase(Locale.ROOT);
        if (fact.exactQuote() != null && !fact.exactQuote().isBlank()) {
            String quote = fact.exactQuote().trim().toLowerCase(Locale.ROOT);
            if (lowerDoc.contains(quote)) {
                return true;
            }
        }
        if (fact.value() == null) {
            return false;
        }
        String val = fact.value().trim().toLowerCase(Locale.ROOT);
        return lowerDoc.contains(val);
    }

    public ConfidenceTier resolveAiConfidenceTier(double score) {
        if (score >= 0.8) return ConfidenceTier.HIGH;
        if (score >= 0.5) return ConfidenceTier.MEDIUM;
        return ConfidenceTier.LOW;
    }

    public String resolveAiSnippet(String exactQuote) {
        return (exactQuote != null && !exactQuote.isBlank())
                ? "AI Quote: \"" + exactQuote + "\""
                : "AI Structured Extraction";
    }
}
