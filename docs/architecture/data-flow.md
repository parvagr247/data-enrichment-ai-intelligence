# End-to-End Data & Execution Flow

This document details the complete lifecycle of tabular datasets through the **Data Enrichment AI Intelligence Platform**, tracing how raw spreadsheet inputs are transformed into verified, evidence-grounded attributes under bounded concurrent execution and real-time observability.

---

## 1. End-to-End Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Browser
    participant UI as Next.js UI (:3000)
    participant GW as api-gateway (:9738)
    participant DS as dataset-service (:9743)
    participant RS as research-service (:9741)
    participant Search as Web Search (Tavily/Mock)
    participant Web as Target Web Pages
    participant AI as ai-intelligent-service (:9742)
    participant DB as MySQL DB (:3306)

    Note over User,UI: 1. Ingestion & Schema Profiling
    User->>UI: Upload CSV / XLSX file
    UI->>UI: Parse tabular rows in-browser (SheetJS) & detect column types (Name, URL, Org, Role)
    User->>UI: Confirm column mappings & enter natural language requirement

    Note over UI,GW: 2. Batch Job Submission & Ingress
    UI->>GW: POST /api/v1/enrichment/jobs<br/>(Bearer JWT, rows, column mappings, requirement)
    GW->>GW: Validate JWT, strip spoofed headers, inject verified X-User-Id
    GW->>DS: Forward to dataset-service:9743
    DS-->>GW: 202 Accepted (jobId, totalRows, concurrency=3)
    GW-->>UI: 202 Accepted (jobId)

    Note over UI,DS: 3. Real-Time Observability Subscription
    UI->>GW: GET /api/v1/enrichment/jobs/{jobId}/events (SSE Connection)
    GW->>DS: Forward SSE Subscription
    DS-->>GW: SSE Stream (init event + replay buffer)
    GW-->>UI: SSE Stream established

    Note over DS,AI: 4. Input Cleansing
    DS->>AI: POST /api/v1/ai/clean (normalize raw seeds)
    AI-->>DS: Cleaned names, normalized URLs

    Note over DS,DB: 5. Bounded Concurrent Execution (Worker Pool = 3 Threads)
    par Worker-1 (Row 0), Worker-2 (Row 1), Worker-3 (Row 2)
        DS->>UI: Event: STARTED (workerId, rowIndex, entityName)
        
        Note over DS,RS: Stage: RESEARCH
        DS->>UI: Event: STAGE_TRANSITION (RESEARCH)
        DS->>RS: POST /api/v1/research (url, name, requirement)
        RS->>AI: POST /api/v1/ai/requirement (parse target fields)
        AI-->>RS: Structured target fields & keywords
        RS->>Search: Query primary & secondary sources
        Search-->>RS: Candidate URLs & snippets
        loop Authoritative Sources
            RS->>Web: Fetch HTML & clean boilerplate
            Web-->>RS: Raw text
            RS->>AI: POST /api/v1/ai/enrich (grounded extraction)
            AI-->>RS: Facts with exact verbatim quotes
        end
        RS->>RS: Corroborate sources & resolve conflicts
        RS-->>DS: ResearchResponse (attributes, sources, confidence)

        Note over DS,AI: Stage: AI_EXTRACTION (Profile Assessment)
        DS->>UI: Event: STAGE_TRANSITION (AI_EXTRACTION)
        DS->>AI: POST /api/v2/ai/profile/assess (objective evaluation)
        AI-->>DS: Multi-dimensional score & summary

        Note over DS,DB: Stage: PERSISTENCE
        DS->>UI: Event: STAGE_TRANSITION (PERSISTENCE)
        DS->>DB: Upsert entity, sources, attributes (Flyway/JPA)
        DB-->>DS: Confirmed persistence

        DS->>UI: Event: ROW_COMPLETED (metadata: result with sources & attributes)
        Note over UI: Row immediately available for Evidence Modal Inspection!
    end

    Note over DS,UI: 6. Terminal Completion
    DS->>UI: Event: JOB_COMPLETED (jobId, status=COMPLETED)
    User->>UI: Inspect full dataset or Export CSV / XLSX
