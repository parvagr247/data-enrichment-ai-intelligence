# Architecture Decision Records & System Evolution

This document records the foundational **Architecture Decision Records (ADRs)**, the evolution of the platform from a synchronous prototype to an observable concurrent engine, and the strategic roadmap.

---

## 1. Architecture Decision Records (ADRs)

### ADR 01: Multi-Microservice Boundary Decomposition
* **Status**: Accepted & Active
* **Context**: The platform performs distinct classes of operations: web research/crawling, LLM prompt orchestration, relational persistence, and API gateway routing.
* **Decision**: Decompose the backend into focused Spring Boot microservices:
  1. `api-gateway` (:8080 / :9738) — Bounded context: Single ingress, reverse proxy, JWT verification, anti-spoofing header injection, security headers.
  2. `auth-service` (:9739) — Bounded context: User credentials, BCrypt hashing, JWT issuance.
  3. `research-service` (:9741) — Bounded context: Autonomous web discovery, scraping, SSRF validation, boilerplate cleaning, and multi-source evidence corroboration.
  4. `ai-intelligent-service` (:9742) — Bounded context: Spring AI interactions, Google Gemini prompt execution, requirement planning, and structured fact extraction with verbatim quote grounding.
  5. `dataset-service` (:9743) — Bounded context: Dataset ingestion, delimiter sniffing, profiling, batch job orchestration, bounded worker pools, SSE streaming, and MySQL relational persistence.
  6. `config-server` (:8888 / :9736) & `discovery-server` (:8761 / :9737) — Core Spring Cloud configuration and Eureka service discovery.
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
* **Decision**: In `ai-intelligent-service`, wrap the Spring AI invocation in a transparent fallback to `DeterministicProfileAssessmentEngine` and heuristic extraction:
  ```java
  try {
      facts = extractViaSpringAi(request);
  } catch (Exception ex) {
      log.warn("Spring AI extraction failed ({}), falling back to deterministic extraction", ex.getMessage());
      facts = extractDeterministically(request);
  }
  ```
* **Consequences**:
  - *Positive*: Upstream research pipelines never crash due to external AI outages.
  - *Positive*: Enables completely offline local development and automated CI testing without requiring live API keys.
  - *Negative*: Deterministic regex/heuristic fallback produces shallower attributes than full LLM reasoning.

---

### ADR 04: Bounded Concurrency over Unbounded Thread Spawning
* **Status**: Accepted & Active
* **Context**: Processing multi-row datasets sequentially is too slow, but spawning unbounded threads causes thread exhaustion, CPU starvation, and provider rate-limiting (HTTP 429).
* **Decision**: Use a managed, configurable `ThreadPoolExecutor` (`BoundedExecutorService`) with bounded queue and caller-runs / rejection policies. Expose dynamic pool capacity via API (`concurrency: 3`).
* **Consequences**:
  - *Positive*: Controlled parallel throughput without overloading external search APIs or the database.
  - *Positive*: Predictable memory and thread footprints.

---

### ADR 05: Server-Sent Events (SSE) for Real-Time Execution Observability
* **Status**: Accepted & Active
* **Context**: Sequential progress indicators (`"Processing 0 of 4 records..."`) leave users blind to parallel worker activities, stage transitions (`RESEARCH` &rarr; `AI_EXTRACTION` &rarr; `PERSISTENCE`), and completed row results.
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

### ADR 07: AI Error Classification, Bounded Timeouts, and Gemini Flash
* **Status**: Accepted & Active
* **Context**: Deprecated LLM models returned HTTP 404, and transient/rate-limit errors caused repeated retries that hung the research pipeline.
* **Decision**:
  1. Standardize on Google Gemini Flash models (`gemini-2.5-flash` / `gemini-3.5-flash-lite`).
  2. Implement `AiErrorClassifier` to categorize errors into `AUTHENTICATION_FAILURE`, `UNSUPPORTED_MODEL`, `RATE_LIMIT`, `TIMEOUT`, `NETWORK_FAILURE`, `SERVER_ERROR`, `STRUCTURED_PARSING_FAILURE`.
  3. Enforce safe credential redacting in diagnostic logs (`[REDACTED_API_KEY]`).
  4. Enforce 15-second bounded execution timeouts on LLM calls with immediate deterministic extraction fallback on permanent failures or daily quota exhaustion.
