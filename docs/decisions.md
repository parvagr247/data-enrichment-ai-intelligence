# Architecture Decisions & System Evolution

This document records the foundational **Architecture Decision Records (ADRs)**, the evolution of the platform from a synchronous prototype to an observable concurrent engine, and the strategic roadmap.

---

## 1. Architecture Decision Records (ADRs)

### ADR 01: Three-Microservice Boundary Decomposition
* **Status**: Accepted & Active
* **Context**: The platform performs three distinct classes of operations: web research/crawling, LLM prompt orchestration, and ACID relational persistence.
* **Decision**: Decompose the backend into three Spring Boot microservices:
  1. `research-service` (:9741) — Bounded context: Autonomous web discovery, scraping, boilerplate cleaning, and multi-source evidence corroboration.
  2. `ai-intelligent-service` (:9742) — Bounded context: Spring AI interactions, Google Gemini prompt execution, and structured fact extraction.
  3. `dataset-service` (:9743) — Bounded context: Dataset ingestion, batch job orchestration, state machine tracking, and MySQL relational persistence.
* **Consequences**:
  - *Positive*: Independent failure domains. High-latency LLM calls or rate-limited web searches cannot block database transactions or frontend polling.
  - *Positive*: Independent scaling and resource allocation (e.g. higher memory/CPU for scraping, network bandwidth for AI).
  - *Negative*: Cross-service HTTP serialization overhead and distributed tracing requirements.

---

### ADR 02: Verbatim Evidence Grounding (Zero-Hallucination Principle)
* **Status**: Accepted & Active
* **Context**: Generative LLMs are prone to plausible hallucinations when asked to synthesize entity facts from raw text.
* **Decision**: All factual attributes extracted by `ai-intelligent-service` must include an `exactQuote` snippet alongside the extracted value. The service executes an automated programmatic verification guard:
  ```java
  if (request.textContent().toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT).trim())) {
      facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
  }
  ```
  If the exact quote cannot be found verbatim within the source text, the fact is unconditionally rejected.
* **Consequences**:
  - *Positive*: Guaranteed factual accuracy. Users and downstream systems can verify every claim against an exact quote.
  - *Negative*: Paraphrased or inferred truths that lack exact text matches are discarded (`UNKNOWN` status).

---

### ADR 03: Deterministic Fallback Engine for AI Extraction
* **Status**: Accepted & Active
* **Context**: External AI providers (e.g. Google Gemini, OpenAI) may experience rate limits (HTTP 429), quota exhaustion, transient network errors, or invalid JSON output.
* **Decision**: In `ai-intelligent-service`, wrap the Spring AI invocation in a transparent fallback to `DeterministicProfileAssessmentEngine`:
  ```java
  try {
      facts = extractViaSpringAi(request);
  } catch (Exception ex) {
      log.warn("Spring AI extraction failed ({}), falling back to deterministic extraction", ex.getMessage());
      facts = extractDeterministically(request);
  }
  ```
* **Consequences**:
  - *Positive*: The upstream research pipeline never crashes due to external AI outages.
  - *Positive*: Enables completely offline local development and automated CI testing without requiring live API keys.
  - *Negative*: Deterministic regex/heuristic fallback produces shallower attributes than full LLM reasoning.

---

### ADR 04: Bounded Concurrency over Unbounded Thread Spawning
* **Status**: Accepted & Active
* **Context**: Processing multi-row datasets sequentially is too slow (e.g. 4 rows taking 2+ minutes), but spawning unbounded threads (`new Thread()` or `CompletableFuture.runAsync()` with default pool) causes thread exhaustion, CPU starvation, and provider rate-limiting (HTTP 429).
* **Decision**: Use a managed, configurable `ThreadPoolExecutor` (`enrichmentTaskExecutor`) with bounded queue and caller-runs / rejection policies. Expose dynamic pool capacity via API (`concurrency: 3`).
* **Consequences**:
  - *Positive*: Controlled parallel throughput without overloading external search APIs or the database.
  - *Positive*: Predictable memory and thread footprints.

---

### ADR 05: Server-Sent Events (SSE) for Real-Time Execution Observability
* **Status**: Accepted & Active
* **Context**: Sequential progress indicators (`"Processing 0 of 4 records..."`) leave users blind to parallel worker activities, stage transitions (`RESEARCH` -> `AI_EXTRACTION` -> `PERSISTENCE`), and completed row results.
* **Decision**: Implement unidirectional Server-Sent Events (`GET /api/v1/enrichment/jobs/{jobId}/events`) using Spring `SseEmitter` with an in-memory bounded replay buffer (latest 500 events) and client-side `EventSource` with polling fallback.
* **Consequences**:
  - *Positive*: Real-time visualization of individual worker threads and stages without the connection overhead of full-duplex WebSockets.
  - *Positive*: Immediate row inspection via modals before the entire batch completes.
  - *Positive*: Reconnecting clients receive historic events to reconstruct in-flight state.

---

