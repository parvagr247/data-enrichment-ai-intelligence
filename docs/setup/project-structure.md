# Project Structure & Directory Reference

> **Status:** Active  
> **Version:** 0.1  
> **Last Updated:** 2026-09-05  

---

## 1. Top-Level Repository Structure

```text
data-enrichment-ai-intelligence/
│
├── .github/                        # CI workflows and repository automation
├── apps/
│   ├── backend/                    # Spring Boot microservices
│   │   ├── research-service/       # Research & web discovery orchestration (Port 9741)
│   │   ├── ai-intelligent-service/ # AI reasoning & structured extraction (Port 9742)
│   │   └── dataset-service/        # Dataset persistence & JPA boundary (Port 9743)
│   │
│   └── frontend/                   # Next.js client application (Port 3000)
│
├── data/
│   ├── input/                      # Staged raw input datasets (CSV, JSON)
│   ├── output/                     # Enriched and verified output datasets
│   └── samples/                    # Sample benchmark datasets
│
├── docs/
│   ├── initial/                    # Foundational specifications & ADRs
│   ├── learning/                   # Technical concept guides & web standards
│   └── setup/                      # Architecture, API design, & pipeline workflows
│
├── infrastructure/
│   └── docker/                     # Canonical container orchestration
│       ├── docker-compose-dev.yml      # Minimal infrastructure (MySQL only)
│       ├── docker-compose-dev-all.yml  # Complete development stack with Compose Watch
│       ├── commands.md                 # Docker execution workflows
│       └── .env.example                # Container environment template
│
├── .env.example                    # Root environment configuration template
├── .gitattributes                  # Line ending and git attribute configurations
├── .gitignore                      # Global git ignore definitions
└── README.md                       # Repository overview and documentation index
```

---

## 2. Directory Responsibilities

| Directory | Responsibility | Notes |
| :--- | :--- | :--- |
| `apps/backend/` | Houses all backend Spring Boot services. | Max 3 services in current stage. |
| `apps/frontend/` | Next.js + React + Tailwind user interface. | Consumes backend REST APIs. |
| `data/input/` | Raw, un-enriched input data files. | Preserved with `.gitkeep`; raw data ignored. |
| `data/output/` | Enriched results, exports, and verification artifacts. | Preserved with `.gitkeep`; results ignored. |
| `data/samples/` | Committed small benchmark datasets for verification. | Version-controlled sample files. |
| `docs/` | Single canonical platform documentation hub. | Subdivided into `initial/`, `learning/`, `setup/`. |
| `infrastructure/docker/` | Docker Compose definitions and container lifecycle documentation. | All Docker Compose files live here exclusively. |

---

## 3. Backend Services & Port Mapping

The platform operates within a reserved port range of **9741–9750**:

| Port | Service | Responsibility & Boundaries |
| :--- | :--- | :--- |
| **9741** | `research-service` | Orchestrates research requests (`POST /api/v1/research`), seed URL canonicalization, external web source discovery, polite HTTP retrieval, and evidence tuple compilation. **Boundary:** Strictly stateless; persists to `dataset-service` via non-blocking REST adapter. |
| **9742** | `ai-intelligent-service` | Manages Spring AI Google GenAI integrations (`ChatClient`), prompt execution, tool/function calling, structured schema extraction, and entity scoring. **Boundary:** Strictly internal AI reasoning engine. |
| **9743** | `dataset-service` | Primary persistence boundary. Handles relational entity storage (MySQL via Spring Data JPA), tabular file ingestion (CSV/TSV/JSON), query APIs, and export generation. |

### Research Service Internal Architecture

The `research-service` follows a modular, single-responsibility architecture where `DefaultResearchService` acts strictly as a thin use-case facade:

