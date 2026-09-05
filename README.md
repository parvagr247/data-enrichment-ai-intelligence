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
data-enrichment-engine/
│
├── apps/
│   ├── backend/
│   │   ├── research-service/           # Research orchestration & retrieval (Port 9741)
│   │   ├── ai-intelligent-service/     # Spring AI model interaction & extraction (Port 9742)
│   │   ├── dataset-service/            # Dataset ingestion & MySQL persistence boundary (Port 9743)
│   │   ├── docs/                       # Backend architecture, workflows, and concept deep-dives
│   │   │   ├── RESEARCH_WORKFLOW.md    # Pipeline roadmap & Phase 1 discovery specification
│   │   │   └── concepts/               # Technical concept guides (e.g., HTTP content negotiation)
│   │   └── one.md                      # Single compact backend reference
│   │
│   └── frontend/                       # Next.js + TypeScript frontend application (Port 3000)
│
├── data/
│   ├── input/                          # Staging raw input datasets (CSV, JSON)
│   ├── output/                         # Enriched and evaluated output datasets
│   └── samples/                        # Sample benchmark files (e.g., sample connection exports)
│
├── docs/
│   ├── initial/                        # Preserved foundation definitions & architecture
│   │   ├── problem.md                  # Problem definition, scope, and reality constraints
│   │   ├── architecture.md             # Architecture blueprint, Spring stack, structure, ADRs
│   │   └── enrichment.md               # Research engine, tool calling, evidence schema, limitations
│   └── setup/                          # Pre-implementation technical setup & contracts
│       ├── entity-model.md             # Generic domain and entity definitions
│       └── api-design.md               # Minimal HTTP API surface and specifications
│
├── scripts/                            # Operational and benchmark automation scripts
│
├── docker-compose.yml                  # Minimal local development environment
├── README.md                           # Project summary and documentation index
└── .gitignore                          # Git ignore definitions
```

---

## 6. Current Status

* **Status**: Current / Phase 0 Completed (Scaffold & In-Memory Research API Operational)
* **Next Implementation Milestone**: Phase 1 — Web Source Discovery (discovering candidate sources and returning populated `sources[]` with metadata).
* **Technical Roadmap**: Detailed in [Research Workflow & Target Pipeline](apps/backend/docs/RESEARCH_WORKFLOW.md).

---

## 7. Documentation Index

The project documentation is organized into foundational definitions (`docs/initial/`), pre-implementation technical specifications (`docs/setup/`), and backend implementation roadmaps (`apps/backend/docs/`):

### Backend Implementation Roadmaps & Workflows
* 🚀 **[Research Workflow & Target Pipeline](apps/backend/docs/RESEARCH_WORKFLOW.md)**: Production-oriented technical roadmap for turning the Research API into an active discovery, retrieval, and enrichment pipeline.
* 📚 **[Backend Documentation Index](apps/backend/docs/README.md)**: Comprehensive index of backend service specifications, media type negotiation, and concept deep-dives.

### Initial Foundations
* 📄 **[Problem Statement & Scope](docs/initial/problem.md)**: Problem analysis, generic entity goals, scope boundaries, and reality constraints.
* 🏗️ **[Architecture & Decisions](docs/initial/architecture.md)**: System design, Spring ecosystem direction, authoritative project structure, roadmap, and ADRs.
* 🔍 **[Enrichment & Research Engine](docs/initial/enrichment.md)**: Autonomous research engine, Spring AI tool calling, evidence tuple pattern, confidence tiers, and research limitations.

### Technical Setup
* 📐 **[Entity Model](docs/setup/entity-model.md)**: Minimal generic domain concepts (Entity, Research Request, Evidence, Enrichment Result) and in-memory vs. persistent design.
* 🔌 **[API Design](docs/setup/api-design.md)**: Minimal synchronous research API endpoint (`POST /api/v1/research`), request/response schemas, and future microservice boundaries.
