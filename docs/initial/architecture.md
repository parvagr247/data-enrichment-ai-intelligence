# Architecture

> **Status:** Active  
> **Version:** 1.0  
> **Last Updated:** 2026-09-05

## Purpose

This document details the production-oriented architecture of the Data Enrichment AI Intelligence Platform, the role of the microservice ecosystem, network boundaries, resilience patterns, and the scalability roadmap.

---

## 1. Overall System Architecture

The platform is designed as a modular, resilient microservice system with zero unnecessary distributed overhead.

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Next.js Frontend (:3000)                        │
│             Interactive Research Hub • Live Async Job Poller • Catalog      │
└──────────────┬───────────────────────────────────────────────┬──────────────┘
               │                                               │
               │ HTTP REST                                     │ HTTP REST
               ▼                                               ▼
┌───────────────────────────────────────────────┐ ┌───────────────────────────┐
│           research-service (:9741)            │ │   dataset-service (:9743) │
│  - Bounded ThreadPoolExecutor (Async Jobs)    │ │ - JPA Relational Storage  │
│  - Deterministic Entity Normalizer (SHA-256)  │ │ - Pagination & Sorting    │
│  - Polite Scraper & Tavily Search Discovery   │ │ - Cascade Entity Graph    │
│  - Multi-Source Corroboration Engine          │ └─────────────┬─────────────┘
│  - Diagnostic Warning Collector               │               │
└──────────────┬─────────────────┬──────────────┘               │
               │                 │                              │
               │ HTTP REST       │ HTTP REST                    │ JDBC
               ▼                 ▼                              ▼
┌───────────────────────────┐   ┌─────────────────────────────────────────────┐
│  ai-intelligent-service   │   │                 MySQL (:3306)               │
│          (:9742)          │   │  - enriched_entities (indexed by type/date) │
│  - Google Gemini Model    │   │  - enriched_sources (indexed by entity_id)  │
│  - Heuristic Fallback     │   │  - enriched_attributes (indexed by attr/id) │
└───────────────────────────┘   └─────────────────────────────────────────────┘
```

---

## 2. Service Responsibilities & Boundaries

### 2.1 Research Service (`research-service` :9741)
- **Deterministic Cleaning & Normalization**: Strips ad/tracking params (`utm_*`, `fbclid`, etc.), trims trailing slashes, sorts functional query parameters, and generates deterministic SHA-256 canonical entity IDs.
- **Polite Retrieval & Bounded I/O**: Configurable timeouts (`connectTimeoutMs=3000`, `readTimeoutMs=5000`), maximum payload limits (`maxResponseSizeMb=5`), and source count caps (`maxSources=5`).
- **Autonomous Discovery**: Integrates with Tavily search API or deterministic mock search to discover corroborating URLs based on entity name and type.
- **Multi-Source Corroboration**: Evaluates multiple sources; upgrades confidence tiers (`LOW` &rarr; `MEDIUM`, `MEDIUM` &rarr; `HIGH`), records corroborating citations, and preserves conflicting assertions without arbitrary clobbering.
- **Partial Failure Resiliency**: Emits structured warnings (`warnings: [...]`) for unreachable or unparseable URLs rather than failing the entire research run.
- **Async Job Engine**: In-memory bounded `ThreadPoolExecutor` (core: 4, max: 16, queue: 500, `CallerRunsPolicy`) with graceful shutdown (`DisposableBean`).

### 2.2 AI Intelligent Service (`ai-intelligent-service` :9742)
- **Grounded Structured Extraction**: Maps text snippets to structured entity attributes strictly from retrieved evidence.
- **Graceful Degradation**: If `GEMINI_API_KEY` is missing or the external API call fails/times out, seamlessly falls back to deterministic heuristic extraction.

### 2.3 Dataset Service (`dataset-service` :9743)
- **Relational Snapshot Persistence**: Stores canonical entities, sources, and attribute evidence into MySQL with foreign key cascading.
- **Fast Lookups & Catalog Pagination**: Database indexes on `entity_type`, `updated_at`, `entity_id`, and `attribute_name`. Supports paginated browsing (`GET /api/v1/entities?page=0&size=20`).

### 2.4 Frontend (`apps/frontend` :3000)
- **Full Research Interface**: Next.js (App Router), Tailwind CSS, Lucide icons.
- **Dual Mode**: Asynchronous job submission with 1s polling, status badge, progress bar, duration timer, and synchronous quick-run.
- **Explainable Results**: Highlighting confidence levels (`HIGH`, `MEDIUM`, `LOW`), verbatim evidence quotes, clickable source URLs, and multi-source corroboration badges.
- **Entity Catalog**: Direct view into persisted records with pagination and inspector modal.

---

## 3. Resilience & Production Guardrails

1. **Memory & Thread Safety**: Concurrency is bounded to prevent unbounded task submission from exhausting JVM heap memory.
2. **Network Timeouts**: Every HTTP call to external web targets or AI endpoints is bounded by strict connect and read timeouts.
3. **Observability**: SLF4J MDC (Mapped Diagnostic Context) attaches `entityId` and `jobId` across log messages for clear correlation.
4. **Graceful Degradation**: Partial failures (e.g. one dead link among five sources) do not abort the entire research pipeline; instead, the run succeeds with a `PARTIAL` status and populated `warnings`.

---

## 4. Scalability & Evolution Roadmap

When dataset volumes scale beyond single-node requirements:

| Scaling Need | Architectural Evolution |
| :--- | :--- |
| **High-Volume Job Queue** | Replace in-memory `ThreadPoolExecutor` with **RabbitMQ** or **Apache Kafka** (`enrichment.jobs.submitted`, `enrichment.jobs.completed`). |
| **Distributed Caching** | Introduce **Redis** for HTTP response caching and domain rate-limiting. |
| **Read/Write DB Separation** | Add MySQL read replicas for entity catalog queries while routing writes to the primary database. |
| **Distributed Workers** | Containerize research workers with horizontal pod autoscaling (HPA) in Kubernetes if container orchestration is later required. |
