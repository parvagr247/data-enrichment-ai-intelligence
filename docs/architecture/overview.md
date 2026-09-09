# System Overview

The **Data Enrichment AI Intelligence Platform** is a distributed, production-grade enrichment engine that transforms sparse, noisy, or unstructured entity inputs into verified, structured, requirement-aware profiles backed by verbatim evidence quotes and multi-source confidence tiers.

---

## 1. High-Level Topology & Ingress Architecture

```mermaid
flowchart TB
    subgraph External ["External Clients & Browsers"]
        Browser["User Browser / Client Application"]
    end

    subgraph HostPorts ["Exposed Host Ports (Public Ingress)"]
        Port3000["Port 3000: Web Dashboard"]
        Port9738["Port 9738: API Gateway Ingress"]
    end

    subgraph InternalNet ["Isolated Internal Network (enrichment-network)"]
        subgraph Infra ["Platform Infrastructure Layer"]
            ConfigServer["config-server (:9736)<br/>- Spring Cloud Config Server (native)<br/>- Centralized YAML in /config<br/>- Actuator Health Probes"]
            DiscoveryServer["discovery-server (:9737)<br/>- Spring Cloud Netflix Eureka<br/>- Dynamic Heartbeats & Registry<br/>- Self-Preservation Tuned"]
            ApiGateway["api-gateway (:9738)<br/>- Spring Cloud Gateway (WebMvc)<br/>- Unified Reverse Proxy & Routing<br/>- JWT Bearer Auth & Anti-Spoofing<br/>- CORS & Security Headers"]
        end

        subgraph Backend ["Microservices Layer (Spring Boot 4 / Java 25)"]
            AuthService["auth-service (:9739)<br/>- User Registration & Login<br/>- BCrypt Password Hashing<br/>- HMAC-SHA256 JWT Issuance<br/>- Flyway users Table"]
            DatasetService["dataset-service (:9743)<br/>- Batch Enrichment Jobs<br/>- Bounded Concurrency (3 Workers)<br/>- Real-Time SSE Stream (:events)<br/>- MySQL Relational Persistence<br/>- User-Scoped Data Ownership"]
            ResearchService["research-service (:9741)<br/>- Entity Identity & Normalization<br/>- Requirement-Aware Search Queries<br/>- Multi-Source Web Discovery<br/>- Polite Fetching & Cleaning<br/>- Verbatim Evidence Extraction<br/>- Multi-Source Corroboration"]
            AIService["ai-intelligent-service (:9742)<br/>- Spring AI (Google GenAI Gemini Client)<br/>- Requirement Interpretation<br/>- Input Data Cleansing<br/>- Grounded Fact Extraction<br/>- Zero-Hallucination Guardrails<br/>- Deterministic Fallback Engine"]
        end

        subgraph Storage ["Persistence Layer (Internal Port 3306)"]
            MySQL[("MySQL 8.0+<br/>- users<br/>- entities (user_id)<br/>- entity_sources<br/>- entity_attributes<br/>- flyway_schema_history")]
        end
    end

    subgraph CloudAPIs ["External Cloud Providers"]
        SearchAPI["Web Search Engine<br/>(Tavily / Mock Provider)"]
        WebPages["Discovered Web Pages / URLs"]
        LLMProvider["LLM API<br/>(Google Gemini / Vertex)"]
    end

    Browser -->|Port 3000| Port3000
    Browser -->|Port 9738 (REST / SSE)| Port9738
    Port9738 --> ApiGateway

    ApiGateway -->|Reverse Proxy /api/v1/auth/**| AuthService
    ApiGateway -->|Reverse Proxy /api/v1/enrichment/**| DatasetService
    ApiGateway -->|Reverse Proxy /api/v1/entities/**| DatasetService
    ApiGateway -->|Reverse Proxy /api/v1/research/**| ResearchService
    ApiGateway -->|Reverse Proxy /api/v1/sources/**| ResearchService
    ApiGateway -->|Reverse Proxy /api/v1/ai/**| AIService

    Backend -.->|Optional Config Fetch| ConfigServer
    Backend -.->|Heartbeat & Discovery| DiscoveryServer
    ApiGateway -.->|Heartbeat & Discovery| DiscoveryServer

    AuthService -->|JDBC JPA| MySQL
    DatasetService -->|Internal HTTP Client| ResearchService
    DatasetService -->|Internal HTTP Client| AIService
    DatasetService -->|JDBC JPA| MySQL
    ResearchService -->|Internal HTTP Client| AIService
    ResearchService -->|Internal HTTP Client| DatasetService
    ResearchService -->|HTTPS Outbound| SearchAPI
    ResearchService -->|HTTPS Outbound Fetch| WebPages
    AIService -->|HTTPS Outbound| LLMProvider
```

---

## 2. Port Map & Network Isolation

In production environments, **only two ports are bound to public host interfaces**:
* **Port 3000**: Next.js Web Frontend.
* **Port 9738**: API Gateway (Unified ingress entry point).

All internal business microservices, the configuration server, Eureka registry, and the MySQL database reside exclusively inside the private Docker network (`enrichment-network`), unreachable directly from outside the host.

