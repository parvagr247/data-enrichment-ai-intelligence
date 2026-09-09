# Dataset Service

The **Dataset Service** (`com.subdual.dataset_service`) is the batch orchestration coordinator, real-time execution observability provider, and relational persistence boundary (MySQL via Spring Data JPA + Flyway) for the platform.

---

## 1. Core Responsibilities

* **Separation of Volatility**: Web crawling and LLM prompting are non-deterministic, high-latency, and failure-prone. This service isolates ACID transactions, entity data models, and query serving from external network volatility.
* **Bounded Parallel Batch Orchestration**: Coordinates multi-row dataset enrichment jobs across a managed `ThreadPoolTaskExecutor` (default: 3 workers) with row-level error isolation.
* **Real-Time Observability**: Streams live worker state transitions, stage badges, and row completion events to the browser using Server-Sent Events (SSE).
* **Idempotent Snapshot Storage**: Allows re-running enrichment jobs on existing entities without duplicating rows or leaving orphan records.
* **User-Scoped Data Ownership**: Enforces tenant boundary isolation—users can only query, subscribe to, or cancel their own batch jobs and entity records.

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description | Public / Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/enrichment/jobs` | Submits a batch dataset enrichment job across rows. | Auth (JWT) |
| `GET` | `/api/v1/enrichment/jobs/{jobId}` | Retrieves job metadata, progress counters, and row statuses. | Auth (JWT) |
| `GET` | `/api/v1/enrichment/jobs/{jobId}/events` | Establishes a Server-Sent Events (SSE) stream for real-time progress. | Auth (JWT) |
| `POST` | `/api/v1/enrichment/jobs/{jobId}/cancel` | Cancels an active or queued batch job. | Auth (JWT) |
| `POST` | `/api/v1/enrichment/single` | Synchronously enriches a single entity row. | Auth (JWT) |
| `POST` | `/api/v1/entities` | Idempotently persists an entity snapshot with sources and attributes. | Internal / Auth |
| `GET` | `/api/v1/entities` | Paginated catalog query of saved entities for the authenticated user. | Auth (JWT) |
| `GET` | `/api/v1/entities/{id}` | Retrieves full entity details, sources, and attribute evidence. | Auth (JWT) |
| `GET` | `/actuator/health` | Service health status probe (`UP`). | Public |

---

## 3. Package Organization

The service strictly adheres to a **feature-centric, cohesive package structure**:

```
com.subdual.dataset_service
├── enrichment/
│   ├── api/
│   │   ├── controller/
│   │   │   └── DatasetEnrichmentController.java
│   │   └── dto/
│   │       ├── request/
│   │       │   ├── BatchEnrichmentRequest.java
│   │       │   └── SingleRowEnrichmentRequest.java
│   │       └── response/
│   │           ├── BatchJobResponse.java
│   │           ├── EnrichedRowResult.java
│   │           ├── EnrichmentExecutionEvent.java
│   │           └── SingleRowEnrichmentResponse.java
│   ├── service/
│   │   ├── DatasetEnrichmentService.java
│   │   ├── impl/
│   │   │   └── DefaultDatasetEnrichmentService.java
│   │   └── helper/
│   │       ├── RowEnrichmentProcessor.java
│   │       ├── EventPublisherHelper.java
│   │       └── SchemaMappingHelper.java
│   ├── integration/
│   │   ├── ResearchServiceClient.java
│   │   └── AiServiceClient.java
│   └── model/
│       ├── EnrichmentJob.java
│       ├── EnrichmentJobStatus.java
│       └── RowExecutionStatus.java
│
├── dataset/
│   ├── api/
│   │   ├── controller/
│   │   │   └── EntityCatalogController.java
│   │   └── dto/
│   │       ├── PersistEntityRequest.java
│   │       └── EntitySummaryResponse.java
│   ├── entity/
│   │   ├── EntityRecord.java
│   │   ├── EntitySourceRecord.java
│   │   └── EntityAttributeRecord.java
│   ├── repository/
│   │   └── EntityRecordRepository.java
│   └── service/
│       ├── EntityService.java
│       └── impl/
│           └── DefaultEntityService.java
│
├── common/
│   ├── exception/
│   │   └── GlobalExceptionHandler.java
│   └── validation/
│       └── DatasetValidationUtils.java
│
└── config/
    ├── DatasetServiceConfiguration.java
    ├── EnrichmentTaskExecutorConfig.java
    └── WebCorsConfiguration.java
```

---

## 4. Architectural Implementation Highlights

1. **Intentionally Small Service Layer**:
   `DefaultDatasetEnrichmentService` coordinates the high-level job state machine. Detailed row execution logic is encapsulated inside `RowEnrichmentProcessor`, event broadcasting is isolated in `EventPublisherHelper`, and schema mapping is in `SchemaMappingHelper`.
2. **Interface `default` Methods for Backward Compatibility**:
   When `userId` was introduced to enforce multi-tenant scoping, `DatasetEnrichmentService` added default overloaded methods (`getJob(jobId) -> getJob(jobId, null)`), preserving compatibility for legacy tests without breaking contract boundaries.
3. **Idempotent Relational Persistence**:
   `EntityRecord` leverages JPA `orphanRemoval = true` within atomic `@Transactional` operations to overwrite existing attributes and sources on re-enrichment, preventing duplicate rows or orphaned evidence.

For in-depth implementation patterns (including `ThreadPoolTaskExecutor` tuning, `RestClient` resilience, and Flyway schema details), see **[Dataset Service Internals](internals.md)**.
