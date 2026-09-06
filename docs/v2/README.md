# V2 Blueprint: Data Enrichment Platform

> [!WARNING]
> **PROPOSED ARCHITECTURE / NOT IMPLEMENTED YET**  
> This document and the surrounding `docs/v2/` suite represent the planned architectural blueprint for V2. The current codebase remains strictly frozen at `v1.0.0`. No V2 code should be written until this blueprint is reviewed and approved.

---

## 1. Executive Summary & Core V2 Question

### The Core Question
> *"What must change to evolve the V1 prototype into a production-oriented, scalable data enrichment platform?"*

V1 proved the end-to-end viability of evidence-grounded enrichment: spreadsheet ingestion, requirement interpretation, web discovery, deterministic extraction, LLM synthesis with verbatim quotes, and MySQL persistence.

However, V1 is fundamentally a **synchronous-heavy pipeline with memory-bound batching**. To evolve into a reliable, enterprise-grade platform, V2 must shift from a "prototype script wrapped in services" to a **declarative, job-orchestrated enrichment engine** with persistent job state, fine-grained evidence provenance, clear data model decoupling, resilient provider fault tolerance, and comprehensive execution observability.

### Evolution Classification Matrix

| Category | Components / Areas | Strategic Justification |
| :--- | :--- | :--- |
| **A. Keep As-Is** | • 3-microservice topology (`dataset-service`, `research-service`, `ai-intelligent-service`)<br/>• Zero-hallucination principle (verbatim evidence quotes required for all facts)<br/>• Non-destructive spreadsheet export (original data preserved)<br/>• MySQL relational persistence & Flyway schema management<br/>• Next.js 15 client foundation & 5-stage progressive UI workflow | The foundational architecture, core guarantees, and UI paradigms are proven and correct. Do not rewrite or replace working systems. |
| **B. Refactor** | • In-memory `JobState` map in `dataset-service` $\rightarrow$ DB-backed job lifecycle<br/>• Monolithic `RowEnrichmentResult` $\rightarrow$ Decoupled data model (`InputRecord` $\rightarrow$ `Entity` $\rightarrow$ `Evidence` $\rightarrow$ `EnrichedRecord`)<br/>• Hardcoded search queries in `QueryBuilder` $\rightarrow$ Declarative, requirement-weighted query strategies<br/>• Scattered logging $\rightarrow$ Structured MDC tracing (`correlationId`, `jobId`, `rowId`) | Eliminates memory leaks, isolates concerns, and enables crash-resilient job recovery. |
| **C. Extend** | • User requirements $\rightarrow$ Reusable enrichment profiles & schema validation rules<br/>• Source discovery $\rightarrow$ Domain-specific scrapers & rate-limited provider adapters<br/>• Actuator endpoints $\rightarrow$ Granular Prometheus metrics (latency, token costs, failure rates)<br/>• Entity resolution $\rightarrow$ Cross-row duplicate detection and alias resolution | Enhances capability without breaking existing V1 contracts. |
| **D. Replace** | • Ephemeral unbounded Java threads $\rightarrow$ Persistent, bounded queue with backpressure & cancellation<br/>• Ad-hoc string comparisons for entity resolution $\rightarrow$ Levenshtein & token-overlap similarity scoring | Fixes operational fragility under production load. |
| **E. New Capability** | • Job pause, resume, and cancellation lifecycle<br/>• Per-field confidence thresholds with automatic fallback queries<br/>• Webhook notifications upon batch job completion<br/>• Interactive evidence inspection with DOM snippet visualizers | Transforms the prototype into a production-ready data platform. |

---

## 2. V2 Product Vision

> **"V2 transforms the V1 enrichment pipeline into a configurable, reliable, and observable data enrichment platform where users can ingest arbitrary datasets, declare structured or natural-language enrichment requirements, execute durable batch jobs with row-level isolation, audit verified evidence quotes with full source provenance, and export high-integrity structured intelligence."**

---

## 3. Comprehensive V1 Limitations Analysis

### Product Limitations
- **No Job Re-execution or Resume**: If a 100-row batch fails at row 85 (e.g., server restart), the entire job must be re-run from scratch; there is no resume capability.
- **Ephemeral Job History**: Batch job states are stored in an in-memory `ConcurrentHashMap`; restarting `dataset-service` wipes all active and historical job statuses.
- **Single-Tenant Assumptions**: No tenant isolation, user profiles, or project workspaces.

### Data Quality Limitations
- **Shallow Disambiguation for Common Names**: Entities with generic names (e.g., "John Smith") lacking organizational context yield low-confidence or empty results without guided user disambiguation.
- **Attribute Freshness Ignorance**: The engine extracts information regardless of publication date, occasionally picking historical data over current facts (e.g., a past job rather than current role).
- **No Cross-Row Deduplication**: If an uploaded spreadsheet contains the same company or person three times, the engine researches them independently three times, wasting bandwidth and LLM tokens.

### AI & Spring AI Limitations
- **Token Inefficiency**: Entire scraped HTML/text bodies (up to 50KB) are occasionally passed into Gemini, driving up token consumption and latency.
- **Model Lock-in Vulnerability**: Prompt templates are tightly coupled to Gemini's markdown and JSON nuances; switching to local models (e.g., Ollama/Llama 3) requires manual prompt adjustments.
- **Lack of Cost/Usage Metering**: No tracking of prompt tokens, completion tokens, or estimated API costs per job or row.

