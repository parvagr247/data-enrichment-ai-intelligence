# Backend

> Status: Active
> Version: 0.1
> Last Updated: 2026-09-04

---

## 1. Backend Overview

The backend consists of up to three Spring Boot services.

### Current Services:
1. **Research Service**: Orchestrates the entity research and retrieval workflow.
2. **AI Intelligent Service**: Handles AI reasoning, Spring AI integration, and structured extraction.
3. **Dataset Service**: Manages dataset ingestion, entity persistence, and exports.

### Conceptual Architecture:
```text
Client
  │
  ▼
Research Service (9741)
  │
  ├──► AI Intelligent Service (9742)
  │
  └──► Dataset Service (9743)
  │
  ▼
External / Public Sources
```

*Note: Communication patterns remain conceptual at this stage. Inter-service protocols will be implemented when required by workflow orchestration.*

---

## 2. Technology Baseline

| Technology | Current Choice |
| :--- | :--- |
| Language | Java 25 |
| Framework | Spring Boot 4.1.1 |
| Build | Maven (with Maven Wrapper) |
| Database | MySQL |
| AI Framework | Spring AI (Google GenAI Starter 2.0.1) |
| API Style | HTTP / REST |
| Frontend | Next.js + TypeScript + Tailwind CSS |

---

## 3. Service Port Mapping

The backend operates within a reserved port range of **9741–9750**:

| Port | Service | Status |
| :--- | :--- | :--- |
| **9741** | `research-service` | CURRENT |
| **9742** | `ai-intelligent-service` | CURRENT |
| **9743** | `dataset-service` | CURRENT |
| 9744 | Reserved | FUTURE |
| 9745 | Reserved | FUTURE |
| 9746 | Reserved | FUTURE |
| 9747 | Reserved | FUTURE |
| 9748 | Reserved | FUTURE |
| 9749 | Reserved | FUTURE |
| 9750 | Reserved | FUTURE |

### Active Port Configurations:
* `research-service`: `server.port=9741`
* `ai-intelligent-service`: `server.port=9742`
* `dataset-service`: `server.port=9743`

---

## 4. Research Service

* **Name**: `research-service`
* **Port**: `9741`
* **Purpose**: Responsible for the research/retrieval workflow.
* **Intended Responsibilities**:
  * Expose the core research HTTP API (`POST /api/v1/research`).
  * Research orchestration across retrieval and analysis phases.
  * Seed URL processing and normalisation.
  * External web source discovery and content retrieval.
  * Evidence collection and compilation into factual evidence tuples.
* **Dependencies**:
  * Spring Web (`spring-boot-starter-webmvc`)
  * Validation (`spring-boot-starter-validation`)
  * Spring Boot Actuator (`spring-boot-starter-actuator`)
  * Spring Boot DevTools (development scope)
  * Project Lombok
* **Boundaries**: Does not connect directly to MySQL or contain persistence logic.
* **Roadmap**: Refer to [Research Workflow & Target Pipeline](docs/RESEARCH_WORKFLOW.md) for the phased implementation roadmap from initial scaffold to web discovery and enrichment.

---

## 5. AI Intelligent Service

* **Name**: `ai-intelligent-service`
* **Port**: `9742`
* **Purpose**: Responsible for AI reasoning and structured extraction.
* **Intended Responsibilities**:
  * Spring AI `ChatClient` abstraction and model interaction.
  * Tool / function calling execution.
  * Research-agent reasoning and decision making.
  * Structured schema extraction from unstructured text.
  * Evidence-grounded entity enrichment and scoring.
* **Dependencies**:
  * Spring Web (`spring-boot-starter-webmvc`)
  * Validation (`spring-boot-starter-validation`)
  * Spring Boot Actuator (`spring-boot-starter-actuator`)
  * Spring AI Google GenAI Starter (`spring-ai-starter-model-google-genai` v2.0.1)
  * Spring Boot DevTools (development scope)
  * Project Lombok
* **Tool Calling Flow**:
  ```text
  LLM
    │
    │ requests tool
    ▼
  Application
    │
    │ executes tool
    ▼
  Tool result
    │
    ▼
  LLM
  ```
  *The AI model does not have direct unmanaged web access; tools are strictly executed within the application boundary.*

---

## 6. Dataset Service

* **Name**: `dataset-service`
* **Port**: `9743`
* **Purpose**: Responsible for dataset ingestion, persistence, and future structured dataset management.
* **Intended Responsibilities**:
  * Serves as the primary persistence boundary for the platform.
  * Tabular dataset ingestion (CSV, TSV, JSON).
  * Storage and retrieval of canonical entity records.
  * Export generation.
* **Dependencies**:
  * Spring Web (`spring-boot-starter-webmvc`)
  * Spring Data JPA (`spring-boot-starter-data-jpa`)
  * MySQL Connector/J (`mysql-connector-j`)
  * Validation (`spring-boot-starter-validation`)
  * Spring Boot Actuator (`spring-boot-starter-actuator`)
  * Spring Boot DevTools (development scope)
  * Project Lombok

---

## 7. Dependency Matrix

| Dependency | Research Service | AI Intelligent Service | Dataset Service |
| :--- | :---: | :---: | :---: |
| Spring Web (`webmvc`) | ✓ | ✓ | ✓ |
| Validation | ✓ | ✓ | ✓ |
| Actuator | ✓ | ✓ | ✓ |
| DevTools | ✓ | ✓ | ✓ |
| Spring AI (Google GenAI) | — | ✓ | — |
| Spring Data JPA | — | — | ✓ |
| MySQL Driver | — | — | ✓ |

---

## 8. Configuration & Environment

Configuration is maintained via `application.yaml` in each service.

### Database Credentials:
No credentials or secrets are committed. Database parameters are configured via environment variables:
* `SPRING_DATASOURCE_URL` (default fallback: `jdbc:mysql://localhost:3306/enrichment_db`)
* `SPRING_DATASOURCE_USERNAME` (default fallback: `root`)
* `SPRING_DATASOURCE_PASSWORD`

### Environment Variables:
A centralized configuration template is provided at `apps/.env.example`. Active local values are stored in `apps/.env` (ignored by Git):
* `apps/.env.example`: Committed template showing all frontend, backend, database, and AI environment variables.
* `apps/.env`: Active environment configuration file for local development.

---

## 9. Backend Documentation & Roadmaps

* 🚀 **[Research Workflow & Target Pipeline](docs/RESEARCH_WORKFLOW.md)**: Production-oriented technical roadmap for transitioning the Research Service from the initial API baseline into an operational web discovery, retrieval, and enrichment pipeline.
* 📚 **[Backend Documentation Index](docs/README.md)**: Index of technical specifications and architectural deep-dives.

