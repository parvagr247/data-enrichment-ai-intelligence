package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.util.UrlNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplicates discovered sources based on normalized URL without losing query context.
 */
public final class SourceDeduplicator {

    private SourceDeduplicator() {}

    public record DeduplicationResult(
            List<DiscoveredSource> deduplicated,
            int duplicatesRemovedCount
    ) {}

    public static DeduplicationResult deduplicate(List<DiscoveredSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return new DeduplicationResult(List.of(), 0);
        }

        Map<String, DiscoveredSource> uniqueMap = new LinkedHashMap<>();
        int duplicates = 0;

        for (DiscoveredSource src : sources) {
            if (src == null || src.url() == null || src.url().isBlank()) {
                continue;
            }

            String norm = UrlNormalizer.normalize(src.url());
            if (uniqueMap.containsKey(norm)) {
                duplicates++;
                DiscoveredSource existing = uniqueMap.get(norm);
                if (src.relevance() != null && (existing.relevance() == null || src.relevance() > existing.relevance())) {
                    uniqueMap.put(norm, src);
                }
            } else {
                uniqueMap.put(norm, src);
            }
        }

        return new DeduplicationResult(new ArrayList<>(uniqueMap.values()), duplicates);
    }
}
