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

## 6. Current Status

* **Status**: Current / Phase 0 Completed &rarr; Phase 1 (Web Source Discovery)
* **Next Implementation Milestone**: Phase 1 — Web Source Discovery (discovering candidate sources and returning populated `sources[]` with metadata).
* **Technical Roadmap**: Detailed in [Research Workflow & Target Pipeline](docs/setup/research-workflow.md).

---

## 7. Documentation Index

The canonical platform documentation is centrally organized under `docs/`:

### Technical Setup & Architecture
* 🏛️ **[Project Structure & Service Responsibilities](docs/setup/project-structure.md)**: Canonical directory ownership, backend service boundaries, port mapping, and environment strategies.
* 🚀 **[Research Workflow & Target Pipeline](docs/setup/research-workflow.md)**: Production-oriented technical roadmap for turning the Research API into an active discovery, retrieval, and enrichment pipeline.
* 📐 **[Entity Model](docs/setup/entity-model.md)**: Minimal generic domain concepts (Entity, Research Request, Evidence, Enrichment Result) and in-memory vs. persistent design.
* 🔌 **[API Design](docs/setup/api-design.md)**: Minimal synchronous research API endpoint (`POST /api/v1/research`), request/response schemas, and future microservice boundaries.

### Initial Foundations
* 📄 **[Problem Statement & Scope](docs/initial/problem.md)**: Problem analysis, generic entity goals, scope boundaries, and reality constraints.
* 🏗️ **[Architecture & Decisions](docs/initial/architecture.md)**: System design, Spring ecosystem direction, authoritative project structure, roadmap, and ADRs.
* 🔍 **[Enrichment & Research Engine](docs/initial/enrichment.md)**: Autonomous research engine, Spring AI tool calling, evidence tuple pattern, confidence tiers, and research limitations.

### Learning Guides
* 🌐 **[HTTP Media Type Negotiation](docs/learning/http-media-type-negotiation.md)**: Deep dive on `consumes` and `produces` content negotiation guards and RFC 7807 error handling in Spring Boot.
* 📚 **[Concepts 01–10: Web & Retrieval Foundations](docs/learning/concepts-01-10.md)**
* 🤖 **[Concepts 11–20: AI Research & Evidence](docs/learning/concepts-11-20.md)**
* ⚙️ **[Concepts 21–30: Spring Implementation & Reliability](docs/learning/concepts-21-30.md)**

