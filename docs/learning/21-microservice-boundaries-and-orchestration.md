# Concept 21: Microservice Boundaries & Orchestration

Microservices promise independent scalability, autonomous deployments, and fault isolation. However, in distributed systems where services collaborate to enrich data, boundaries frequently erode into a **"Distributed Monolith"**: services sharing database tables, importing common business JARs, or making synchronous circular calls that deadlock under load.

This guide explains how this platform enforces clean **Microservice Boundaries**, delegates domain ownership, balances synchronous and asynchronous communication, and achieves true loose coupling across `dataset-service`, `research-service`, and `ai-intelligent-service`.

---

## Why This Exists

Our platform handles three fundamentally conflicting architectural concerns:
1. **Catalog & Batch Persistence (`dataset-service`)**: Needs relational integrity (ACID), transactional consistency, Flyway schema migrations, and fast indexed catalog queries.
2. **Web Discovery & Extraction (`research-service`)**: High I/O throughput, non-blocking network calls, external search engine rate-limiting, and bounded thread pools.
3. **Cognitive Synthesis (`ai-intelligent-service`)**: Token-intensive LLM inference, prompt templating, and model provider decoupling.

Running all three in a single process creates severe resource contention: slow web scrapers exhaust database connection pools, and AI memory spikes cause JVM garbage collection pauses that freeze user API queries.

---

## Problem

A naive microservice implementation typically creates:
* **The Shared-Database Trap**: Giving multiple microservices access to the same MySQL instance. When `dataset-service` migrates a column name with Flyway, `research-service` crashes with SQL syntax errors.
* **Shared Binary Domain Coupling**: Packaging models into a shared library (`shared-domain.jar`). Any model change forces a recompile and coordinated redeployment of all 3 services.
* **Synchronous Cascading Block**: `dataset-service` makes a blocking HTTP call to `research-service`, which makes a blocking HTTP call to `ai-intelligent-service`. If `ai-intelligent-service` slows down, threads backup across all three services, crashing the entire cluster.

---

## Core Idea

The core idea is **Contract-First Autonomous Service Boundaries**:

```mermaid
flowchart LR
    subgraph UI ["Client (Port 3000)"]
        Browser["Next.js Application"]
    end

    subgraph Service_Boundary_1 ["dataset-service (Port 9743)"]
        BatchOrchestrator["DatasetEnrichmentService"]
        Persistence["EntityPersistenceService"]
        DB[("MySQL 8.0<br/>Exclusive Ownership")]
        Persistence --> DB
    end

    subgraph Service_Boundary_2 ["research-service (Port 9741)"]
        ResearchOrchestrator["ResearchOrchestrator"]
        Scraper["WebContentFetcher & Extractor"]
    end

    subgraph Service_Boundary_3 ["ai-intelligent-service (Port 9742)"]
        AIService["SpringAiEnrichmentService"]
        Gemini["Spring AI Gemini Model"]
        AIService --> Gemini
    end

    Browser -->|1. Submit Batch (Async 202)| BatchOrchestrator
    Browser -.->|Poll Progress| BatchOrchestrator
    
    BatchOrchestrator -->|2. Interpret Intent (Sync HTTP)| AIService
    BatchOrchestrator -->|3. Execute Research (Sync HTTP)| ResearchOrchestrator
    ResearchOrchestrator --> Scraper
    
    BatchOrchestrator -->|4. Synthesize Evidence (Sync HTTP)| AIService
    BatchOrchestrator -->|5. Save Entity| Persistence
```

1. **Single-Service Database Ownership**: Only `dataset-service` possesses database credentials and writes to MySQL. Neither `research-service` nor `ai-intelligent-service` has direct database access.
2. **Zero Shared Code**: No common business JAR exists. Each microservice defines its own internal records, validation rules, and DTOs.
3. **Synchronous Internal Calls, Asynchronous External Ingestion**:
   * Between frontend and backend: Ingestion is **asynchronous** (`202 Accepted` + polling) so the browser is never blocked.
   * Between backend services: Cross-service calls are **synchronous HTTP REST** with strict client timeouts. This eliminates complex distributed transaction sagas while bounded thread pools prevent thread starvation.
4. **Resilient Downstream Degradation**: If `ai-intelligent-service` goes down, `dataset-service` falls back to raw research evidence, completing the batch without failure.

---

## How It Works

### 1. The Service Responsibility Matrix