* **Consequences**:
  - *Positive*: Zero unhandled AI exceptions leaking upstream; research pipeline never blocks on external provider outages.
  - *Positive*: Structured observability via logging without credential leakage.

---

### ADR 08: Idempotent URL Normalization and Evidence Quality Tiering
* **Status**: Accepted & Active
* **Context**: Markdown-wrapped URLs (e.g. `[https://...](https://...)`) polluted canonical entity IDs, and search provider snippet fallbacks were conflated with direct verified crawl evidence.
* **Decision**:
  1. Enforce strict link unwrapping (`unwrapLink`) and scheme normalization across all service boundaries, guaranteeing idempotency (`normalize(normalize(x)) == normalize(x)`).
  2. Define `EvidenceQuality` tiers (`DIRECT_SOURCE`, `SEARCH_SNIPPET`, `OTHER_PROVIDER_RESULT`, `DERIVED_INFERRED`).
  3. Search snippets used as fallback when direct crawling is blocked (e.g. anti-bot HTTP 999) are capped at `MEDIUM` confidence and yield to `DIRECT_SOURCE` in conflict resolution.
  4. Anchor common names in discovery queries with known organization, canonical profile URL slugs, or roles.
* **Consequences**:
  - *Positive*: Clean entity deduplication and canonical URLs free of markdown artifacts.
  - *Positive*: Transparent fact provenance distinguishing directly crawled pages from search engine snippets.

---

### ADR 09: Strict Segregation of Public Ingress DTOs and Internal Inter-Service DTOs
* **Status**: Accepted & Active
* **Context**: Microservices previously shared DTO models between public HTTP controllers, internal `RestClient` clients, and database persistence layers. Changes in internal communication schemas risked breaking external frontends or leaking internal system fields (such as token counts or raw HTML snippets).
* **Decision**:
  1. Enforce strict segregation across all microservices:
     - **Public Ingress DTOs** reside exclusively in `api.dto.request` and `api.dto.response`.
     - **Internal Inter-Service DTOs** reside in `integration.<service>.dto` (or `integration.client.dto`).
     - **Persistence Entities** reside exclusively in feature-specific entity packages.
  2. Map explicitly between internal and public representations in service coordinators or dedicated mapper/helper components.
* **Consequences**:
  - *Positive*: Public API contracts remain stable and backwards-compatible regardless of internal mesh evolution.
  - *Positive*: Sensitive internal execution fields are never inadvertently exposed to the web client.
  - *Negative*: Minimal boilerplate for mapping between internal integration models and public response models.

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
      Spring AI Integration : Gemini Flash fact extraction
    section Phase 3: Dataset Ingestion
      CSV/XLSX Upload : Client-side SheetJS parsing
      Schema Profiling : Type detection & column mapping
      User Requirements : Natural language enrichment prompts
    section Phase 4: Observability & Concurrency
      Bounded Concurrency : Managed worker pool (3 workers)
      Server-Sent Events : Granular row stage streaming
      Live Execution UI : Active worker cards & timeline
      Objective Scoring : Multi-dimensional relevance scoring
    section Phase 5: Architecture Hardening
      API Gateway & Auth : JWT verification & anti-spoofing
      DTO Segregation : Public API vs Internal Inter-Service DTOs
      Refactored Services : Feature-centric packaging in all microservices
```

---

## 3. Strategic Future Roadmap

The following architectural initiatives are planned for future development phases:

1. **Persistent Distributed Job Store**:
   - Back `JobState` lifecycle directly in MySQL with a distributed state machine (allowing paused/resumed jobs across server restarts).
2. **Cross-Row Deduplication & Cache Sharing**:
   - Shared research cache keyed by canonical SHA-256 entity IDs to prevent redundant scraping and LLM token spend.
3. **Headless Browser Sidecar**:
   - Playwright/Puppeteer sidecar container for JavaScript-heavy Single Page Applications (SPAs).
4. **Webhook Event Dispatching**:
   - Configurable outbound webhooks notifying external customer endpoints upon batch job completion.
