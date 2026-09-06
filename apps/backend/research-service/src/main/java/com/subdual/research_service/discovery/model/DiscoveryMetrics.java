package com.subdual.research_service.discovery.model;

/**
 * Operational metrics capturing source discovery execution performance and quality (Task 60).
 */
public record DiscoveryMetrics(
        int queryCount,
        int resultCount,
        int duplicatesRemoved,
        int sourcesSelected,
        int sourcesSkipped,
        String providerUsed,
        long durationMs
) {
    public static DiscoveryMetrics empty(String provider) {
        return new DiscoveryMetrics(0, 0, 0, 0, 0, provider != null ? provider : "unknown", 0L);
    }
}
