# Data Enrichment & Research Engine

A domain-agnostic entity enrichment and intelligence platform built on the **Java + Spring Boot + Spring AI** ecosystem.

---

## 1. What Is This?

The **Data Enrichment & Research Engine** transforms tabular datasets of sparse entity records (e.g., CSV, TSV, JSON) into rich, verified, structured, and AI-scored data assets. It treats input URLs and identifiers as research seeds, discovers corroborating facts from legitimate public sources, enforces zero-hallucination evidence extraction, and applies configurable AI evaluation.

---

## 2. What Problem Does It Solve?

Input datasets frequently contain minimal surface-level information (name, URL, sparse role or company). Manually researching hundreds or thousands of records across the web is labor-intensive, slow, and unscalable. Simple string filters fail to evaluate qualitative relevance or synthesize evidence across disparate public sources. This platform automates the research, extraction, normalization, and evaluation process with full source provenance.

---

## 3. High-Level Approach

```text
Sparse Input (CSV/JSON)
         │
         ▼
Deterministic Normalization & Canonical ID
         │
         ▼
Autonomous Web Research & Source Discovery
         │
         ▼
Evidence Tuple Compilation (Verbatim Snippet + URL + Confidence)
         │
         ▼
Spring AI Structured Extraction
         │
         ▼
Configurable AI Scoring & Classification
         │
         ▼
Human-in-the-Loop Review
         │
         ▼
Clean Export (CSV / JSON / DB)
```

---

## 4. Current Technology Direction

* **Language**: Java 25
* **Backend Framework**: Spring Boot 4.x
* **AI Framework**: Spring AI (Google GenAI)
* **Frontend**: Next.js + React + TypeScript + Tailwind CSS
* **Containerization**: Docker, Docker Compose (minimal local dev environment)

---

## 5. Project Structure

```text
data-enrichment-ai-intelligence/
│
├── .github/                        # CI workflows and repository automation
├── apps/
│   ├── backend/                    # Spring Boot microservices
│   │   ├── research-service/       # Research orchestration & retrieval (Port 9741)
│   │   ├── ai-intelligent-service/ # Spring AI model interaction & extraction (Port 9742)
│   │   └── dataset-service/        # Dataset ingestion & MySQL persistence boundary (Port 9743)
│   │
│   └── frontend/                   # Next.js + TypeScript frontend application (Port 3000)
│
├── data/
│   ├── input/                      # Staging raw input datasets (CSV, JSON)
│   ├── output/                     # Enriched and evaluated output datasets
│   └── samples/                    # Sample benchmark files (e.g., sample connection exports)
│
├── docs/
│   ├── initial/                    # Preserved foundation definitions & architecture
│   │   ├── problem.md              # Problem definition, scope, and reality constraints
│   │   ├── architecture.md         # Architecture blueprint, Spring stack, structure, ADRs
│   │   └── enrichment.md           # Research engine, tool calling, evidence schema, limitations
│   ├── learning/                   # Technical concept guides & web standards
│   │   ├── concepts-01-10.md       # Web & retrieval foundations
│   │   ├── concepts-11-20.md       # AI research & evidence extraction
│   │   ├── concepts-21-30.md       # Spring implementation & reliability
│   │   └── http-media-type-negotiation.md # Content negotiation guards
│   └── setup/                      # Technical setup, architecture, & pipeline workflows
│       ├── project-structure.md    # Canonical directory & service responsibilities
│       ├── research-workflow.md    # Pipeline roadmap & Phase 1 discovery specification
│       ├── entity-model.md         # Generic domain and entity definitions
│       └── api-design.md           # Minimal HTTP API surface and specifications
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
├── .gitignore                      # Git ignore definitions
└── README.md                       # Project summary and documentation index
```

---

---

## 6. Current Status & Verification

* **Status**: Complete & Production-Hardened (Phases 1–8 verified)
* **Architecture**: 3 Modular Spring Boot Microservices (`research-service` :9741, `ai-intelligent-service` :9742, `dataset-service` :9743) + Next.js App Router Frontend (:3000) + MySQL (:3306).
* **Core Capabilities**:
  - Deterministic entity normalization, tracking parameter stripping, and canonical SHA-256 ID generation.
  - Multi-source search discovery (Tavily provider + Mock fallback) with polite web scraping and bounded HTTP guardrails.
  - LLM extraction via Google Gemini with deterministic heuristic fallback.
  - Multi-source evidence corroboration engine with source agreement confidence boosting and conflict tracking.
  - Diagnostic warning collection and partial failure resiliency.
  - Bounded async research job execution (`ThreadPoolExecutor`) with non-blocking status polling.
  - Relational MySQL persistence with index optimizations and paginated catalog exploration.
  - Rich interactive frontend with dual async/sync execution modes, confidence badges, exact evidence quotes, and catalog inspector.

