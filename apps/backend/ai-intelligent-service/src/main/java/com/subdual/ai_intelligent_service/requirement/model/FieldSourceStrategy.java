package com.subdual.ai_intelligent_service.requirement.model;

/**
 * Strategy for how an enrichment field will be fulfilled in the execution plan (Task 38).
 */
public enum FieldSourceStrategy {
    EXISTING_DATASET_FIELD, // Field already present in input dataset; no discovery needed
    RESEARCH_LOOKUP,        // Field discoverable via deterministic web research
    AI_SYNTHESIS,           // Field requires evidence-grounded AI reasoning/extraction
    LOCAL_DERIVATION        // Field derivable locally (e.g. domain from URL)
}
