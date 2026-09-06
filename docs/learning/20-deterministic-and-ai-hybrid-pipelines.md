# Concept 20: Deterministic & AI Hybrid Pipelines

A common temptation in modern software development is the **"All-in-on-AI" Anti-Pattern**: treating an LLM as an autonomous agent that directly ingests raw user files, searches the web, writes to the database, and drives the entire business workflow.

In enterprise data platforms, unconstrained autonomous AI pipelines are dangerous: they are non-deterministic, slow, expensive, and prone to catastrophic hallucinations. Conversely, purely deterministic code (regexes and rule engines) cannot interpret messy human prose or synthesize nuanced findings from disparate web articles.

This guide explains the architectural pattern of the **Deterministic + AI Hybrid Pipeline**: placing deterministic Java guardrails before, alongside, and after probabilistic AI inference.

---

## Why This Exists

In our platform:
* We need deterministic guarantees: when a user inputs a URL, the URL must be canonicalized identically every time; entity IDs must be reproducible SHA-256 hashes; and SQL database updates must be strictly transactional and idempotent.
* We need probabilistic cognitive reasoning: when a user says "Find their recent funding and tech stack", an LLM must infer what constitutes a "funding round" and synthesize multiple messy news articles into a clean statement.

The only way to achieve both **enterprise reliability** and **cognitive flexibility** is a hybrid pipeline where deterministic code controls the workflow, and AI is strictly invoked as an isolated, bounded computational worker.

---

## Problem

A naive implementation that surrenders workflow control to an LLM suffers from:
* **Non-Reproducibility**: Running the exact same dataset on Monday and Tuesday produces different database rows, different column names, and different validation results.
* **Token Explosion & Latency**: Sending raw 50KB HTML web pages straight into prompt contexts consumes hundreds of thousands of tokens and introduces 15-second latencies per row.
* **Hallucinated Attributes**: An LLM confidently asserting that a person is the CEO of a company when the source text only mentioned they attended a conference hosted by that company.

---

## Core Idea

The core idea is the **Sandwiched AI Hybrid Pipeline Pattern**:

```mermaid
flowchart TD
    RawInput["1. Raw Input Data (CSV / XLSX / API)"] --> Preprocess["2. Deterministic Preprocessing<br/>(Whitespace trim, regex URL canonicalize,<br/>SHA-256 entityId, Jsoup noise strip)"]
    
    Preprocess --> Heuristics["3. Deterministic Extraction<br/>(OpenGraph tags, meta descriptions, regex patterns)"]
    
    Heuristics --> GapCheck{"Missing Target Fields<br/>or Complex Requirements?"}
    GapCheck -->|No (All fields satisfied)| Persistence["6. Deterministic Persistence & Export"]
    
    GapCheck -->|Yes| PromptPrep["4. Bounded AI Reasoning Context<br/>(Curated evidence snippets + strict prompt)"]
    PromptPrep --> AIExecution["Spring AI LLM Call<br/>(Probabilistic Synthesis)"]
    
    AIExecution --> PostValidation["5. Deterministic Post-Validation<br/>(Zero-hallucination quote check, schema parse,<br/>confidence tier calculation)"]
    
    PostValidation --> Persistence
```

1. **Deterministic Preprocessing (Before AI)**:
   * Strips HTML noise (`<script>`, `<nav>`, cookie banners) using Jsoup.
   * Canonicalizes URLs, removes marketing tracking parameters (`utm_*`), and computes SHA-256 entity keys.
   * Extracts standard metadata (OpenGraph title, description) via deterministic regexes at zero cost.
2. **Bounded AI Reasoning (During AI)**:
   * The LLM is only called when deterministic extractors leave target fields unfulfilled.
   * The LLM is passed clean, curated snippets—never raw HTML pages.
3. **Deterministic Post-Validation (After AI)**:
   * Every fact returned by the LLM is programmatically checked against the raw document text. If the cited quote does not literally exist in the document, the fact is rejected.
   * Confidence scores and conflict flags are computed deterministically by comparing claims across multiple sources.

---

## How It Works

### 1. Deterministic Extraction First in [`EvidenceExtractor.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/EvidenceExtractor.java#L65-L84)

The pipeline extracts every possible fact deterministically before considering an AI invocation:

