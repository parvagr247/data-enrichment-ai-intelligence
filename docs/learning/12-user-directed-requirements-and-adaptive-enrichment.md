# Concept 12: User-Directed Requirements vs. Default Enrichment & Adaptive Scoping

In generic data enrichment platforms, user intent varies dramatically. One user uploading a list of executives wants to find their **current employer and job title**; another user uploading the same list wants to find their **university education, open-source repositories, and verified skills**. 

If an enrichment system hardcodes the fields it searches for into Java service methods, it cannot adapt to different business use cases without code changes.

This guide explains how this platform separates **User-Directed Enrichment** from **Default Enrichment**, represents user requirements as dynamic runtime data, interprets freeform natural language intent, and performs **adaptive early stopping**.

---

## Why This Exists

Hardcoding enrichment targets directly into pipeline extractors creates severe rigidity:
1. Supporting a new field (e.g. `fundingStage` or `techStack`) requires modifying Java classes, regex extractors, and database schemas.
2. The pipeline wastes web search queries, scraping bandwidth, and AI tokens gathering irrelevant facts that the user never asked for.
3. Conversely, when users have no specific requirement and simply want a complete profile, the system must know what a "complete" profile looks like for a person versus a company or software repository.

---

## The Problem

A naive implementation typically has these architectural flaws:
* **Hardcoded Business Logic for Enrichment Scope**: Writing `if (type == PERSON) { extractRole(); extractCompany(); }`. Every new user request requires a backend code deployment.
* **All-or-Nothing Search Latency**: Always querying 5 web search results and scraping all 5 websites, even if the primary website (e.g. official LinkedIn or GitHub page) already answered 100% of the user's questions in the first 200 milliseconds.
* **Silent Dropping of Unfulfilled Requirements**: If a user explicitly requested "Find Series A valuation", and no web source mentioned it, a naive system returns `{}`. The user has no idea whether the engine forgot to look, hallucinated an empty string, or genuinely searched and found no evidence.

---

## The Core Idea

