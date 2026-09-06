# Dataset Service

The **Dataset Service** acts as the batch orchestration coordinator, real-time observability stream provider, and relational database persistence boundary (MySQL via Spring Data JPA + Flyway) for the platform.

---

## 1. Why This Service Exists

* **Separation of Volatility**: Web scraping and LLM synthesis are non-deterministic, long-running, and failure-prone. This service isolates data modeling, ACID transactions, and query serving from pipeline execution.
* **Bounded Parallel Batch Orchestration**: Coordinates multi-row dataset enrichment jobs across managed worker pools with row-level error isolation.
* **Real-Time Execution Observability**: Streams live worker stage transitions to the frontend using Server-Sent Events (SSE).
* **Idempotent Snapshot Storage**: Allows repeated runs of the research pipeline without duplicating rows or leaving corrupted partial states.

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/enrichment/jobs` | Submits a batch enrichment job across dataset rows. |
| `GET` | `/api/v1/enrichment/jobs/{jobId}` | Retrieves job status, progress counters, and row results. |
| `GET` | `/api/v1/enrichment/jobs/{jobId}/events` | Real-time Server-Sent Events (SSE) progress stream. |
| `POST` | `/api/v1/enrichment/jobs/{jobId}/cancel` | Cancels a running batch job. |
| `POST` | `/api/v1/enrichment/single` | Synchronous enrichment of a single row. |
| `POST` | `/api/v1/entities` | Idempotent persistence of an entity with sources and attributes. |
| `GET` | `/api/v1/entities` | Paginated catalog query of saved entities. |
| `GET` | `/api/v1/entities/{id}` | Retrieves full entity details, sources, and attribute evidence. |
| `GET` | `/actuator/health` | Service health status probe (`UP`). |

---

## 3. Architecture & Non-Trivial Implementation

### A. Bounded Thread Pool (`EnrichmentTaskExecutor`)
* Custom `ThreadPoolTaskExecutor` (default: 3 workers, configurable via `enrichment.concurrency.workers`).
* Bounded queue capacity (500) paired with `CallerRunsPolicy` to prevent thread exhaustion and memory starvation.

### B. Server-Sent Events with Replay Buffer
* `subscribeJobEvents(jobId)` leverages Spring `SseEmitter`.
* Retains the last 500 events per job in a thread-safe replay buffer, allowing newly connected or reconnected clients to reconstruct active worker cards instantly.

### C. Idempotent Upsert via JPA `orphanRemoval`
* Relational mapping in `EntityRecord` configures `orphanRemoval = true`.
* Re-saving an entity clears previous child collections and re-inserts new sources and attributes within an atomic `@Transactional` boundary.

---

For complete API contracts, see [API Reference](../../../docs/api.md).  
For local development, see [Development Guide](../../../docs/development.md).