| Microservice | Port | Exclusive Domain Responsibility | Key Tech Stack |
| :--- | :--- | :--- | :--- |
| **`dataset-service`** | `9743` | **The System of Record**: Batch job lifecycle, row-level error isolation, relational schema, Flyway migrations, catalog search. | Spring Data JPA, Hibernate, Flyway, MySQL Driver |
| **`research-service`** | `9741` | **The Evidence Engine**: URL canonicalization, search engine integration (Tavily), HTML scraping (Jsoup), domain regex extractors, entity resolution. | Spring Boot, Jsoup, ThreadPoolExecutor |
| **`ai-intelligent-service`** | `9742` | **The Synthesis Provider**: Natural language requirement interpretation, structured LLM extraction, prompt schemas, and deterministic fallbacks. | Spring AI, Google GenAI starter |
| **`frontend`** | `3000` | **The Interactive Workflow**: Tabular preview, column detection heuristics, requirement chips, live progress tracking, and CSV/XLSX export. | Next.js 15, React, TypeScript, SheetJS |

### 2. Isolated Private Clients

In [`ResearchServiceClient.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/integration/client/ResearchServiceClient.java), `dataset-service` defines its own local contract records (`ResearchCallRequest`, `ResearchCallResponse`):

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

If `research-service` refactors internal classes or introduces private pipeline fields, `dataset-service` is unaffected.

---

## Where It Appears in This Project

* **Batch Orchestration**: [`DefaultDatasetEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/dataset-service/src/main/java/com/subdual/dataset_service/service/DefaultDatasetEnrichmentService.java) calls `researchServiceClient` and `aiServiceClient` sequentially per row.
* **Stateless Research**: [`ResearchController.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/api/ResearchController.java) handles single-target research requests without retaining state.
* **AI Cognitive Endpoints**: [`EnrichmentAiController.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/controller/EnrichmentAiController.java) exposes stateless `/api/v1/ai/requirement` and `/api/v1/ai/synthesize`.
* **Container Orchestration**: `docker-compose.yml` configures ports, service hostnames, and environment URLs across all 4 services.

---

## Design Decisions

| Decision | Justification |
| :--- | :--- |
| **`dataset-service` as the Master Coordinator** | Placing batch orchestration in `dataset-service` aligns with data gravity. Batches originate from datasets and end in database persistence; keeping the coordinator close to storage minimizes unnecessary data transfer. |
| **Stateless Compute in `research-service`** | `research-service` has zero database dependencies. It can be horizontally scaled from 1 replica to 10 replicas behind a load balancer without data partitioning challenges. |
| **HTTP REST Over gRPC / Message Brokers** | For current operational scales (batches of 10–100 rows), HTTP/JSON provides transparent debugging via curl/Postman and zero infrastructure overhead compared to Kafka or gRPC protobuf compilers. |

---

## Common Mistakes

1. **Creating Circular Calls Across Services**:
   Having Service A call Service B, which calls back into Service A. This leads to distributed deadlocks and complex failure cycles. All communication must flow in a strict directed acyclic graph (DAG).
2. **Allowing Service Outages to Cascade**:
   If `ai-intelligent-service` fails, throwing an unhandled exception that aborts the batch. Always provide fallback paths (e.g. falling back to raw research evidence).
3. **Sharing JPA Entities via Shared JARs**:
   Packaging `@Entity` classes into a shared JAR couples database schemas across teams and causes runtime lazy-loading exceptions.

---

## Practical Mental Model

Think of the microservice architecture as a **specialized medical clinic**:
* `dataset-service` is the **front desk and medical records archive**: it checks the patient in, creates the chart, schedules the appointment, and files the permanent record.
* `research-service` is the **diagnostic laboratory**: it runs blood tests and takes X-rays (web search and scraping), returning raw factual readings.
* `ai-intelligent-service` is the **consulting specialist**: it reads the lab reports and drafts an expert summary.
* The lab and the specialist never touch the patient's permanent archive directly; they communicate through formal medical requisitions.

---

## Implementation Status

* **CURRENT IMPLEMENTATION**: 3 Spring Boot microservices + 1 Next.js frontend, HTTP REST communication via Spring `RestClient`, exclusive MySQL ownership by `dataset-service`, zero shared-domain libraries.
* **ARCHITECTURAL DIRECTION**: Centralized API gateway for unified routing and authentication; distributed trace ID propagation across all HTTP headers.
* **FUTURE POSSIBILITY**: Transitioning batch row processing to a persistent task queue (e.g. MySQL `SELECT FOR UPDATE SKIP LOCKED` or RabbitMQ) for massive scale.

---

## Related Concepts

* **Previous:** [Concept 20: Deterministic & AI Hybrid Pipelines](20-deterministic-and-ai-hybrid-pipelines.md)
* **Next:** [Concept 22: Contract-First API Evolution](22-contract-first-api-evolution.md)
