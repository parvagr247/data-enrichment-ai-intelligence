# Dataset Service

## 1. Why This Service Exists
The **Dataset Service** acts as the system of record and transactional relational database boundary (MySQL via Spring Data JPA + Flyway) for enriched research entities.
* **Separation of Volatility**: Web scraping and LLM synthesis are non-deterministic, long-running, and failure-prone. This service isolates data modeling, ACID transactions, and query serving from pipeline execution.
* **Idempotent Snapshot Storage**: Allows repeated runs of the research pipeline without duplicating rows or leaving corrupted partial states.

---

## 2. API Specifications

### `POST /api/v1/entities`
Creates or idempotently updates an entity, its sources, and its attribute evidence.
* **Request Body** (`PersistEntityRequest`):
  ```json
  {
    "entityId": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "displayName": "Spring Boot",
    "entityType": "FRAMEWORK",
    "canonicalUrl": "https://spring.io/projects/spring-boot",
    "sources": [
      {
        "url": "https://spring.io/projects/spring-boot",
        "title": "Spring Boot",
        "snippet": "Spring Boot makes it easy...",
        "sourceType": "PRIMARY_DOCS",
        "domain": "spring.io",
        "provider": "tavily",
        "relevance": 0.95,
        "retrievedAt": "2026-09-05T10:00:00Z"
      }
    ],
    "attributes": {
      "description": {
        "value": "Makes it easy to create stand-alone applications",
        "sourceUrl": "https://spring.io/projects/spring-boot",
        "evidenceSnippet": "Spring Boot makes it easy...",
        "confidence": 0.95
      }
    }
  }
  ```
* **Response**: `201 Created` with full [`EntityDetailResponse`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/dto/EntityDetailResponse.java).

### `GET /api/v1/entities/{entityId}`
Retrieves complete entity details including all sources and attributes with confidence scores.
* **Response**: `200 OK` (`EntityDetailResponse`) or `404 Not Found`.

### `GET /api/v1/entities?page={page}&size={size}`
Retrieves a paginated list of lightweight entity summaries sorted by `updatedAt DESC`.
* **Response**: `200 OK` (`List<EntitySummaryResponse>`).

---

## 3. Service Flow (Short)

```
[HTTP Request]
   │
   ▼
EntityController
   │
   ▼
DefaultEntityPersistenceService (@Transactional)
   ├── persistOrUpdate:
   │     1. Lookup EnrichedEntity by entityId (or instantiate if absent)
   │     2. Set top-level metadata (displayName, canonicalUrl, entityType)
   │     3. entity.getSources().clear() -> append new EnrichedSource items
   │     4. entity.getAttributes().clear() -> append new EnrichedAttribute items
   │     5. entityRepository.save(entity) (triggers cascade deletes + inserts)
   │     └── Map and return EntityDetailResponse
   │
   ├── findById:
   │     └── entityRepository.findById(id) -> Map to EntityDetailResponse
   │
   └── list / listAll:
         └── Apply bounded PageRequest(safePage, safeSize) -> Map to EntitySummaryResponse
```

---

## 4. Critical & Non-Trivial Code (Why Only This)

### A. Atomic Orphan Removal via Clear & Append
* **Location**: [`DefaultEntityPersistenceService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/service/DefaultEntityPersistenceService.java#L43-L76) & [`EnrichedEntity.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/domain/EnrichedEntity.java#L55-L62)
```java
@OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
private List<EnrichedSource> sources = new ArrayList<>();

// In persistOrUpdate:
entity.getSources().clear();
// append fresh sources...
entity.getAttributes().clear();
// append fresh attributes...
```
* **Why only this**: When a research pipeline re-runs, older attributes or sources may no longer be relevant or supported by evidence. Calling `clear()` followed by `add()` on an `orphanRemoval = true` collection forces Hibernate to issue DELETE statements for missing children and INSERTs for new ones in a single atomic transaction. This prevents orphaned foreign keys and stale data accumulation.

### B. Deterministic SHA-256 Primary Key
* **Location**: [`EnrichedEntity.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/domain/EnrichedEntity.java#L36-L38)
```java
@Id
@Column(name = "entity_id", length = 64, nullable = false, updatable = false)
private String entityId;
```
* **Why only this**: Rather than using auto-incrementing database sequence IDs, the primary key is a deterministic 64-character SHA-256 hash computed upstream from the canonical URL or URN. This enables natural idempotency: identical research targets map to the exact same record without requiring distributed locks.

### C. Summary Projection vs Deep Hydration
* **Location**: [`DefaultEntityPersistenceService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/service/DefaultEntityPersistenceService.java#L111-L121)
```java
return new EntitySummaryResponse(
    e.getEntityId(), e.getDisplayName(), e.getEntityType(), e.getCanonicalUrl(),
    e.getSources() != null ? e.getSources().size() : 0,
    e.getAttributes() != null ? e.getAttributes().size() : 0,
    e.getUpdatedAt()
);
```
* **Why only this**: Listing catalog entities does not serialize deep nested sources and evidence snippets. Returning collection sizes keeps list endpoints fast, lightweight, and bandwidth-efficient.

### D. Strict Schema Migrations via Flyway & Hibernate Validation
* **Location**: [`application.yaml`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/resources/application.yaml#L16-L26)
```yaml
jpa:
  hibernate:
    ddl-auto: validate
flyway:
  enabled: true
  baseline-on-migrate: true
```
* **Why only this**: Relying on `ddl-auto: update` causes unindexed queries, silent type shifts, and schema drift. Migrations are strictly versioned SQL (`V1__initial_schema.sql`) checked into source control; Hibernate only validates schema compatibility.