### ADR 06: Relational MySQL Persistence with Flyway Migrations
* **Status**: Accepted & Active
* **Context**: Enriched entities have structured relationships (sources, attributes, corroborations) that need ACID guarantees and structured querying.
* **Decision**: Use MySQL 8.0+ managed by versioned Flyway migrations in `dataset-service`, with JPA `orphanRemoval = true` for idempotent full-snapshot upserts.
* **Consequences**:
  - *Positive*: Deterministic schema evolution across development, Docker, and staging.
  - *Positive*: Idempotent re-enrichment of existing entities without duplicate rows or orphan foreign keys.

---

### ADR 07: AI Error Classification, Bounded Timeouts, and Gemini 3.5 Flash Lite
* **Status**: Accepted & Active
* **Context**: Older Gemini models (e.g. `gemini-2.0-flash`) were retired/deprecated by Google GenAI (returning HTTP 404), and transient/rate-limit errors caused repeated retries that hung the research pipeline.
* **Decision**:
  1. Transition default production model to active `gemini-3.5-flash-lite`.
  2. Implement `AiErrorClassifier` to categorize errors into `AUTHENTICATION_FAILURE`, `UNSUPPORTED_MODEL`, `RATE_LIMIT`, `TIMEOUT`, `NETWORK_FAILURE`, `SERVER_ERROR`, `STRUCTURED_PARSING_FAILURE`.
  3. Enforce safe credential redacting in diagnostic logs (`[REDACTED_API_KEY]`).
  4. Enforce 15-second bounded execution timeouts on LLM calls with immediate deterministic extraction fallback on permanent failures or daily quota exhaustion.
* **Consequences**:
  - *Positive*: Zero unhandled AI exceptions leaking upstream; research pipeline never blocks on external provider outages.
  - *Positive*: Structured observability via `[AI_EXTRACTION]` logging without credential leakage.

---

### ADR 08: Idempotent URL Normalization and Evidence Quality Tiering
* **Status**: Accepted & Active
* **Context**: Markdown-wrapped URLs (e.g. `[https://...](https://...)`) polluted canonical entity IDs, and search provider snippet fallbacks were conflated with direct verified crawl evidence.
* **Decision**:
  1. Enforce strict link unwrapping (`unwrapLink`) and scheme normalization across all service boundaries (`ResearchRequest`, `ResearchRequestValidator`, `DefaultEntityNormalizer`, `DefaultDatasetEnrichmentService`), guaranteeing idempotency (`normalize(normalize(x)) == normalize(x)`).
  2. Define `EvidenceQuality` tiers (`DIRECT_SOURCE`, `SEARCH_SNIPPET`, `OTHER_PROVIDER_RESULT`, `DERIVED_INFERRED`).
  3. Search snippets used as fallback when direct crawling is blocked (e.g. anti-bot HTTP 999) are capped at `MEDIUM` confidence and yield to `DIRECT_SOURCE` in conflict resolution.
  4. Anchor common names in discovery queries with known organization, canonical profile URL slugs, or roles.
* **Consequences**:
  - *Positive*: Clean entity deduplication and canonical URLs free of markdown artifacts.
  - *Positive*: Transparent fact provenance distinguishing directly crawled pages from search engine snippets.

---

## 2. System Evolution History

```mermaid
timeline
    title Platform Architecture Evolution
    section Phase 1: Prototype
      Single-Entity Pipeline : Synchronous /api/v1/research
      In-Memory State : Ephemeral research jobs
      Mock Search Provider : Local offline heuristics
    section Phase 2: Microservices
      3-Service Topology : research, ai-intelligent, dataset
      MySQL Persistence : Flyway migrations & entity tables
      Spring AI Integration : Gemini 1.5/2.0 Flash extraction
    section Phase 3: Dataset Ingestion
      CSV/XLSX Upload : Client-side SheetJS parsing
      Schema Profiling : Type detection & column mapping
      User Requirements : Natural language enrichment prompts
    section Phase 4: Observability & Concurrency
      Bounded Concurrency : Managed worker pool (3 workers)
      Server-Sent Events : Granular row stage streaming
      Live Execution UI : Active worker cards & timeline
      Objective Scoring : Multi-dimensional relevance scoring
```

---

## 3. Strategic Future Roadmap

The following architectural initiatives are planned for future development phases:

1. **Persistent Distributed Job Store**:
   - *Current*: `DefaultDatasetEnrichmentService` tracks active batch jobs in memory.
   - *Target*: Back `EnrichmentJob` lifecycle directly in MySQL with a distributed state machine (allowing paused/resumed jobs across server restarts).

2. **Cross-Row Deduplication & Cache Sharing**:
   - *Current*: Identical companies or people appearing in multiple rows are researched independently.
   - *Target*: Shared research cache keyed by canonical SHA-256 entity IDs to prevent redundant scraping and LLM token spend.

3. **Domain-Specific Scrapers & Headless Browser Support**:
   - *Current*: Standard HTTP fetching with boilerplate striping.
   - *Target*: Playwright/Puppeteer sidecar container for JavaScript-heavy Single Page Applications (SPAs).

4. **Webhook Event Dispatching**:
   - *Current*: UI receives progress via SSE or polling.
   - *Target*: Configurable outbound webhooks notifying external systems upon batch job completion.