```

---

## 2. Stage-by-Stage Operational Breakdown

### Stage 1: Tabular Dataset Ingestion & Profiling
* **Client-Side Parsing**: Next.js parses CSV or Excel (`.xlsx`) files directly in the browser via SheetJS (`xlsx`). The server never handles raw file uploads or ephemeral file storage.
* **Automatic Schema Detection**: Analyzes column headers and sample row values using regex heuristics to classify columns into `NAME`, `URL`, `ORGANIZATION`, `ROLE`, and `LOCATION`.
* **Requirement Input**: Users select prompt suggestions (e.g. *"Extract current role, organization, key skills, and recent projects"*) or type custom instructions.

### Stage 2: Batch Job Submission & Ingress
* **Endpoint**: `POST /api/v1/enrichment/jobs`
* **Security**: Mediated by `api-gateway`. External clients supply an `Authorization: Bearer <jwt>` header. The gateway cryptographically validates the token and injects a verified `X-User-Id` downstream.
* **Job Registration**: `dataset-service` registers an in-memory job tracker, assigns a UUID `jobId`, creates an in-memory event buffer, and submits tasks to `enrichmentTaskExecutor`. Returns `202 Accepted`.

### Stage 3: Real-Time Observability via Server-Sent Events (SSE)
* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}/events`
* **Event Taxonomy**:
  * `init`: Handshake event transmitting `jobId`, `totalRows`, and active concurrency (`3`).
  * `execution-event`: Emitted on every state transition, containing `workerId`, `rowIndex`, `stage` (`STARTED`, `RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`), and message.
  * `row-completed`: Transmits the full enriched entity payload (attributes, sources, confidence) as soon as an individual row completes.
  * `job-completed`: Terminal event signaling the batch is complete (`COMPLETED` or `FAILED`).
* **Replay Buffer**: Holds the last 500 events per job in a thread-safe circular buffer. If the client browser reconnects or refreshes, it immediately replays historical events to restore active worker cards.

### Stage 4: Input Cleansing
* Before executing research, `dataset-service` invokes `ai-intelligent-service` (`POST /api/v1/ai/clean`) to normalize noisy inputs:
  * Strips emojis, honorifics, and irrelevant social handles from names.
  * Parses compound titles (`"VP Engineering & Co-Founder"` $\rightarrow$ primary role: `"VP Engineering"`).
  * Normalizes and unwraps URLs.

### Stage 5: Bounded Concurrent Execution
* **Managed Thread Pool**: Rows are executed concurrently via `enrichmentTaskExecutor` with a bounded worker pool (default: 3 threads) and a 500-item queue.
* **Row-Level Error Isolation**: Each row execution is wrapped in an isolated `CompletableFuture`. If row 2 experiences an upstream timeout or 403 scraping error, only row 2 is marked `FAILED`. Workers processing rows 0, 1, and 3 proceed unaffected.
* **Subsystem Delegation**:
  1. `research-service` discovers candidate URLs, scrapes pages, and extracts evidence.
  2. `ai-intelligent-service` extracts verbatim quotes and scores profiles.
  3. `dataset-service` persists the canonical entity snapshot into MySQL.

### Stage 6: Job Cancellation Lifecycle
* **Endpoint**: `POST /api/v1/enrichment/jobs/{jobId}/cancel`
* **Authorization**: The gateway-injected `X-User-Id` must match the job's creator. Cross-tenant cancellations return `403 Forbidden`.
* **Semantics**: Sets the job state to `CANCELLED`. In-flight worker threads finish their current row gracefully, while all pending rows in the queue are dropped without starting.

### Stage 7: Evidence Inspection & Non-Destructive Export
* **Live Inspection**: Completed rows are immediately viewable in the frontend table and "Recently Completed" drawer while remaining rows are still running.
* **Non-Destructive Export**: Users can export the enriched dataset to CSV or XLSX at any time. The export preserves all original columns and order, appending new `Enriched_<attribute>`, `Canonical_Url`, and `Enrichment_Status` columns.
