# Architecture

> **Status:** Current  
> **Version:** 0.1  
> **Last Updated:** 2026-09-04

## Purpose

This document details the architectural design of the Data Enrichment & Research Engine, the role of the Spring ecosystem, the authoritative repository layout, the evolutionary roadmap, and key architectural decisions.

---

## 1. Overall Architecture

The platform follows an incremental, pragmatic architectural philosophy: start with the smallest possible architecture that can solve the core enrichment problem, then scale as data volume demands.

### High-Level Data Flow

```text
Raw Entity Input (CSV / JSON)
              │
              ▼
   Deterministic Cleaning & ID
              │
              ▼
    Autonomous Web Research
              │
              ▼
   Evidence Compilation & Citations
              │
              ▼
    Spring AI Structured Extraction
              │
              ▼
    Configurable AI Intelligence & Scoring
              │
              ▼
     Human Review & Verification
              │
              ▼
    Structured Export (CSV / JSON / DB)
```

### Architectural Principles

1. **Raw Data Invariance**: Original input records are never mutated; enriched attributes are added as structured layers.
2. **Deterministic Processing First**: Deduplication, string cleaning, canonical ID generation (SHA-256), and cache lookups execute deterministically before external network calls or AI reasoning.
3. **Evidence Before Inference**: Search first, reason second, structure third. Language models extract facts strictly from retrieved evidence.
4. **Pragmatic Scaling**: Build and validate the complete end-to-end research flow inside a **single Spring Boot application** before considering distributed services.

---

## 2. Spring Ecosystem

The backend is fundamentally a **Java + Spring ecosystem** project.

### Core Intended Stack

* **Java 21 (LTS)**: Strong typing, pattern matching, and virtual threads for high-concurrency external I/O.
* **Spring Boot 3.x**: Enterprise foundation, robust dependency injection, auto-configuration, and production-ready Actuator metrics.
* **Spring AI (1.x Milestone)**: Native Spring abstractions for LLM interactions, structured output mapping, and function/tool-calling.
* **Docker & Docker Compose**: Minimal containerization for local development dependencies.

### Potential Components (Adopted ONLY When Genuinely Required)

* **Spring Web**: REST endpoints for dataset upload, enrichment triggers, and export.
* **Spring Data / PostgreSQL**: Relational persistence for master records, evidence logs, and audit trails (introduced when in-memory storage is insufficient).
* **Spring Validation**: Declarative schema and input validation.
* **Spring Kafka / Apache Kafka**: Asynchronous message broker for high-throughput batch scaling (deferred to Phase 3).
* **Spring Cloud Gateway & Discovery**: Evaluated only if the backend is split into multiple independent microservices.

> **Rule**: Do not add dependencies or infrastructure merely because they exist in the Spring ecosystem. Every component must solve an immediate, verified requirement.

---

## 3. Project Structure

### Authoritative Repository Layout

```text
data-enrichment-engine/
│
├── apps/
│   └── backend/                        # Single Spring Boot application workspace
│
├── data/
│   ├── input/                          # Staging raw input files (CSV, TSV, JSON)
│   ├── output/                         # Enriched and evaluated output files (CSV, JSON)
│   └── samples/                        # Sample benchmark files (e.g., sample connection exports)
│
├── docs/
│   ├── initial/                        # Foundation definitions & initial architectural direction
│   │   ├── problem.md                  # Problem definition, scope, and reality constraints
│   │   ├── architecture.md             # System design, Spring stack, structure, ADRs
│   │   └── enrichment.md               # Research engine, tool calling, evidence, limitations
│   └── setup/                          # Pre-implementation technical setup & contracts
│       ├── entity-model.md             # Generic domain and entity definitions
│       └── api-design.md               # Minimal HTTP API surface and specifications
│
├── scripts/                            # Operational and setup scripts
│
├── docker-compose.yml                  # Minimal local development environment
├── README.md                           # Concise project summary and documentation index
└── .gitignore                          # Git ignore definitions
```

> **Technical Setup References**: Detailed pre-implementation domain models and API contracts are maintained under `docs/setup/`:
> * 📐 **[Entity Model](../setup/entity-model.md)**: Generic domain entities, evidence semantics, and in-memory vs. persistence boundaries.
> * 🔌 **[API Design](../setup/api-design.md)**: Minimal synchronous research API endpoint (`POST /api/v1/research`) and Spring Web conventions.

### Backend Package Conventions

When the Spring Boot application in `apps/backend/` is implemented, the internal package structure should remain domain-oriented and lean:

