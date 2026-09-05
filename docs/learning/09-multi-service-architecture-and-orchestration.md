# Concept 09: Multi-Service Architecture, Isolation & Cross-Service Orchestration

In an enterprise data intelligence platform, business capabilities span fundamentally different operational profiles: relational catalog persistence requires ACID guarantees; web research requires network I/O with rate-limiting and timeouts; AI enrichment requires token streaming, prompt synthesis, and GPU/LLM interactions.

This guide explains how this platform decomposes these concerns into isolated microservices (`frontend`, `dataset-service`, `research-service`, `ai-intelligent-service`) that communicate over strict HTTP contracts without shared database schemas or domain model coupling.

---

## Why This Exists

Early implementations of research systems often place web scraping, database models, and LLM SDKs into a single monolithic backend. As the project evolves:
1. Long-running web scraping threads exhaust database connection pools.
2. Changes to external AI provider libraries force redeployment and schema re-verification of persistence layers.
3. Batch operations (e.g. enriching a 50-row dataset) block interactive user queries on saved entity catalogs.

By separating the system into four autonomous components, each service scales, deploys, and fails independently according to its operational demands.

---

## The Problem

A naive implementation typically couples services in one of three ways:
* **Shared Database Anti-Pattern**: Allowing both `research-service` and `dataset-service` to read and write directly to MySQL tables (`entities`, `entity_attributes`). Schema migrations in one service silently break queries in another, and transactions can lock across network boundaries.
* **Shared Domain Model JAR**: Packaging domain entities and business records into a common `shared-domain.jar` imported by all services. Any field addition or refactoring forces a lock-step deployment across the entire platform.
* **Cascading Failure Fragility**: Having Service A synchronously block on Service B, which blocks on Service C. If Service C (e.g. external AI model) throttles or slows down, the entire chain backs up, exhausting thread pools across all upstream microservices.

---

## The Core Idea

1. **Strict Ownership Boundaries**:
   * `dataset-service` owns batch orchestration, relational schemas, Flyway migrations, and the entity catalog.
   * `research-service` owns URL canonicalization, web discovery, search engine integration, HTML scraping, and heuristic rule extraction.
   * `ai-intelligent-service` owns prompt templates, Spring AI integration, structured LLM extraction, requirement interpretation, and synthesis.
   * `frontend` owns file ingestion, preview, column mapping, live progress tracking, and tabular dataset inspection.
2. **Contract-Based HTTP Integration**: Services communicate strictly via HTTP REST using independent, private Data Transfer Objects (DTOs). No service imports domain classes or persistence models from another service.
3. **Resilient Downstream Degradation**: Downstream service calls are guarded with client timeouts and fallback defaults. If the AI service is unavailable, `dataset-service` and `research-service` continue using deterministic heuristic evidence rather than failing the user's batch.

---

## How This Project Uses It

```
apps/
├── frontend/                       # Port 3000 (Next.js, TypeScript, Tailwind)
│   ├── app/page.tsx                # Batch, Single, and Catalog workflow tabs
│   └── lib/api.ts                  # Direct REST clients to backend services
└── backend/
    ├── dataset-service/            # Port 9743 (Spring Boot, JPA, Flyway, MySQL)
    │   ├── controller/             # EnrichmentJobController, EntityController
    │   ├── service/                # DefaultDatasetEnrichmentService (Batch Orchestrator)
    │   └── integration/client/     # ResearchServiceClient, AiServiceClient
    ├── research-service/           # Port 9741 (Spring Boot, Jsoup, ThreadPool)
    │   ├── api/                    # ResearchController (/api/v1/research)
    │   ├── research/               # ResearchOrchestrator, SourceProcessor
    │   └── extraction/             # DefaultSourceEvidenceService, EvidenceExtractor
    └── ai-intelligent-service/     # Port 9742 (Spring Boot, Spring AI, Gemini)
        ├── controller/             # EnrichmentAiController, ExtractionController
        ├── prompt/                 # PromptTemplates (strict JSON prompts)
        └── service/                # SpringAiEnrichmentService, SpringAiExtractionService
```

### 1. The Cross-Service Orchestration Flow

