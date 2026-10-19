# Development & Engineering Standards

This document outlines local environment setup, Low-Level Design (LLD) architectural standards, coding conventions, and automated testing workflows for the platform.

---

## Table of Contents
- [1. Prerequisites & Local Environment Setup](#1-prerequisites--local-environment-setup)
- [2. Low-Level Design (LLD) & Package Architecture](#2-low-level-design-lld--package-architecture)
  - [2.1 The 5-Package Architecture (dataset-service & research-service)](#21-the-5-package-architecture-dataset-service--research-service)
  - [2.2 The 4-Package Architecture (ai-intelligent-service)](#22-the-4-package-architecture-ai-intelligent-service)
  - [2.3 Interface, Implementation, and Helper Pattern](#23-interface-implementation-and-helper-pattern)
- [3. Coding Conventions & Guardrails](#3-coding-conventions--guardrails)
  - [3.1 Lombok Usage & Java 25 Preview Guardrails](#31-lombok-usage--java-25-preview-guardrails)
  - [3.2 Strict DTO Segregation Rule](#32-strict-dto-segregation-rule)
  - [3.3 Defensive SSRF Sanitization](#33-defensive-ssrf-sanitization)
- [4. Automated Testing & Verification](#4-automated-testing--verification)
  - [4.1 Running Test Suites](#41-running-test-suites)
  - [4.2 Key Test Focus Areas](#42-key-test-focus-areas)
  - [4.3 Health & Liveness Actuator Probes](#43-health--liveness-actuator-probes)

---

## 1. Prerequisites & Local Environment Setup

* **Java Development Kit (JDK)**: OpenJDK 25 with `--enable-preview` flags.
* **Build Tool**: Apache Maven 3.9+ (or included `./mvnw` wrapper).
* **Node.js**: Node.js 22 LTS with `npm` (for Next.js frontend).
* **Container Runtime**: Docker Desktop or Docker Engine with Docker Compose v2.20+.
* **Database**: MySQL 8.0+ (run locally or via `docker compose -f infrastructure/docker/docker-compose-dev.yml up -d`).

### Quick Start Workflow
```bash
# 1. Start MySQL database container
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d

# 2. Run backend microservices in separate terminals
cd apps/backend/discovery-server && ./mvnw spring-boot:run
cd apps/backend/config-server && ./mvnw spring-boot:run
cd apps/backend/auth-service && ./mvnw spring-boot:run
cd apps/backend/research-service && ./mvnw spring-boot:run
cd apps/backend/ai-intelligent-service && ./mvnw spring-boot:run
cd apps/backend/dataset-service && ./mvnw spring-boot:run
cd apps/backend/api-gateway && ./mvnw spring-boot:run

# 3. Start Next.js frontend
cd apps/frontend && npm install && npm run dev
```

---

## 2. Low-Level Design (LLD) & Package Architecture

The platform adheres to a simplified LLD packaging philosophy, eliminating deep nesting (`controller/`, `service/impl/`, `model/entity/`, `dto/request/`) in favor of high cohesion, flat hierarchies, and clear bounded contexts.

### 2.1 The 5-Package Architecture (`dataset-service` & `research-service`)
```
com.subdual.dataset_service/
├── dataset/                     # File ingestion, sniffing, validation, profiling
├── enrichment/                  # Batch orchestration, async processing, SSE events
├── entity/                      # JPA entities, repository layer, multi-attribute querying
├── shared/                      # Cross-cutting exceptions, correlation MDC, common models
└── configuration/               # Thread pools, security, web client beans

com.subdual.research_service/
├── api/                         # Public REST controller, validation, public DTOs
├── research/                    # Search, discovery, crawling, extraction, corroboration
├── integration/                 # Outbound clients (e.g. ai-intelligent-service client)
├── shared/                      # Shared exceptions, security validator, normalizers
└── configuration/               # Search provider beans, crawler configuration
```

### 2.2 The 4-Package Architecture (`ai-intelligent-service`)
```
com.subdual.ai_intelligent_service/
├── capability/                  # Core intelligence features (extraction, planning)
├── ai/                          # Spring AI ChatModel, prompt templates, heuristic fallback
├── shared/                      # Output normalizers, response validators, exceptions
└── configuration/               # Gemini AI beans, web client configurations
```

### 2.3 Interface, Implementation, and Helper Pattern
1. **Service Interfaces (`<Name>Service`)**: Kept small, intentional, and focused on 1 to 4 primary business methods. Mockable targets for controllers and coordinators.
2. **Implementation (`Default<Name>Service`)**: Focuses strictly on business orchestration and transaction boundaries (`@Transactional`).
3. **Helper Classes (`<Name>Helper`)**: Encapsulate non-transactional calculations, scoring algorithms, and data transformations as pure or stateless components.
4. **No Manual GoF Singletons**: Avoid manual static inner-holder patterns (`getInstance()`) inside Spring `@Service` or `@Component` beans; rely on Spring IoC container singleton lifecycle.

---

## 3. Coding Conventions & Guardrails

### 3.1 Lombok Usage & Java 25 Preview Guardrails
* Use `@RequiredArgsConstructor` for standard constructor-based dependency injection. Avoid field `@Autowired`.
* Use `@Getter` and `@Setter` judiciously to eliminate boilerplate property accessors.
* **JPA Entity Warning**: **Never use `@Data` or `@EqualsAndHashCode` on JPA entities**. Generating automatic `equals` / `hashCode` on Hibernate bidirectional relations causes infinite recursive stack overflows.
* **Java 25 Preview Constructor Constraint (JEP 482)**:
  When a class defines overloaded or secondary constructors (`this(...)`), do **not** use Lombok's `@RequiredArgsConstructor`. Under JDK 25 preview, Lombok's generated constructor bytecode places field initialization prior to calling explicit secondary constructors, causing JVM class verification failure: `VerifyError: Constructor must call super() or this() before return`. In classes with multiple constructor overloads (e.g. `DefaultDatasetEnrichmentService`), provide an explicit Java constructor annotated with `@Autowired`.

### 3.2 Strict DTO Segregation Rule
Never reuse Public Ingress DTOs for internal inter-service communication or JPA entity persistence:

| Category | Package Location | Purpose | Change Frequency |
| :--- | :--- | :--- | :--- |
| **Public API Request** | `<feature>.dto` / `api.dto` | Ingress payload accepted from web/SDK clients. Enforces `@NotBlank`, `@Size`, `@Valid`. | Very Low (Backwards Compatible) |
| **Public API Response** | `<feature>.dto` / `api.dto.response` | Egress JSON presented to external callers. Masks internal IDs and tokens. | Very Low (Backwards Compatible) |
| **Internal Client DTO** | `<feature>.client.dto` / `integration.<target>.dto` | Downstream REST payload exchanged with peer microservices. | Moderate (Internal Mesh Evolution) |
| **Persistence Entity** | `entity.model` / `entity` | MySQL Hibernate JPA entity. Manages table columns and relations. | Low (Schema Migrations) |

### 3.3 Defensive SSRF Sanitization
Any user-provided URL evaluated by crawlers or HTTP clients must pass `SecurityValidator` check:
* Rejects `localhost`, `127.0.0.1`, `::1`.
* Rejects private RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
* Rejects cloud instance metadata endpoints (`169.254.169.254`).

---

## 4. Automated Testing & Verification

### 4.1 Running Test Suites
```bash
# Test AI Intelligent Service (30 tests)
cd apps/backend/ai-intelligent-service && mvn test

# Test Research Service (165 tests)
cd apps/backend/research-service && mvn test

# Test Dataset Service (36 tests)
cd apps/backend/dataset-service && mvn test

# Test Auth Service
cd apps/backend/auth-service && mvn test
```

### 4.2 Key Test Focus Areas
* **`dataset-service`**:
  - `DatasetIngestionControllerTest`: Multipart upload, delimiter sniffing, schema profiling endpoints.
  - `DatasetIngestionServiceTest`: Header normalization, column role heuristics, completeness metrics.
  - `DatasetEnrichmentServiceTest`: Batch enrichment orchestration, `EnrichmentTaskExecutor` concurrency, asynchronous execution, SSE event broadcasting.
  - `EntityControllerTest`: Persisted entity querying, multi-attribute retrieval, pagination.
  - `UserIsolationAndOwnershipTest`: Multi-tenant ownership verification (`X-User-Id`), ensuring cross-tenant isolation and 403/404 responses.
* **`research-service`**:
  - `ResearchControllerTest`: Synchronous and asynchronous research endpoints (`/api/v1/research`, `/api/v1/research/jobs`).
  - `ResearchOrchestratorTest`: Multi-stage pipeline execution (query formulation, search discovery, web fetching, evidence extraction, corroboration).
  - `DefaultWebContentFetcherTest`: SSRF filtering, timeouts, size limits, HTML boilerplate stripping.
  - `EntityAccuracyAndSourceQualityTest`: Multi-source corroboration, conflict penalties, confidence score calculations.
* **`ai-intelligent-service`**:
  - `ExtractionControllerTest`: Fact extraction (`/api/v1/ai/extract`), structured enrichment (`/api/v1/ai/enrich`), requirement planning (`/api/v2/ai/requirements/plan`).
  - `EnrichmentAiServiceTest`: Spring AI Gemini model interaction, prompt rendering, verbatim quote grounding, deterministic fallback.
  - `EnrichmentPlanningServiceTest`: Requirements parsing, field objective formulation, profile assessment.
  - `PromptTemplateServiceTest`: Structured prompt rendering with strict JSON output schemas.

### 4.3 Health & Liveness Actuator Probes
Every Spring Boot service exposes standard Actuator health probes:
* `http://localhost:9738/actuator/health` (Gateway)
* `http://localhost:9739/actuator/health` (Auth Service)
* `http://localhost:9741/actuator/health` (Research Service)
* `http://localhost:9742/actuator/health` (AI Intelligent Service)
* `http://localhost:9743/actuator/health` (Dataset Service)
