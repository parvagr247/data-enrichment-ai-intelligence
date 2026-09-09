package com.subdual.research_service.research.model;

/**
 * Represents the inherent authority and trustworthiness of a source.
 * Distinct from relevance (e.g. high relevance on a personal blog != high reliability).
 */
public enum SourceReliability {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
