# Research Service

## 1. Why This Service Exists
The **Research Service** is the core orchestrator of the entire platform. It coordinates discovery, web page retrieval, grounded attribute extraction, multi-source corroboration, and persistence.
* **Separation of Concerns**: Orchestrates the workflow across external search APIs (Tavily/Mock), the AI extraction engine (`ai-intelligent-service`), and the relational store (`dataset-service`).
* **Asynchronous & Synchronous Execution**: Supports both real-time synchronous enrichment and non-blocking asynchronous background jobs with bounded concurrency.

---

## 2. API Specifications

### `POST /api/v1/research`
Executes synchronous end-to-end research for an entity.
* **Request Body** (`ResearchRequest`):
  ```json
  {
    "name": "Spring Boot",
    "url": "https://spring.io/projects/spring-boot?utm_source=docs",
    "entityType": "FRAMEWORK"
  }
  ```
* **Response**: `200 OK` [`ResearchResponse`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/dto/response/ResearchResponse.java) (includes target info, ranked sources, grounded evidence tuples, and diagnostic timings).

### `POST /api/v1/research/jobs`
Submits an asynchronous research task.
* **Request Body**: Same as `ResearchRequest`.
* **Response**: `202 Accepted` [`ResearchJobResponse`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/dto/response/ResearchJobResponse.java) with `jobId`, `status: SUBMITTED`, `progress: 0`.

### `GET /api/v1/research/jobs/{jobId}`
Polls the execution status of an asynchronous research job.
* **Response**: `200 OK` (`ResearchJobResponse` with `status: IN_PROGRESS | COMPLETED | FAILED`, duration, and completed result).

---

## 3. Service Flow (Short)

```
[Client Request]
   │
   ├─► Sync: ResearchController.executeResearch ──┐
   └─► Async: InMemoryResearchJobService.submitJob ─┤
                                                    ▼
                                     DefaultResearchPipeline.execute(context)
                                                    │
    ┌───────────────────────────────────────────────┴───────────────────────────────────────────────┐
    ▼ 1. VALIDATE: ResearchRequestValidator checks format and constraints                           │
    ▼ 2. NORMALIZE: EntityNormalizer cleans URL, strips tracking tags, hashes SHA-256 entityId     │
    ▼ 3. DISCOVER: ResearchDiscoveryService queries TavilySearchProvider or MockSearchProvider      │
    ▼ 4. PROCESS SOURCES: SourceProcessor fetches web HTML, cleans text, and ranks sources         │
    ▼ 5. EXTRACT EVIDENCE: SourceEvidenceService delegates to AiExtractionClient (port 9742)       │
    │     └── Applies multi-source corroboration and conflict resolution                            │
    ▼ 6. PERSIST SNAPSHOT: ResearchSnapshotPersister sends payload to dataset-service (port 9743)  │
    ▼ 7. ASSEMBLE: Bundles EvidenceTuples, execution time, and diagnostic warnings into response     │
    └───────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Critical & Non-Trivial Code (Why Only This)

### A. Grounded Evidence Tuple with Multi-Source Corroboration & Conflict Resolution
* **Location**: [`EvidenceExtractor.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/EvidenceExtractor.java#L133-L188)
```java
if (isAgreement(existing.value(), value)) {
    // Agreement detected -> boost confidence tier (LOW -> MEDIUM -> HIGH)
    ConfidenceTier boostedTier = switch (current) {
        case UNKNOWN, LOW -> ConfidenceTier.MEDIUM;
        case MEDIUM, HIGH -> ConfidenceTier.HIGH;
    };
    attributes.put(key, new EvidenceTuple(existing.value(), existing.sourceUrl(), combinedSnippet, boostedTier, sources, false));
} else {
    // Disagreement detected -> resolve based on tier precedence and flag conflict
    attributes.put(key, new EvidenceTuple(chosenValue, chosenSource, conflictSnippet, resolvedTier, sources, true));
}
```
* **Why only this**: Raw web extraction produces conflicting or low-quality data. Rather than blindly overwriting keys, this algorithm corroborates claims across distinct URLs. Agreement boosts confidence tier, while disagreement retains the competing fact in the snippet and explicitly sets `conflictDetected = true`.

### B. URL Canonicalization & Tracking Stripping
* **Location**: [`DefaultEntityNormalizer.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/normalization/DefaultEntityNormalizer.java#L53-L92)
```java
private static final Set<String> TRACKING_PARAMS = Set.of(
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
    "ref_src", "fbclid", "gclid", "mc_eid", "_ga", "_gl"
);
```
* **Why only this**: Research requests submitted with marketing tracking query parameters (e.g. `?utm_source=twitter`) would otherwise generate different SHA-256 entity hashes, causing duplicate database entries and cache misses for the exact same target webpage.

### C. Bounded Thread Pool with Backpressure (`CallerRunsPolicy`)
* **Location**: [`InMemoryResearchJobService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/service/InMemoryResearchJobService.java#L49-L57)
```java
this.executor = new ThreadPoolExecutor(
    4,
    16,
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(500),
    threadFactory,
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```
* **Why only this**: Scraping and AI extraction are high-latency I/O operations. An unbounded queue or thread pool causes thread starvation and OutOfMemoryError under burst traffic. Bounding the queue to 500 and using `CallerRunsPolicy` forces the submitting HTTP thread to execute tasks when overloaded, applying natural backpressure.

### D. Pluggable Service Mesh & Mock Adaptability
* **Location**: [`ServiceMeshConfiguration.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/configuration/ServiceMeshConfiguration.java) & [`ResearchDiscoveryConfiguration.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/configuration/ResearchDiscoveryConfiguration.java)
```java
// Dynamically wires TavilySearchProvider vs MockSearchProvider
// Dynamically wires RestAiExtractionClient vs NoOpAiExtractionClient
// Dynamically wires RestDatasetPersistenceClient vs NoOpDatasetPersistenceClient
```
* **Why only this**: Allows the entire research pipeline to run in offline local development or unit test suites without requiring paid API tokens (Tavily, Gemini) or active remote microservices.
