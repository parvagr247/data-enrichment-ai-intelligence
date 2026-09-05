# Concept 13: Modular Evidence Extraction, Domain Extractor Decomposition & Entity Resolution

In data enrichment engines, the extraction layer is where raw, noisy HTML pages turn into structured, verified facts. Without deliberate architecture, this layer rapidly degenerates into an unmaintainable "God Class"—a 1,500-line monolith filled with dozens of regular expressions, DOM traversal logic, domain-specific heuristics (people, companies, repositories, products), and string merging rules.

Furthermore, searching the open web introduces **Identity Drift**: searching for a person or company frequently returns pages for a namesake, an alumni directory with 50 unrelated names, or a competitor. Extracting facts from these false matches pollutes the dataset with invalid data.

This guide explains how this platform decomposed its extraction architecture into modular domain subpackages and implemented **Entity Resolution** to eliminate identity collisions.

---

## Why This Exists

During the evolution of `research-service`:
1. The original `EvidenceExtractor` grew to encompass people patterns, company patterns, product patterns, GitHub repository parsing, HTML DOM extraction, and evidence merging all in a single file. Tuning a person's role regex risked breaking repository license extraction.
2. Search queries on common names (e.g. searching for a software engineer) returned alumni pages from universities listing hundreds of graduates. The extractor erroneously attributed other graduates' employers and degrees to the target entity.
3. To solve this, we refactored extraction into clean, single-responsibility subpackages and introduced an explicit `EntityResolver` verification step.

---

## The Problem

