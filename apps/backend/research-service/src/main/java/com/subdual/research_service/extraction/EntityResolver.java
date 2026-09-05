package com.subdual.research_service.extraction;

import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class EntityResolver {

    private static final Logger log = LoggerFactory.getLogger(EntityResolver.class);

    public record ResolutionResult(ConfidenceTier confidence, boolean matched, String reason) {}

    public ResolutionResult resolve(ResearchTarget target, ExtractedDocument document) {
        if (target == null || document == null || document.url() == null) {
            return new ResolutionResult(ConfidenceTier.UNKNOWN, false, "Missing target or document");
        }

        // Rule 1: Canonical domain/host match
        try {
            URI targetUri = URI.create(target.canonicalUrl());
            URI docUri = URI.create(document.url());
            if (targetUri.getHost() != null && docUri.getHost() != null
                    && targetUri.getHost().equalsIgnoreCase(docUri.getHost())) {
                return new ResolutionResult(ConfidenceTier.HIGH, true, "Host match with target canonical URL");
            }
        } catch (Exception ignored) {}

        String name = target.displayName();
        if (name == null || name.isBlank() || name.equalsIgnoreCase(target.canonicalUrl())) {
            // No distinct name provided: check if host domain or path aligns with canonical target
            try {
                URI targetUri = URI.create(target.canonicalUrl());
                URI docUri = URI.create(document.url());
                String targetHost = targetUri.getHost() != null ? targetUri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
                String docHost = docUri.getHost() != null ? docUri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";

                if (!targetHost.isBlank() && (docHost.endsWith("." + targetHost) || targetHost.endsWith("." + docHost) || docHost.equalsIgnoreCase(targetHost))) {
                    return new ResolutionResult(ConfidenceTier.MEDIUM, true, "Domain-aligned discovery match");
                }
            } catch (Exception ignored) {}

            return new ResolutionResult(ConfidenceTier.LOW, false, "No host or entity name correlation found");
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        // Rule 2: Exact entity name match in document title
        if (docTitle.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.HIGH, true, "Entity name matched in document title");
        }

        // Rule 3: Entity name mentioned in page body
        if (docText.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, true, "Entity name mentioned in document body");
        }

        // Fallback: Discovered link did not contain entity name
        return new ResolutionResult(ConfidenceTier.LOW, false, "Entity name not found in document content");
    }
}
