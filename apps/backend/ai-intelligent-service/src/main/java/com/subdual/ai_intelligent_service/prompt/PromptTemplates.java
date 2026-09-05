package com.subdual.ai_intelligent_service.prompt;

public final class PromptTemplates {

    private PromptTemplates() {}

    public static final String REQUIREMENT_INTERPRETATION_PROMPT = """
            You are a requirement analyst for a generic entity data enrichment engine.
            The user wants to enrich entities of type: %s.
            
            USER REQUIREMENT:
            \"%s\"
            
            TASK:
            1. Analyze the user's natural language requirement.
            2. Extract all distinct requested attribute fields.
            3. Convert each field name into a concise, standard camelCase identifier (e.g., currentOrganization, currentRole, education, skills, location, funding, products, employeeCount).
            4. Provide a brief 1-sentence summary of the requested scope.
            
            CRITICAL RULES:
            - ONLY extract fields explicitly or clearly requested by the user.
            - DO NOT invent unnecessary extra fields.
            
            Return strictly valid JSON in the format:
            {
              "requestedFields": ["field1", "field2", ...],
              "scopeDescription": "Summary of what user requested"
            }
            """;

    public static final String INPUT_CLEANSING_PROMPT = """
            You are an input cleansing specialist for an entity enrichment system.
            Given the following raw input fields for an entity of type: %s:
            
            RAW INPUT:
            %s
            
            TASK:
            1. Clean and normalize entity identifiers (names, titles, organizations, URLs).
            2. Remove accidental noise, whitespace, quotation marks, or artifacts.
            3. Keep the original input values intact under their original keys, while offering normalized values for canonical keys (name, organization, role, url).
            
            Return strictly valid JSON in the format:
            {
              "cleanedInput": { ... },
              "normalizedFields": { "name": "...", "organization": "...", "role": "...", "url": "..." },
              "notes": ["note 1", ...]
            }
            """;

    public static final String ENRICHMENT_SYNTHESIS_PROMPT = """
            You are a rigorous, evidence-grounded entity enrichment synthesizer.
            Synthesize verified enriched attributes for entity '%s' (type: %s, url: %s).
            
            ORIGINAL RAW INPUT (from uploaded dataset):
            %s
            
            USER REQUIREMENT:
            %s
            
            TARGET FIELDS TO ENRICH:
            %s
            
            RESEARCH EVIDENCE COLLECTED FROM WEB SOURCES:
            %s
            
            CRITICAL ANTI-HALLUCINATION RULES:
            1. DO NOT INVENT INFORMATION.
            2. Ground EVERY claimed attribute strictly in the provided research evidence or raw input.
            3. If an evidence snippet supports a fact, quote or cite it verbatim.
            4. If no evidence supports a target field, DO NOT FABRICATE A VALUE. Add that field to "unresolvedFields".
            5. If multiple sources contradict each other on a field (e.g. conflicting current employers or roles), set status to "CONFLICT" and note both values.
            6. For confidence:
               - HIGH: Fact verified by primary official sources or multiple corroborating sources.
               - MEDIUM: Fact asserted in a single credible web source with explicit sentence quote.
               - LOW: Weak or ambiguous reference.
               - UNKNOWN: No evidence found.
            
            Return strictly valid JSON in the format:
            {
              "displayName": "%s",
              "entityType": "%s",
              "canonicalUrl": "%s",
              "attributes": {
                "fieldName": {
                  "field": "fieldName",
                  "value": "Extracted Fact",
                  "originalValue": "Original raw value if mapped, else null",
                  "confidence": "HIGH|MEDIUM|LOW|UNKNOWN",
                  "status": "VERIFIED|INFERRED|CONFLICT|UNRESOLVED",
                  "sources": ["url1", "url2"],
                  "evidence": "Verbatim quote or evidence excerpt",
                  "notes": "Reasoning or normalization note"
                }
              },
              "unresolvedFields": ["fieldX", "fieldY"],
              "conflicts": ["Description of conflict if any"],
              "overallConfidence": 0.90
            }
            """;
}