| Service | Host Port | Internal Port | Network Scope | Technology | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`config-server`** | None | `9736` | Internal-only | Spring Cloud Config Server | Native file-based centralized configuration store |
| **`discovery-server`** | None | `9737` | Internal-only | Spring Cloud Netflix Eureka | Dynamic service registry and health awareness |
| **`api-gateway`** | `9738` | `9738` | **Public Host** | Spring Cloud Gateway (WebMvc) | Unified ingress, reverse proxy, JWT auth, anti-spoofing, CORS |
| **`auth-service`** | None | `9739` | Internal-only | Spring Boot 4.1.1 / Java 25 | User accounts, BCrypt passwords, HMAC-SHA256 JWT tokens |
| **`research-service`** | None | `9741` | Internal-only | Spring Boot 4.1.1 / Java 25 | Web research, multi-source scraping, corroboration |
| **`ai-intelligent-service`** | None | `9742` | Internal-only | Spring Boot 4.1.1 / Java 25 | LLM grounding, entity cleansing, schema interpretation |
| **`dataset-service`** | None | `9743` | Internal-only | Spring Boot 4.1.1 / Java 25 | Ingestion, async batch worker pools, SSE events, JPA persistence |
| **`mysql`** | None | `3306` | Internal-only | MySQL 8.0+ | Relational schema persistence and Flyway history |
| **`frontend`** | `3000` | `3000` | **Public Host** | Next.js 15 / React / Tailwind | Interactive multi-stage dataset enrichment UI |

*(Note: In local development with `docker-compose-dev-all.yml`, service ports are additionally exposed to `localhost` to allow direct curl and IDE debugging).*

---

## 3. Foundational Architectural Principles

1. **Zero Hallucination (Verbatim Grounding)**:
   Every factual attribute extracted by `ai-intelligent-service` must contain an exact `evidenceSnippet` (quote). The service programmatically verifies that this quote appears verbatim inside the source document. If not corroborated, the fact is marked `UNKNOWN`.
2. **Bounded Concurrency & Backpressure**:
   Multi-row datasets are processed through a strictly bounded `ThreadPoolTaskExecutor` (default: 3 worker threads, 500-item queue) with `CallerRunsPolicy`. This protects downstream search APIs and the database from connection exhaustion.
3. **Transparent Resilience & Deterministic Fallback**:
   External AI LLMs and live web scrapers are treated as volatile, fail-prone dependencies. If LLMs are rate-limited or offline, `ai-intelligent-service` falls back to deterministic heuristic extractors. If external scraping is blocked by anti-bot controls, `research-service` falls back to search snippets. Batch dataset jobs never abort due to single-cell failures.
4. **Idempotent Relational Persistence**:
   Entities are identified by a deterministic SHA-256 hash of their canonical identities. Re-enriching an entity overwrites prior attributes and sources within an atomic `@Transactional` boundary using JPA `orphanRemoval = true`.
5. **Strict DTO Segregation**:
   Public REST API contracts (`api/dto/request` and `api/dto/response`) are decoupled from inter-service integration communication payloads (`integration/{ai,persistence}/dto`).

---

## 4. Relational Data Model (MySQL 8.0+)

The persistence model is managed via versioned Flyway migrations in `auth-service` and `dataset-service`:

```sql
-- Users Table (auth-service V1)
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
);

-- Core Entity Table (dataset-service V1 + V3 user_id)
CREATE TABLE entities (
    entity_id VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    canonical_url VARCHAR(1024) NOT NULL,
    user_id VARCHAR(36) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entities_type (entity_type),
    INDEX idx_entities_updated (updated_at),
    INDEX idx_entities_user_id (user_id)
);

-- Discovered Sources Table (dataset-service V1)
CREATE TABLE entity_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    title VARCHAR(512),
    snippet TEXT,
    source_type VARCHAR(32) NOT NULL,
    domain VARCHAR(255),
    provider VARCHAR(64),
    relevance DOUBLE,
    retrieved_at TIMESTAMP,
    CONSTRAINT fk_source_entity FOREIGN KEY (entity_id) REFERENCES entities(entity_id) ON DELETE CASCADE,
    INDEX idx_sources_entity (entity_id)
);

-- Grounded Attributes & Evidence Table (dataset-service V1)
CREATE TABLE entity_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    attribute_key VARCHAR(128) NOT NULL,
    attribute_value TEXT NOT NULL,
    source_url VARCHAR(1024),
    evidence_snippet TEXT,
    confidence VARCHAR(32) NOT NULL,
    CONSTRAINT fk_attribute_entity FOREIGN KEY (entity_id) REFERENCES entities(entity_id) ON DELETE CASCADE,
    INDEX idx_attributes_entity (entity_id),
    INDEX idx_attributes_key (attribute_key)
);
```

---

## 5. Technology Stack Summary

* **Language & Runtime**: Java 25 (OpenJDK 25) with preview features enabled, utilizing records, pattern matching, and virtual threads.
* **Framework**: Spring Boot 4.1.1, Spring Framework 7.0.9, Spring Cloud 2025.1.0-M2.
* **AI Integration**: Spring AI 1.1.2 with Google GenAI Gemini integration (`gemini-3.5-flash-lite`).
* **Database & Migration**: MySQL 8.0+, Spring Data JPA (Hibernate 7), Flyway Database Migrations.
* **Web Frontend**: Next.js 15 (App Router), React 19, Tailwind CSS, Lucide Icons, SheetJS (`xlsx`).
* **Containerization & Cloud**: Docker Compose v2, Google Cloud Platform (Compute Engine VM, Artifact Registry).