```text
com.subdual.research_service/
├── validation/     # ResearchRequestValidator: ensures target presence and valid HTTP/HTTPS URLs
├── normalization/  # EntityNormalizer & DefaultEntityNormalizer: canonical URL & SHA-256 ID generation
├── discovery/      # QueryBuilder & ResearchDiscoveryService: search provider abstraction & query generation
├── processing/     # SourceProcessor, SourceClassifier, RelevanceEvaluator: ranking & deduplication
├── extraction/     # ContentExtractor, EntityResolver, EvidenceExtractor, SourceEvidenceService: grounded facts
├── persistence/    # ResearchSnapshotPersister & RestDatasetPersistenceClient: resilient, non-blocking storage
├── orchestration/  # ResearchContext, ResearchExecutionTimer, ResearchPipeline: stage coordination
├── response/       # ResearchResponseFactory: DTO mapping, metadata, warnings, and COMPLETED/PARTIAL status
├── diagnostics/    # ResearchDiagnostics: tracks skipped sources, upstream warnings, degraded states
├── service/        # DefaultResearchService (thin facade) & InMemoryResearchJobService (async pool)
├── controller/     # ResearchController: REST endpoints, HTTP content negotiation, ProblemDetail mapping
└── client/         # External HTTP clients and Null Object fallback implementations
```

### Technology Baseline

* **Language:** Java 25
* **Framework:** Spring Boot 4.1.1
* **Build System:** Apache Maven (with committed `mvnw` wrappers)
* **Database:** MySQL 8.0+
* **AI Integration:** Spring AI (Google GenAI Starter v2.0.1)
* **Frontend:** Next.js 15+ (App Router), TypeScript, Tailwind CSS

---

## 4. Docker Compose Responsibilities

All container orchestration files are located exclusively under `infrastructure/docker/`:

* **`docker-compose-dev-all.yml`**: The primary development Compose file. Launches MySQL, all three backend Spring Boot services, and the Next.js frontend with Docker Compose Watch enabled (`action: sync+restart` for backend Java code and `action: sync` for frontend assets).
* **`docker-compose-dev.yml`**: Launches only the core infrastructure (MySQL database) for developers running services natively via `./mvnw spring-boot:run` or IDEs.
* **`commands.md`**: Complete operational command reference for starting, watching, logging, rebuilding, and cleaning Docker containers.

---

## 5. Environment Configuration Strategy

1. **Central Templates:**
   * `.env.example` at project root.
   * `infrastructure/docker/.env.example` inside the Docker infrastructure directory.
2. **Local Overrides:**
   * Active values are placed in `.env` (at root or in `infrastructure/docker/`), which are strictly ignored by Git.
   * Spring Boot `application.yaml` files specify sensible defaults for local development (e.g. `mock` search provider, localhost ports) so services can boot without mandatory manual configuration.
3. **Zero Secrets in Repository:**
   * Real API keys, passwords, and tokens are never committed.
   * The `apps/` directory does not contain loose `.env` files; configuration flows through Docker environment variables or service-level configuration beans.

---

## 6. Generated Artifacts & Clean Repository Rules

The following directories represent generated build, dependency, or cache artifacts and must never be tracked by Git:

* `target/` (Maven build output)
* `node_modules/` (Node dependencies)
* `.next/` (Next.js compilation cache)
* `dist/` & `build/` (Distribution bundles)
* `coverage/` (Test coverage reports)
* `.mvn/wrapper/maven-wrapper.jar` (Binary wrapper jar; properties file is retained)

---

## 7. Explicitly Deferred Architectural Scope

To prevent premature complexity, the following are intentionally **NOT** present in the current stage:

* **No Distributed Message Broker:** No Kafka, RabbitMQ, or active message mesh. In-memory execution is sufficient for single-entity and small batch proof-of-concepts.
* **No Headless Browser Automation:** No Puppeteer or Playwright. Polite HTTP clients handle public documentation and SERP endpoints.
* **No Vector Database:** No Pinecone, Milvus, or Qdrant until semantic similarity retrieval is strictly justified.
* **No Premature Microservices:** The platform consists of at most three backend services (`research-service`, `ai-intelligent-service`, `dataset-service`). No API Gateway, Eureka discovery server, or auth proxy will be created at this stage.