A naive implementation typically suffers from:
* **The Monolithic Extractor Anti-Pattern**: Putting extraction logic for all entity types (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`, `WEBSITE`) in one massive class. Adding a new entity type requires modifying existing code, violating the Open/Closed Principle.
* **Identity Drift & False Positives**: Trusting that every search result returned by Google or Tavily actually belongs to the target entity. In reality, search engines return candidate URLs that contain only partial name matches, directory listings, or unrelated entities.
* **Unstructured Text Noise**: Feeding raw web pages containing navigation menus, cookie notices, footer links, and sidebar recommendations directly into fact extractors. Extracted facts frequently capture website boilerplate rather than true entity attributes.

---

## The Core Idea

1. **Modular Subpackage Decomposition**:
   * `extraction.document`: Pure document parsing and noise stripping via Jsoup ([`ContentExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/document/ContentExtractor.java) $\rightarrow$ [`ExtractedDocument`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/document/ExtractedDocument.java)).
   * `extraction.extractor`: Focused, domain-specific extractors ([`PersonEvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/extractor/PersonEvidenceExtractor.java), [`OrganizationEvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/extractor/OrganizationEvidenceExtractor.java), [`ProductEvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/extractor/ProductEvidenceExtractor.java), [`RepositoryEvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/extractor/RepositoryEvidenceExtractor.java), [`CommonEvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/extractor/CommonEvidenceExtractor.java)).
   * `extraction.support`: Common infrastructure for entity resolution ([`EntityResolver`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/support/EntityResolver.java)), attribute merging ([`EvidenceMerger`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/support/EvidenceMerger.java)), and target field normalization ([`TargetFieldNormalizer`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/support/TargetFieldNormalizer.java)).
   * `extraction.ai`: LLM-assisted evidence enrichment ([`AiEvidenceEnricher`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/ai/AiEvidenceEnricher.java)).
   * `extraction`: High-level facade orchestrators ([`EvidenceExtractor`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/EvidenceExtractor.java), [`DefaultSourceEvidenceService`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/DefaultSourceEvidenceService.java)).
2. **Pre-Extraction Entity Resolution**: Before any extractor parses a document, `EntityResolver` compares the document's URL, title, and body against the target's identity anchors. If the document has low match confidence or exhibits conflicting identity signals, it is rejected before extraction can occur.

---

## How This Project Uses It

```
apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/
├── DefaultSourceEvidenceService.java       # Coordinates scraping, resolution & extraction
├── EvidenceExtractor.java                  # Facade orchestrating extractors
├── document/
│   ├── ContentExtractor.java               # Jsoup HTML parsing & noise tag stripping
│   └── ExtractedDocument.java              # Immutable representation of cleaned document
├── extractor/
│   ├── CommonEvidenceExtractor.java        # Title, meta description, site name
│   ├── PersonEvidenceExtractor.java        # Role, company, location, education, skills
│   ├── OrganizationEvidenceExtractor.java  # Industry, headquarters, employee count
│   ├── ProductEvidenceExtractor.java       # Vendor, categories, release date
│   └── RepositoryEvidenceExtractor.java    # GitHub stars, languages, topics, licenses
└── support/
    ├── EntityResolver.java                 # Identity verification & false-positive filtering
    ├── EvidenceMerger.java                 # Corroboration boosting & conflict resolution
    └── TargetFieldNormalizer.java          # Canonical attribute mapping
```

### 1. Document Noise Stripping in [`ContentExtractor.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/document/ContentExtractor.java#L58-L67)

Before any regex or AI extractor runs, `ContentExtractor` aggressively strips non-content DOM elements using Jsoup:

```java
private void stripNoiseTags(Document doc) {
    doc.select("script, style, nav, header, footer, noscript, svg, form, aside, " +
            "[role='navigation'], [role='banner'], [role='contentinfo'], " +
            "[class*='cookie'], [id*='cookie'], [class*='consent'], [id*='consent'], " +
            "[class*='advertisement'], [id*='advertisement'], [class*='ads'], [id*='ads'], " +
            "[class*='sidebar'], [id*='sidebar'], [class*='people-also-viewed'], " +
            "[class*='related-profiles'], [id*='related-profiles'], [class*='recommended']").remove();
}
```

This guarantees that navigation text ("Home", "About Us", "Sign Up") and cookie disclaimers are removed, preventing false-positive matches on company names or roles.

### 2. Entity Resolution & Drift Prevention in [`EntityResolver.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/support/EntityResolver.java#L23-L65)

`EntityResolver` evaluates whether a candidate document actually represents the target:

```java
public ResolutionResult resolve(ResearchTarget target, ExtractedDocument doc) {
    if (target == null || doc == null) {
        return new ResolutionResult(ConfidenceTier.UNKNOWN, "Missing target or document", false);
    }

    // 1. Exact canonical URL or domain host match -> HIGH confidence
    if (isExactHostMatch(target.canonicalUrl(), doc.url())) {
        return new ResolutionResult(ConfidenceTier.HIGH, "Direct canonical domain match", true);
    }

    // 2. Target display name matching in title or body
    String name = target.displayName();
    if (name != null && !name.isBlank()) {
        boolean titleMatch = containsIgnoreCase(doc.title(), name);
        boolean bodyMatch = containsIgnoreCase(doc.cleanText(), name);

        if (titleMatch) {
            return new ResolutionResult(ConfidenceTier.HIGH, "Title contains target name", true);
        }
        if (bodyMatch) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, "Body contains target name", true);
        }
    }

    return new ResolutionResult(ConfidenceTier.LOW, "Weak identity match", false);
}
```

In [`DefaultSourceEvidenceService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/DefaultSourceEvidenceService.java), any document with a resolution result marked as `matched = false` is excluded from the domain extractors.

### 3. Domain Extractor Delegation in [`EvidenceExtractor.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/EvidenceExtractor.java#L65-L84)

The top-level facade delegates strictly to the appropriate domain extractor based on `EntityType`:

```java
public Map<String, EvidenceTuple> extractAttributes(
        ResearchTarget target,
        List<ExtractedDocument> documents,
        Map<String, EntityResolver.ResolutionResult> resolutions
) {
    Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

    // 1. Common web metadata (title, meta description, site name)
    commonEvidenceExtractor.extractCommonAttributes(target, documents, attributes, resolutions);

    // 2. Domain-specific extraction
    switch (target.type()) {
        case PERSON -> personEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
        case ORGANIZATION -> organizationEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
        case PRODUCT -> productEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
        case REPOSITORY -> repositoryEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
        case WEBSITE, OTHER -> { /* common attributes sufficient */ }
    }

    // 3. Optional AI-grounded enrichment for missing target fields
    aiEvidenceEnricher.enrichWithAiIfConfigured(target, documents, attributes, resolutions);

    return attributes;
}
```

---

## Flow

```
                Fetched Raw HTML Document
                            │
                            ▼
                  [ContentExtractor]
           Strip noise tags: nav, footer, ads
           Extract title, meta-desc, cleanText
                            │
                            ▼
                    ExtractedDocument
                            │
                            ▼
                    [EntityResolver]
             Compare against target identity:
             - URL host match?
             - Target name in title/body?
             - Conflicting signals detected?
                            │
               Matched? ────┴──── Unmatched / Mismatched?
                  │                           │
                 Yes                          No
                  ▼                           ▼
        Filter to Domain Extractor      Reject Document
      (e.g. PersonEvidenceExtractor)    (Log rejection note;
                  │                      do not extract facts)
                  ▼
     Extract Attributes with Snippets
                  │
                  ▼
          [EvidenceMerger]
   Does an existing source report this?
         /                     \
    Agreement               Conflict
       /                         \
      ▼                           ▼
Boost Confidence:           Keep competing value;
LOW -> MEDIUM -> HIGH       set conflictDetected = true
```

---

## Important Design Decisions

1. **Subpackage Modularization Over Monolithic Growth**:
   By separating `document`, `extractor`, and `support`, each package has an unambiguous scope. Testing `PersonEvidenceExtractor` only requires mocking `EvidenceMerger`; it has zero coupling to `RepositoryEvidenceExtractor` or `ContentExtractor`.
2. **Identity Verification as a Gatekeeper**:
   Allowing extractors to run on unmatched search results causes severe data corruption. The `EntityResolver` acts as an invariant gatekeeper: if the document fails identity resolution, domain extractors never inspect its text.
3. **Evidence Tuple Immutability**:
   `EvidenceTuple` records the extracted value, source URL, verbatim sentence quote, and confidence tier. By capturing the exact quote at extraction time, downstream AI synthesizers and human reviewers can independently verify the fact's provenance.

---

## Alternatives

| Alternative | Why We Did Not Choose It |
| :--- | :--- |
| **Pure AI Extraction (No Regexes)** | Submitting raw HTML directly to an LLM is 50x slower, costs tokens on every request, and frequently hallucinates formatting. Deterministic extractors handle 80% of standard metadata instantly at zero cost. |
| **Monolithic Single Class** | Tempting during initial prototyping, but quickly becomes unmaintainable when supporting multiple domain types (`PERSON`, `ORGANIZATION`, `REPOSITORY`). |
| **Fuzzy Semantic Similarity Only** | Using embedding cosine similarity to match entities sounds modern, but is computationally expensive and frequently confuses namesakes who work in the same industry. Exact host and substring matching provides faster, deterministic verification. |

---

## Common Mistakes

1. **Hardcoding AI Extraction Ahead of Rule Extractors**:
   Calling an LLM before checking if standard metadata (e.g. OpenGraph tags, `meta[name="description"]`, GitHub JSON APIs) already answered the question wastes money and introduces latency. Always run deterministic extractors first.
2. **Allowing Identity Collisions on Common Names**:
   If a search query for "Alex Smith" returns a LinkedIn profile for an architect and a Twitter profile for a doctor, failing to check company or role signals merges two completely different people into one corrupted record.
3. **Overwriting Existing Attributes Without Corroboration**:
   Simply writing `attributes.put(key, newValue)` whenever a new source is parsed discards earlier findings. Always route through `EvidenceMerger` to boost confidence upon agreement or flag conflicts upon disagreement.

---

## Production Considerations

* **Domain-Specific Parsing Rules**: In production, major platforms (LinkedIn, GitHub, Crunchbase) have anti-bot protections and specialized markup. Adding provider-specific adapters (e.g. GitHub REST API instead of web scraping) yields higher accuracy.
* **Regular Expression Safety**: Unanchored regexes with nested repetition operators can cause catastrophic backtracking (ReDoS) when run against multi-megabyte web pages. Ensure all regexes in extractors are bounded, anchored, and tested with worst-case input strings.
* **Configurable Confidence Thresholds**: Different consumers have different tolerances for uncertainty. An automated marketing campaign might accept `MEDIUM` confidence, whereas a credit underwriting pipeline might demand `HIGH` confidence with multi-source corroboration.

---

## What I Should Learn From This

1. **Decompose complex multi-domain processing pipelines into modular domain subpackages.**
2. **Strip noise and boilerplate from web documents before running extraction logic.**
3. **Always verify that a candidate document actually represents your target entity before extracting facts.**
4. **Treat evidence and provenance as first-class citizens: store source URLs, verbatim quotes, and confidence tiers alongside every extracted attribute.**

---

**Previous:** [Concept 12: User-Directed Requirements vs. Default Enrichment & Adaptive Scoping](12-user-directed-requirements-and-adaptive-enrichment.md) | **Next:** [Concept 14: Observability, MDC Correlation Tracing & ProblemDetail Diagnostics](14-observability-mdc-tracing-and-diagnostics.md)