### Research & Scraping Limitations
- **Search Provider Quota Brittleness**: Tavily API rate limits (HTTP 429) immediately degrade discovery to mock sources unless manual intervention occurs.
- **JavaScript SPA Inaccessibility**: Scraper relies on standard HTTP fetching; client-rendered JavaScript single-page apps (SPAs) return empty or skeleton HTML.
- **Robots.txt & WAF Blocks**: Cloudflare and Akamai bot protections on certain target domains cause silent fetch failures.

### API Limitations
- **Coarse Error Responses**: Upstream research failures report general strings rather than standardized RFC 7807 problem details with typed error codes.
- **Missing Pagination on Row Results**: Batch jobs with 1,000+ rows return all row results in a single giant JSON response payload.
- **No Webhook / Asynchronous Notification**: Clients must continuously poll `GET /api/v1/enrichment/jobs/{jobId}` every few seconds.

### Architecture & Reliability Limitations
- **Memory Pressure under Large Batches**: Parsing and holding 5,000 rows in memory simultaneously can exhaust JVM heap space.
- **Thread Pool Exhaustion**: A single long-running batch job can saturate the single/bounded thread pool, starving concurrent single-record enrichment requests.
- **Coupled Service Timeouts**: Synchronous chains (`dataset-service` $\rightarrow$ `research-service` $\rightarrow$ `ai-service`) risk cascade timeouts if scraping hangs.

### Observability & Security Limitations
- **Uncorrelated Logs**: Log statements lack unified MDC tracing across service boundaries, making distributed debugging across 3 services tedious.
- **Unauthenticated Internal Endpoints**: All microservice ports (9741, 9742, 9743) are completely unauthenticated.
- **Prompt Injection Surface**: Untrusted external web content is fed into LLM prompts without explicit safety sanitization against adversarial instructions embedded in scraped pages.

---

## 4. V2 Boundary

### V2 In Scope
1. **Durable Job Orchestration**: Database-backed job states (`PENDING`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`), persistent row results, and resumable execution.
2. **Decoupled Data Architecture**: Clear domain boundaries separating `InputRecord`, `NormalizedRecord`, `EntityIdentity`, `EvidenceDocument`, `AttributeAssertion`, and `EnrichedRow`.
3. **Configurable Enrichment Profiles**: System and user-defined profiles specifying required fields, validation regexes, and acceptable confidence thresholds.
4. **Enhanced Evidence Provenance**: Capturing crawl timestamp, HTTP response status, source domain authority rating, exact verbatim quote, and DOM snippet locator.
5. **Deterministic Pre-Filtering**: Token-aware chunking and heuristic pre-extraction before invoking Spring AI, cutting LLM token usage by $\approx 50\%$.
6. **Unified MDC Distributed Tracing**: `X-Correlation-ID`, `X-Job-ID`, and `X-Row-ID` propagated across all HTTP REST client calls and log events.
7. **Production Fault Tolerance & Retries**: Circuit breaking, exponential backoff for search/LLM providers, and bounded thread pools with backpressure.
8. **Next.js UI Refinements**: Job pause/resume controls, pagination for large datasets, and enriched field confidence filter.

### V2 Out of Scope
1. Multi-tenant SaaS billing, Stripe subscriptions, and metered user credits.
2. Complex distributed event brokers (Kafka, RabbitMQ, Pulsar) — relational queueing and bounded executors remain sufficient.
3. Kubernetes Helm charts and service meshes (Istio/Linkerd).
4. Autonomous multi-turn recursive web browsing agents (e.g. headless Chromium farms executing arbitrary JS clicks).
5. Vector database infrastructure (Pinecone, Weaviate, Milvus) — keyword and lexical matching provide superior grounding for entity facts.
6. Public user registration, OAuth2 social login, and fine-grained RBAC.

---

## 5. V2 Anti-Goals: What NOT to Build

* **DO NOT introduce Kafka or RabbitMQ**: The platform processes batches of 10 to 5,000 rows. A durable relational task table (`enrichment_job_tasks`) in MySQL with `SELECT FOR UPDATE SKIP LOCKED` or a bounded Spring thread pool provides complete crash durability without operational overhead.
* **DO NOT introduce Kubernetes or Service Meshes**: Docker Compose remains the production and development orchestration standard for V2.
* **DO NOT rewrite the Frontend in a different framework**: Next.js 15 with Tailwind and React hooks works cleanly; preserve and refine it.
* **DO NOT add Vector Databases**: Vector embeddings hallucinate false semantic proximity for exact entity facts (e.g., confusing two people at the same company). Lexical anchoring and exact substring quotes are mathematically superior for factual verification.
* **DO NOT create parallel V2 services or duplicate APIs**: No `dataset-service-v2` or `/api/v2/*` duplicates. APIs evolve through backward-compatible field extensions.

---

## 6. Future Learning Topics
Based on the transition from V1 prototype to V2 platform, the following engineering topics are slated for future documentation in `docs/learning/`:
1. **Chapter 15: Durable Relational Task Queues without Message Brokers**: Implementing crash-resilient worker pools in Spring Boot using MySQL row locking.
2. **Chapter 16: Cost-Aware LLM Engineering**: Token budgeting, prompt caching, and heuristic pre-filtering to minimize inference expenditure.
3. **Chapter 17: Adversarial Prompt Injection Defense in Web Scraping Pipelines**: Neutralizing malicious text instructions embedded within third-party HTML.
4. **Chapter 18: Evidence Provenance & Audit Graphs**: Modeling immutable citation chains from HTTP response to database cell.