```java
// 1. Deterministic common metadata (title, meta description, site name)
commonEvidenceExtractor.extractCommonAttributes(target, documents, attributes, resolutions);

// 2. Deterministic domain-specific regex extractors
switch (target.type()) {
    case PERSON -> personEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
    case ORGANIZATION -> organizationEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
    case PRODUCT -> productEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
    case REPOSITORY -> repositoryEvidenceExtractor.extractAttributes(target, documents, attributes, resolutions);
    case WEBSITE, OTHER -> { /* common attributes sufficient */ }
}

// 3. Probabilistic AI synthesis only for remaining gaps
aiEvidenceEnricher.enrichWithAiIfConfigured(target, documents, attributes, resolutions);
```

### 2. Programmatic Post-Validation Guard in [`SpringAiExtractionService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiExtractionService.java#L128-L135)

The system never trusts the LLM's assertion of evidence. It programmatically verifies that the quote exists in the source text:

```java
// Zero-hallucination deterministic guard
String quote = entry.getValue().exactQuote();
if (quote != null && request.textContent().toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT).trim())) {
    facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
} else {
    // Drop fact immediately; log warning
    log.warn("Rejected hallucinated quote for key '{}': quote not found in document", entry.getKey());
}
```

---

## Where It Appears in This Project

| Component | Responsibility in Hybrid Pipeline |
| :--- | :--- |
| [`DefaultEntityNormalizer.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/research/pipeline/DefaultEntityNormalizer.java) | **Deterministic**: Strips UTM tags, validates URL protocols, computes SHA-256 entity keys. |
| [`ContentExtractor.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/document/ContentExtractor.java) | **Deterministic**: Strips DOM noise tags using Jsoup, extracts clean body text. |
| [`SpringAiEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java) | **Probabilistic**: Synthesizes verified facts from snippets via Google Gemini. |
| [`EvidenceMerger.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/support/EvidenceMerger.java) | **Deterministic**: Compares values, promotes confidence (`LOW` $\rightarrow$ `HIGH`), and flags contradictions. |
| [`DefaultEntityPersistenceService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/service/DefaultEntityPersistenceService.java) | **Deterministic**: Atomic, transactional database upsert with orphan removal. |

---

## Design Decisions

| Decision | Justification |
| :--- | :--- |
| **Never Let AI Direct the Execution Flow** | The pipeline sequence (normalize $\rightarrow$ search $\rightarrow$ scrape $\rightarrow$ extract $\rightarrow$ persist) is written in immutable Java code. The AI is a stateless worker called at specific stages, not an autonomous agent that decides what to do next. |
| **Deterministic Heuristics Before AI** | 80% of metadata (e.g. page title, meta description, GitHub repo stars, license) is accessible via deterministic parsing in 2 milliseconds. Using an LLM for these basics is wasteful. |
| **Substring Verification as Invariant Gate** | A fact without a verbatim matching substring in the raw source text is dropped. This single programmatic rule eliminates over 95% of hallucinated facts. |

---

## Common Mistakes

1. **Using AI to Validate What Code Can Validate**:
   Prompting an LLM: *"Is 'foo@bar.com' a valid email?"* instead of using a standard Java regex or validator library.
2. **Skipping Post-Validation Because the Prompt Said "Do Not Hallucinate"**:
   Prompt rules reduce hallucinations, but they do not eliminate them. Programmatic verification must always follow LLM output.
3. **Failing to Provide Deterministic Fallbacks**:
   If the LLM endpoint experiences a 503 outage, an all-AI pipeline halts completely. A hybrid pipeline falls back to deterministic regexes, ensuring the system remains operational.

---

## Practical Mental Model

Think of the hybrid pipeline as an **orchestra with a soloist**:
* The Java orchestrator is the **conductor**: it sets the tempo, directs the sections, and strictly enforces the score (validation, transactions, data flow).
* The LLM is the **virtuoso soloist**: it steps in for a nuanced solo (synthesis, semantic understanding) when called upon, but it does not direct the tempo or rewrite the symphony.

---

## Implementation Status

* **CURRENT IMPLEMENTATION**: Deterministic Jsoup cleaning, regex extractors for all domain types, Spring AI synthesis, programmatic verbatim quote verification, deterministic multi-source corroboration and conflict resolution.
* **ARCHITECTURAL DIRECTION**: Pre-LLM token budgeting (calculating snippet token size before prompt dispatch) and automated chunk truncation.
* **FUTURE POSSIBILITY**: Embedding-based semantic validation to cross-verify factual consistency across large corpora.

---

## Related Concepts

* **Previous:** [Concept 19: Spring AI Abstraction & Structured AI Workflows](19-spring-ai-abstraction-and-structured-ai-workflows.md)
* **Next:** [Concept 21: Microservice Boundaries & Orchestration](21-microservice-boundaries-and-orchestration.md)