---

## 7. Quick Start

### 1. Environment Configuration
Copy the template to create your `.env` file:
```bash
cp .env.example .env
```
*(Optional: Add your `SEARCH_PROVIDER_API_KEY` for Tavily or `GEMINI_API_KEY` for live Google Gemini).*

### 2. Start Services via Docker Compose
To start the full stack (MySQL, all 3 backend services, and the Next.js frontend):
```bash
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d
```
Or start only MySQL if running services locally from your IDE / terminal:
```bash
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d
```

### 3. Service Endpoints
* **Web UI**: [http://localhost:3000](http://localhost:3000)
* **Research Service**: [http://localhost:9741](http://localhost:9741)
* **AI Intelligent Service**: [http://localhost:9742](http://localhost:9742)
* **Dataset Service**: [http://localhost:9743](http://localhost:9743)
* **MySQL Database**: `localhost:3306` (database: `enrichment_db`, user: `enrichment_user`)

---

## 8. Documentation Index

The canonical platform documentation is centrally organized under `docs/`:

### Technical Setup & Architecture
* 🏛️ **[Project Structure & Service Responsibilities](docs/setup/project-structure.md)**: Canonical directory ownership, backend service boundaries, port mapping, and environment strategies.
* 🚀 **[Research Workflow & Target Pipeline](docs/setup/research-workflow.md)**: Production-oriented technical roadmap for discovery, retrieval, and enrichment.
* 📐 **[Entity Model](docs/setup/entity-model.md)**: Generic domain concepts, evidence tuples, multi-source corroboration, and relational schema.
* 🔌 **[API Design](docs/setup/api-design.md)**: Complete HTTP API specification across all microservices (sync, async jobs, entities, and extraction).
* 📮 **[Postman Collection & CLI Guide](postman/README.md)**: Comprehensive Postman collection and Newman runner guide.

### Foundations & Architecture
* 📄 **[Problem Statement & Scope](docs/initial/problem.md)**: Problem analysis, generic entity goals, scope boundaries, and reality constraints.
* 🏗️ **[Architecture & Decisions](docs/initial/architecture.md)**: System design, Spring ecosystem direction, authoritative structure, and ADRs.
* 🔍 **[Enrichment & Research Engine](docs/initial/enrichment.md)**: Autonomous research engine, Spring AI tool calling, evidence schema, and limitations.

### Engineering & Learning Guides
* 🌐 **[01. HTTP API Design & Media Types](docs/learning/01-http-api-design-and-media-types.md)**: Content negotiation guards, `consumes`/`produces`, and RFC 7807 ProblemDetail error handling.
* 🧩 **[02. Spring Dependency Injection & Boundaries](docs/learning/02-spring-dependency-injection-and-boundaries.md)**: Constructor injection, thin controllers, and domain separation.
* 📐 **[03. Service Abstraction & SOLID](docs/learning/03-service-abstraction-and-solid.md)**: Single responsibility decomposition, open/closed extension, and dependency inversion.
* 🔌 **[04. Strategy & Adapter Patterns](docs/learning/04-strategy-and-adapter-patterns-in-provider-integrations.md)**: SearchProvider abstraction, Tavily integration, Mock offline fallbacks, and backward compatibility.
* ⏱️ **[05. Async Job Lifecycle & Thread Pooling](docs/learning/05-async-job-lifecycle-and-thread-pooling.md)**: State machine, bounded ThreadPoolExecutor, and MDC logging.
* 🧠 **[06. Evidence-Grounded AI Extraction](docs/learning/06-evidence-grounded-ai-extraction.md)**: Zero-hallucination fact verification, quote citations, and confidence scoring.
* 🗄️ **[07. Transactional Persistence & Idempotency](docs/learning/07-transactional-persistence-and-idempotency.md)**: Flyway versioned migrations, orphan removal, and idempotent upserts.