In [`DefaultDatasetEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/service/DefaultDatasetEnrichmentService.java), the batch orchestrator coordinates a 5-stage pipeline across service boundaries:

```java
// Stage 1: Interpret user requirement via ai-intelligent-service
AiServiceClient.RequirementCallResponse reqResponse = aiServiceClient.interpretRequirement(
        request.userRequirement(), request.defaultEntityType(), row
);
List<String> targetFields = reqResponse != null ? reqResponse.requestedFields() : List.of();

// Stage 2: Web research & evidence discovery via research-service
ResearchServiceClient.ResearchCallResponse researchResp = researchServiceClient.executeResearch(
        new ResearchServiceClient.ResearchCallRequest(url, entityType, name, org, role, targetFields, requirement)
);

// Stage 3: Prepare evidence map from research response
Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap = extractEvidence(researchResp);

// Stage 4: AI synthesis via ai-intelligent-service
AiServiceClient.SynthesisCallResponse aiResp = aiServiceClient.synthesizeEnrichment(
        new AiServiceClient.SynthesisCallRequest(rawRow, displayName, entityType, canonicalUrl, requirement, targetFields, evidenceMap, sourceUrls)
);

// Stage 5: Relational persistence via local EntityPersistenceService
persistenceService.persistOrUpdate(new PersistEntityRequest(entityId, displayName, entityType, canonicalUrl, entitySources, finalAttributes));
```

### 2. Isolated REST Clients

Instead of using heavy RPC or shared interfaces, [`ResearchServiceClient.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/integration/client/ResearchServiceClient.java) and [`AiServiceClient.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/integration/client/AiServiceClient.java) use Spring's modern `RestClient`:

```java
@Component
public class ResearchServiceClient {
    private final RestClient restClient;

    public ResearchServiceClient(@Value("${services.research-service.url:http://localhost:9741}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ResearchCallResponse executeResearch(ResearchCallRequest request) {
        return restClient.post()
                .uri("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ResearchCallResponse.class);
    }
}
```

Notice that `ResearchCallRequest` and `ResearchCallResponse` are private records declared directly inside `ResearchServiceClient`. If `research-service` adds internal pipeline fields, `dataset-service` ignores them without compilation or deserialization errors.

---

## Flow

```
+------------------+
|     Browser      |
|  (apps/frontend) |
+--------+---------+
         |
         | 1. Submit Batch / Poll Progress (HTTP :9743)
         v
+-----------------------------------------------------------+
|                      dataset-service                      |
|  (DefaultDatasetEnrichmentService - Batch Orchestrator)   |
+--------+------------------------------------+-------------+
         |                                    |
         | 2. Interpret Requirement           | 3. Execute Research
         |    (HTTP POST :9742)               |    (HTTP POST :9741)
         v                                    v
+-------------------------+         +-------------------------+
|  ai-intelligent-service |         |     research-service    |
| (SpringAiEnrichment)    |         | (ResearchOrchestrator)  |
+--------+----------------+         +------------+------------+
         |                                       |
         |                                       | 4. Search & Fetch Web
         |                                       v
         |                              +-----------------+
         |                              | Public Internet |
         |                              | (Tavily/Sites)  |
         |                              +-----------------+
         |                                       |
         | 5. Synthesize Verified Evidence       |
         |    (HTTP POST :9742) <----------------+
         v
+-------------------------+
| Relational Persistence  |
| (dataset-service -> DB) |
+-------------------------+
```

---

## Important Design Decisions

1. **`dataset-service` as the Central Coordinator**:
   We placed the dataset batch runner in `dataset-service` rather than `research-service` because dataset uploads and batch jobs are directly tied to persistence, dataset cataloging, and relational storage. `research-service` remains a stateless, high-throughput compute engine that researches a single target without needing to know about CSV files or database tables.
2. **Isolated DTOs Over Shared Schema Libraries**:
   Each service defines its own DTOs for external calls. While this introduces minor duplication of record definitions, it eliminates distributed binary coupling. A developer can refactor `research-service` internals without creating merge conflicts or version skew in `dataset-service`.
3. **Graceful Fallback on Downstream Failure**:
   In `DefaultDatasetEnrichmentService.java`, if the call to `aiServiceClient.synthesizeEnrichment()` throws an exception (due to rate-limiting or network error), the system falls back directly to the raw evidence map extracted by `research-service`. The batch never crashes, and the user receives verified evidence even during AI service degradation.

---

## Alternatives

| Alternative | Why We Did Not Choose It |
| :--- | :--- |
| **Monolithic Single Application** | Fast to build initially, but creates thread starvation when web scraping blocks HTTP threads and complicates independent horizontal scaling. |
| **Shared Database Access** | Bypasses HTTP boundaries, but couples database schema across teams and destroys data encapsulation. |
| **Message Broker (RabbitMQ / Kafka)** | Ideal for high-throughput enterprise scale, but introduces infrastructure operational overhead for a working prototype. The bounded in-memory thread pool provides async isolation today while establishing the clean boundaries needed for future broker migration. |

---

## Common Mistakes

1. **Leaking Internal Entity Objects into Remote DTOs**:
   Returning JPA `@Entity` instances directly over HTTP causes Jackson serialization loops, lazy-loading exceptions outside transactions, and leaks internal database column names to client services.
2. **Missing Timeouts on Inter-Service HTTP Calls**:
   Using `RestClient` without connection and read timeouts means a slow external search query in `research-service` can hang a worker thread in `dataset-service` indefinitely.
3. **Circular Dependencies Across Services**:
   If Service A calls Service B, Service B must NEVER call Service A directly. If `research-service` needed persistence, it must not call `dataset-service` while being orchestrated by `dataset-service`. Instead, `dataset-service` orchestrates the lifecycle and persists the result.

---

## Production Considerations

* **Service Discovery & API Gateways**: In local Docker Compose, services communicate via static Docker hostnames (`research-service:9741`, `ai-intelligent-service:9742`). In production Kubernetes, an ingress controller or service mesh (e.g. Envoy, Istio) handles mutual TLS, health checking, and dynamic DNS routing.
* **Message Queue Migration**: As batch volumes grow from 50 rows to 50,000 rows, the in-memory `ExecutorService` in `dataset-service` should be replaced with a distributed message queue (RabbitMQ or AWS SQS) where each row is a discreet message processed by a fleet of worker pods.
* **Distributed Tracing**: With requests traversing 3 services, passing a `X-Correlation-Id` header (via MDC logging) across all HTTP clients is essential for tracking a single row's journey in tools like Jaeger or Zipkin.

---

## What I Should Learn From This

1. **Microservice boundaries are defined by data ownership, not arbitrary code splitting.**
2. **Never share database tables or entity classes across microservice repositories.**
3. **Design inter-service workflows so that downstream failure results in graceful feature degradation rather than total system failure.**
4. **Keep domain orchestrators stateless where possible, delegating persistence strictly to the system of record.**

---

**Previous:** [Concept 08: Note on Consolidation](08-canonicalization-and-configuration-binding.md) | **Next:** [Concept 10: Spring AI Model Abstraction, Prompt Engineering & Deterministic Fallbacks](10-spring-ai-model-abstraction-and-prompt-engineering.md)