```text
src/main/java/com/enrichment/
├── controller/                         # REST endpoints (upload, review, export)
├── service/                            # Core workflow coordination
├── research/                           # Web research, HTTP fetching, search engine queries
├── enrichment/                         # Normalization, schema mapping, evidence compilation
├── ai/                                 # Spring AI prompts, ChatClient configuration, tool definitions
├── model/                              # Entity models, evidence tuples, request/response DTOs
└── config/                             # Application and tool configurations
```

> **Rule**: Avoid creating empty packages or excessive abstraction layers (no nested facade/manager/orchestrator/factory hierarchies without concrete need).

---

## 4. Current Architecture vs. Planned Evolution

### Current Architecture (Starting State)

```text
[Input CSV/JSON] ──► [Single Spring Boot Backend] ──► [Structured Output]
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
      Polite HTTP / Search         Spring AI Model
        (Evidence Tools)          (Extraction & Eval)
```

* **One Clean Spring Boot Application**: Proves the complete research loop on a single record and micro-batches before adding architectural complexity.
* **Zero Distributed Overhead**: No message brokers, no service registries, no distributed transactions at start.

### Planned Evolution (Only When Scale Justifies It)

1. **Milestone 1 (Current Target)**: Single Spring Boot service validating URL research, evidence-first extraction, and structured output on individual records.
2. **Milestone 2**: In-process thread-pool batch execution, Redis caching, and polite rate-limiting for 10–100 records.
3. **Milestone 3 (Future Scale)**: If processing volume (e.g., 2,500+ records) creates severe bottlenecks, extract approximately 2–3 meaningful services:
   * **Dataset Service**: Data ingestion, normalization, and export.
   * **Enrichment Service**: Autonomous web research and evidence compilation.
   * **Intelligence Service**: Spring AI scoring, ranking, and evaluation.
   * **Apache Kafka**: Asynchronous event streams (`enrichment.jobs.pending`, `enrichment.jobs.completed`, `enrichment.dlq`) connecting the stages.

---

## 5. Major Architectural Decisions

### ADR-001: Domain-Agnostic Generic Entity Model
* **Status**: Accepted
* **Context**: The project began with a sample export of ~2,500 professional connections.
* **Decision**: Model entities generically (`entity_id`, `canonical_url`, `display_name`, `attributes`, `provenance`). Professional profiles are treated strictly as an initial benchmark.
* **Reason**: Prevents premature coupling to any single platform (e.g., LinkedIn) and allows the engine to enrich companies, repositories, products, and publications.

### ADR-002: Evidence-First & Zero-Hallucination Policy
* **Status**: Accepted
* **Context**: LLMs naturally hallucinate missing information when asked to complete profile fields without grounding.
* **Decision**: Enforce "Search First, Reason Second, Structure Third". Every extracted field is an evidence tuple (`value`, `source_url`, `evidence_snippet`, `confidence`). If evidence cannot be retrieved, the field is explicitly marked as `UNKNOWN`.
* **Reason**: Trust and data integrity are essential; downstream business decisions require verifiable public citations.

### ADR-003: Core Technology Stack: Java 21, Spring Boot 3.x, and Spring AI
* **Status**: Accepted
* **Context**: High-throughput external I/O, enterprise maintainability, and clean LLM tool abstractions are needed.
* **Decision**: Adopt Java 21, Spring Boot 3.x, and Spring AI as the primary backend foundation.
* **Reason**: Java virtual threads provide excellent external I/O concurrency; Spring Boot offers production-ready conventions; Spring AI provides vendor-neutral tool calling and structured output.

### ADR-004: Single Application First; Decompose Only When Justified
* **Status**: Accepted
* **Context**: Distributed microservices introduce deployment, serialization, and debugging overhead.
* **Decision**: Build the entire research and intelligence workflow in a single Spring Boot application in `apps/backend/`. Microservices are deferred until single-entity and batch workflows are proven.
* **Reason**: Minimizes complexity and accelerates feedback cycles during the experimental phase.

### ADR-005: Kafka Deferred to Batch Scale Phase
* **Status**: Accepted
* **Context**: Event-driven brokers are valuable for scaling to thousands of records, but unnecessary for single-record research spikes.
* **Decision**: Do not implement Kafka, topics, or consumers until the core enrichment workflow is validated. Document Kafka as a target architecture for asynchronous batch scale.
* **Reason**: Avoids running unnecessary local infrastructure before business logic exists.

### ADR-006: Strict Public & Permitted Data Compliance
* **Status**: Accepted
* **Context**: Web enrichment can tempt developers to scrape authenticated or protected pages.
* **Decision**: Operate strictly within legal and technical boundaries: respect `robots.txt`, utilize public search APIs, never bypass login walls or CAPTCHAs, and cache all responses.
* **Reason**: Protects platform viability, avoids IP blacklisting, and ensures legal compliance.
