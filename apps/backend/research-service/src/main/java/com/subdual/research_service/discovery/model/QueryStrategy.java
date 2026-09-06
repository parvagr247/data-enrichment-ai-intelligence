package com.subdual.research_service.discovery.model;

/**
 * Controlled strategy for generating targeted search queries (Task 53).
 */
public enum QueryStrategy {
    EXACT_NAME_AND_ORG,
    NAME_AND_FIELD,
    CANONICAL_DOMAIN,
    NAME_AND_INTENT,
    GENERAL_DISCOVERY
}