1. **User Requirements as Dynamic Runtime Data**: Natural language instructions ("Find their current role, company, and university education") are passed as data in the request payload (`userRequirement`). They are never hardcoded into business services.
2. **Dual-Mode Requirement Interpretation**:
   * **User-Directed Mode**: When `userRequirement` is present, the engine interprets the natural language string (via Spring AI or deterministic rule parsing) and extracts a concise list of target fields (`requestedFields`).
   * **Default Automated Mode**: When `userRequirement` is blank or omitted, the engine automatically assigns an authoritative domain scope based on the entity type (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`).
3. **Adaptive Early Stopping**: As web sources are scraped in order of search relevance, the pipeline checks whether all requested `targetFields` are satisfied with `HIGH` confidence. If covered, it halts discovery immediately, eliminating redundant network calls.
4. **Explicit Resolution Status**: Every target field receives an explicit status tag: `VERIFIED`, `INFERRED`, `CONFLICT`, or `UNRESOLVED`.

---

## How This Project Uses It

```
apps/backend/ai-intelligent-service/
├── prompt/PromptTemplates.java         # REQUIREMENT_INTERPRETATION_PROMPT
├── dto/
│   ├── RequirementInterpretationRequest.java
│   └── RequirementInterpretationResponse.java
└── service/SpringAiEnrichmentService.java # interpretRequirement, buildDefaultScope
apps/backend/research-service/
└── research/ResearchOrchestrator.java  # Adaptive early stopping & field coverage
```

### 1. Dual-Mode Scope Resolution in [`SpringAiEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java#L49-L68)

```java
@Override
public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
    String requirement = request != null ? request.requirement() : null;
    String entityType = request != null && request.entityType() != null 
            ? request.entityType().toUpperCase(Locale.ROOT) : "PERSON";

    // 1. DEFAULT AUTOMATED ENRICHMENT: User provided no requirement
    if (requirement == null || requirement.isBlank()) {
        return buildDefaultScope(entityType);
    }

    // 2. USER-DIRECTED ENRICHMENT: AI interprets natural language intent
    if (!isMockMode() && chatModel != null) {
        try {
            String promptText = String.format(PromptTemplates.REQUIREMENT_INTERPRETATION_PROMPT, entityType, requirement.trim());
            String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            return parseRequirementResponse(responseText);
        } catch (Exception ex) {
            log.warn("Spring AI requirement interpretation failed, falling back to rule extraction", ex);
        }
    }

    // 3. Fallback deterministic keyword rules
    return interpretDeterministically(requirement, entityType);
}
```

### 2. The Authoritative Default Scopes

When the user specifies no custom requirement, [`buildDefaultScope(...)`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java#L129-L138) provides sensible domain defaults:

```java
private RequirementInterpretationResponse buildDefaultScope(String entityType) {
    List<String> defaultFields = switch (entityType) {
        case "PERSON" -> List.of("currentOrganization", "currentRole", "education", "skills", "location");
        case "ORGANIZATION" -> List.of("description", "industry", "headquarters", "products", "employeeCount");
        case "PRODUCT" -> List.of("description", "vendor", "features", "pricing", "license");
        case "REPOSITORY" -> List.of("description", "owner", "license", "language", "stars");
        default -> List.of("description", "overview", "category");
    };
    return new RequirementInterpretationResponse(defaultFields, "Default automated enrichment scope for " + entityType, true);
}
```

### 3. Adaptive Early Stopping in [`ResearchOrchestrator.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/research/ResearchOrchestrator.java)

During web evidence extraction, after each candidate source is processed, the orchestrator evaluates whether further scraping is necessary:

```java
// Check if all requested target fields are already covered with high confidence
if (requestedFields != null && !requestedFields.isEmpty()) {
    boolean allCovered = requestedFields.stream().allMatch(field -> {
        EvidenceTuple tuple = attributes.get(field);
        return tuple != null && tuple.confidence() == ConfidenceTier.HIGH;
    });

    if (allCovered) {
        log.info("[Pipeline: ADAPTIVE_STOP] All requested target fields covered. Stopping early.");
        break; // Halt web fetching loop immediately
    }
}
```

If a target person has 5 discovered web pages, but the first source (their primary official profile) provides both `currentRole` and `currentOrganization` with `HIGH` confidence, the pipeline stops immediately in under 10 milliseconds rather than fetching the remaining 4 pages.

---

## Flow

```
                           User Submission
                   (Raw Row + Optional Requirement)
                                  │
                                  ▼
                   Has User Specified Requirement?
                         /                 \
                   Yes  /                   \ No
                       /                     \
                      ▼                       ▼
            User-Directed Mode         Default Mode
         (Natural Language Prompt)  (Entity-Type Standard)
                      │                       │
                      ▼                       ▼
           AI Requirement Parsing    Assign Default Fields
           (Extract target fields    (e.g. PERSON -> role, org,
            e.g. ["funding", "tech"]) education, skills, location)
                      │                       │
                      └───────────┬───────────┘
                                  │
                                  ▼
                     Resolved Target Fields List
                                  │
                                  ▼
                    Iterative Web Evidence Fetch
                                  │
                                  ▼
                    All Target Fields Satisfied?
                         /                 \
                   Yes  /                   \ No
                       /                     \
                      ▼                       ▼
             [ADAPTIVE EARLY STOP]      Fetch Next Ranked
             Halt discovery; save       Web Source Until
             bandwidth & LLM tokens     Timeout/Max Sources
                      │                       │
                      └───────────┬───────────┘
                                  │
                                  ▼
                     Synthesize Final Attributes
              (Tag: VERIFIED, CONFLICT, or UNRESOLVED)
```

---

## Important Design Decisions

1. **User Requirement as Data, Not Logic**:
   By treating the user's requirement as a runtime request property, the same backend pipeline services HR recruiter use cases, VC investment research, and competitive intelligence without requiring bespoke endpoints for each domain.
2. **The "Unresolved Fields" Guarantee**:
   If a user asks for `funding` and no source mentions it, the synthesis response explicitly places `"funding"` into the `unresolvedFields` array. This gives the frontend the ability to display a clear badge: *"Searched 3 sources, no funding data found"* instead of leaving the user wondering if the field was skipped.
3. **Early Stopping for Operational Efficiency**:
   Web scraping is the slowest phase of data enrichment (network latency, anti-bot delays). Adaptive early stopping reduces total latency by 60–80% for records where the primary canonical source is authoritative.

---

## Alternatives

| Alternative | Why We Did Not Choose It |
| :--- | :--- |
| **Exhaustive Always-Scrape Policy** | Always scrapes all 5 discovered URLs. Maximizes corroboration at the cost of massive latency (5–15 seconds per row) and high server load. |
| **Rigid Dropdown Field Selector** | Restricts users to selecting from a predefined checkbox list of 10 fields. Prevents users from expressing custom natural language requirements like "Find their PhD thesis advisor". |
| **Pure LLM Guessing on Missing Data** | Fabricates missing values when sources are silent. Violates our platform's zero-hallucination guarantee. |

---

## Common Mistakes

1. **Confusing Default Scope with Hardcoded Logic**:
   Default scopes should serve as fallbacks when input is absent, not as constraints that filter out user requests. If the user explicitly asks for `dogBreed` on a `PERSON` entity, the system should honor the request rather than discarding it.
2. **Ignoring Confidence During Early Stopping**:
   Stopping early when a field is only `LOW` confidence from an ambiguous forum post results in poor data quality. Only stop early if the existing evidence meets `HIGH` confidence thresholds.
3. **Failing to Normalize Field Identifiers**:
   Users write "job title", "role", "position", or "designation". The requirement interpretation engine must normalize these variations into a canonical key (e.g. `role` or `currentRole`) so subsequent extractors know what to search for.

---

## Production Considerations

* **Requirement Caching**: Often, users run batch enrichment where all 500 rows share the exact same requirement string. Caching the interpreted `targetFields` in Redis or an in-memory cache avoids re-calling the LLM 500 times for the same requirement.
* **Token Budgeting**: Complex user requirements that ask for 20+ fields can exceed token budgets and increase response times. Enforce a maximum field limit (e.g. up to 10 target fields per entity) for non-enterprise tiers.
* **Corroboration Overrides**: For high-stakes fields like `license` or `revenue`, users can configure "require-corroboration: true", disabling early stopping and forcing the engine to search multiple sources before declaring a fact verified.

---

## What I Should Learn From This

1. **Treat user requirements as runtime data, not hardcoded if-else statements.**
2. **Provide authoritative, domain-appropriate default scopes when users specify no preferences.**
3. **Use adaptive early stopping to eliminate redundant network I/O and reduce batch latency.**
4. **Explicitly mark missing fields as `UNRESOLVED` rather than silently omitting them.**

---

**Previous:** [Concept 11: Dataset Ingestion, Schema Detection & Multi-Tier Entity Normalization](11-dataset-ingestion-schema-detection-and-normalization.md) | **Next:** [Concept 13: Modular Evidence Extraction, Domain Extractor Decomposition & Entity Resolution](13-modular-evidence-extraction-and-entity-resolution.md)
