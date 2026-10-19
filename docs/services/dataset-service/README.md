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
| `POST` | `/api/v2/datasets/upload` | Uploads and parses tabular CSV / XLSX files with delimiter sniffing and malformed row detection. | Auth (JWT) |
| `POST` | `/api/v2/datasets/profile` | Profiles dataset columns, detects semantic roles, identity candidates, and quality score. | Auth (JWT) |
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

The service strictly adheres to a **simplified 5-package architecture** with low coupling and high cohesion:

```
com.subdual.dataset_service
├── DatasetServiceApplication.java
│
├── dataset/                                      # Domain 1: Ingestion, parsing & profiling
│   ├── DatasetIngestionController.java           # POST /api/v2/datasets/upload, /profile
│   ├── DatasetIngestionService.java              # Ingestion contract
│   ├── DefaultDatasetIngestionService.java       # Ingestion orchestrator
│   ├── DatasetParser.java                        # Strategy interface for file parsers
│   ├── DatasetParserFactory.java                 # Parser factory by Content-Type / extension
│   ├── CsvDatasetParser.java                     # RFC 4180 CSV parser with delimiter sniffing
│   ├── ExcelDatasetParser.java                   # Apache POI streaming XLSX parser
│   ├── DatasetProfiler.java                      # Semantic schema profiler
│   ├── DefaultDatasetProfiler.java               # Column role detection & quality scoring
│   ├── ColumnRoleDetector.java                   # Heuristic role classification (Name, URL, etc.)
│   ├── DatasetValidator.java                     # Upload validation & sanity checks
│   ├── dto/ (DatasetProfileRequest, DatasetProfileReport)
│   └── model/ (RawDataset, ColumnProfile, ColumnRole, MalformedRow)
│
├── enrichment/                                   # Domain 2: Batch orchestration & SSE streaming
│   ├── EnrichmentJobController.java              # POST /jobs, GET /jobs/{id}, GET /events
│   ├── DatasetEnrichmentService.java             # Batch enrichment coordinator contract
│   ├── DefaultDatasetEnrichmentService.java      # Job lifecycle & bounded thread coordination
│   ├── EnrichmentEngineFacade.java               # Unified facade interface
│   ├── DefaultEnrichmentEngineFacade.java        # Facade implementation
│   ├── EnrichmentJobManager.java                 # Thread-safe in-memory job state repository
│   ├── EnrichmentTaskExecutor.java               # Bounded ThreadPoolExecutor with CallerRunsPolicy
│   ├── RowEnrichmentProcessor.java               # Individual row research & AI pipeline execution
│   ├── RowIdentityResolver.java                  # Entity identity seed resolver
│   ├── event/                                    # GoF Bridge Pattern for event delivery
│   │   ├── JobEventBroadcaster.java              # Abstraction interface
│   │   ├── DefaultJobEventBroadcaster.java       # Event broadcaster implementation
│   │   ├── EventDeliveryChannel.java             # Implementor interface
│   │   └── SseEventDeliveryChannel.java          # SSE emitter channel with replay buffer
│   ├── integration/                              # Downstream RestClient clients
│   │   ├── ResearchServiceClient.java            # Client to research-service (:9741)
│   │   └── AiServiceClient.java                  # Client to ai-intelligent-service (:9742)
│   ├── dto/ (EnrichmentJobRequest, EnrichmentJobResponse, ExecutionEvent, RowEnrichmentResult, etc.)
│   └── model/ (JobState, ResearchFinding, ResearchProfile, ObjectiveAssessment, etc.)
│
├── entity/                                       # Domain 3: Relational persistence & entity catalog
│   ├── EntityController.java                     # GET /api/v1/entities, GET /{id}, POST /
│   ├── EntityPersistenceService.java             # Entity snapshot persistence contract
│   ├── DefaultEntityPersistenceService.java      # JPA entity service with orphan removal
│   ├── EnrichedEntityRepository.java             # Spring Data JPA repository
│   ├── EntityIdResolver.java                     # Entity identity resolver
│   ├── EntityMapper.java                         # Entity <-> DTO mapper
│   ├── model/
│   │   ├── EnrichedEntity.java                   # JPA entity with user_id multi-tenancy
│   │   ├── EnrichedSource.java                   # Discovered web source JPA child
│   │   └── EnrichedAttribute.java                # Corroborated attribute JPA child
│   └── dto/ (PersistEntityRequest, EntitySummaryResponse, EntityDetailResponse)
│
├── shared/                                       # Cross-cutting utilities & error handling
│   ├── exception/
│   │   └── GlobalExceptionHandler.java           # Central RFC 7807 error handler
│   ├── filter/
│   │   └── CorrelationIdFilter.java              # Inbound MDC correlation ID filter
│   ├── interceptor/
│   │   └── CorrelationIdClientInterceptor.java   # Outbound RestClient tracing interceptor
│   ├── dto/ (EntityAttributeDto, EntitySourceDto)
│   └── model/ (ApiErrorResponse)
│
└── configuration/                                # Spring configuration properties & beans
    ├── DatasetServiceConfiguration.java          # RestClient, RestClientBuilder, JobExecutor beans
    ├── EnrichmentProperties.java                 # Type-safe @ConfigurationProperties
    └── WebCorsConfiguration.java                 # CORS policy configuration
```

---

## 4. Architectural Implementation Highlights

1. **Clean 5-Package Architecture**:
   The service organizes code by business responsibility into `dataset/` (ingestion/profiling), `enrichment/` (concurrency/orchestration), `entity/` (JPA persistence), `shared/` (common models/exceptions), and `configuration/` (Spring beans).
2. **GoF Bridge Pattern for Observability**:
   `JobEventBroadcaster` acts as the abstraction while `EventDeliveryChannel` (`SseEventDeliveryChannel`) acts as the implementor, allowing pluggable streaming protocols (SSE, WebSocket, or message queue) without modifying the enrichment engine.
3. **Bounded Parallel Concurrency**:
   `EnrichmentTaskExecutor` wraps a managed `ThreadPoolExecutor` enforcing bounded workers (default: 3) and bounded queue capacity (default: 500) with `CallerRunsPolicy` backpressure.
4. **Idempotent Relational Persistence**:
   `EnrichedEntity` leverages JPA `orphanRemoval = true` within atomic `@Transactional` operations in `DefaultEntityPersistenceService` to overwrite existing attributes and sources on re-enrichment, preventing duplicate rows or orphaned evidence.

For in-depth implementation patterns (including `ThreadPoolExecutor` tuning, `RestClient` resilience, and Flyway schema details), see **[Dataset Service Internals](internals.md)**.
