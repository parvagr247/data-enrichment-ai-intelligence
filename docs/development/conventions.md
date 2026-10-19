# Codebase Conventions & Design Philosophy

This document outlines the architectural patterns, package organization standards, coding conventions, and best practices enforced across the Data Enrichment AI Intelligence Platform.

---

## Table of Contents
- [1. Feature-Centric Package Organization](#1-feature-centric-package-organization)
  - [1.1 Anti-Patterns Avoided](#11-anti-patterns-avoided)
  - [1.2 Target Architectural Standards](#12-target-architectural-standards)
- [2. Interface, Implementation, and Helper Pattern](#2-interface-implementation-and-helper-pattern)
- [3. Strict DTO Segregation Rule](#3-strict-dto-segregation-rule)
- [4. Coding & Framework Standards](#4-coding--framework-standards)
  - [4.1 Lombok Usage & Java 25 Preview Guardrails](#41-lombok-usage--java-25-preview-guardrails)
  - [4.2 Error Handling & Problem Details](#42-error-handling--problem-details)
  - [4.3 Threading & Bounded Concurrency](#43-threading--bounded-concurrency)
  - [4.4 Defensive Data Sanitization (SSRF Protection)](#44-defensive-data-sanitization-ssrf-protection)

---

## 1. Feature-Centric Package Organization

The platform adheres to a **simplified Low-Level Design (LLD)** packaging philosophy, eliminating deep nesting (`controller/`, `service/impl/`, `model/entity/`, `dto/request/`) in favor of high cohesion, flat hierarchies, and clear bounded contexts.

### 1.1 Anti-Patterns Avoided
* **Generic Dumping Grounds**: Never create top-level dumping-ground packages like `service/`, `util/`, `common/`, `model/`, or `dto/` that aggregate dozens of unrelated classes.
* **Premature Micro-Packaging**: Do not create sub-packages (`validator/`, `mapper/`, `factory/`) if they contain only one or two trivial classes.
* **God Services**: Avoid monolithic 1,000+ line service classes that combine business orchestration, validation, HTTP calls, and data mapping.
* **Manual GoF Singletons in Spring Beans**: Avoid manual static inner-holder singletons (`getInstance()`) inside Spring `@Service` or `@Component` beans; rely on Spring IoC container singleton lifecycle.

### 1.2 Target Architectural Standards

#### 5-Package Architecture (`dataset-service`)
```
com.subdual.dataset_service/
├── DatasetServiceApplication.java
├── dataset/                     # File ingestion, sniffing, validation, profiling
├── enrichment/                  # Batch orchestration, async processing, SSE events
├── entity/                      # JPA entities, repository layer, multi-attribute querying
├── shared/                      # Cross-cutting exceptions, correlation MDC, common models
└── configuration/               # Thread pools, security, web client beans
```

#### 5-Package Architecture (`research-service`)
```
com.subdual.research_service/
├── ResearchServiceApplication.java
├── api/                         # Public REST controller, validation, public DTOs
├── research/                    # Search, discovery, crawling, extraction, corroboration
├── integration/                 # Outbound clients (e.g. ai-intelligent-service client)
├── shared/                      # Shared exceptions, security validator, normalizers
└── configuration/               # Search provider beans, crawler configuration
```

#### 4-Package Architecture (`ai-intelligent-service`)
```
com.subdual.ai_intelligent_service/
├── AiIntelligentServiceApplication.java
├── capability/                  # Core intelligence features (extraction, planning)
├── ai/                          # Spring AI ChatModel, prompt templates, heuristic fallback
├── shared/                      # Output normalizers, response validators, exceptions
└── configuration/               # Gemini AI beans, web client configurations
```

---

## 2. Interface, Implementation, and Helper Pattern

To maintain maintainable, easily testable services:

1. **Service Interfaces (`<Name>Service`)**:
   - Kept small, intentional, and focused on 1 to 4 primary business methods.
   - Serve as clean mockable injection targets for upstream controllers or batch coordinators.
2. **Implementation (`<Name>ServiceImpl`)**:
   - Focuses strictly on business workflow orchestration and transaction management.
   - Annotated with `@Service` and `@Transactional(readOnly = true)` (or `@Transactional` on mutation methods).
   - Annotates overridden methods with `@Override` comments clarifying business intent:
     ```java
     @Override // Orchestrates multi-worker research and persistence
     public JobState submitJob(JobSubmissionRequest request) { ... }
     ```
3. **Helper Classes (`<Name>Helper`)**:
   - Encapsulate non-transactional calculations, scoring algorithms, data cleaning, or formatting (e.g., `ConfidenceScoringHelper`, `EntityResolutionHelper`, `SseBroadcastingHelper`).
   - Pure functions or stateless Spring components, making unit testing straightforward without requiring heavy Spring Context or Mockito mocks.

---

## 3. Strict DTO Segregation Rule

Never reuse Public Ingress DTOs for internal inter-service communication or JPA entity persistence.

| Category | Package Location | Purpose | Change Frequency |
| :--- | :--- | :--- | :--- |
| **Public API Request** | `<feature>.dto` / `api.dto` | Ingress payload accepted from web/SDK clients. Enforces `@NotBlank`, `@Size`, `@Valid`. | Very Low (Backwards Compatible) |
| **Public API Response** | `<feature>.dto` / `api.dto.response` | Egress JSON presented to external callers. Masks internal IDs and tokens. | Very Low (Backwards Compatible) |
| **Internal Client DTO** | `<feature>.client.dto` / `integration.<target>.dto` | Downstream REST payload exchanged with peer microservices. | Moderate (Internal Mesh Evolution) |
| **Persistence Entity** | `entity.model` / `entity` | MySQL Hibernate JPA entity. Manages table columns and relations. | Low (Schema Migrations) |

---

## 4. Coding & Framework Standards

### 4.1 Lombok Usage & Java 25 Preview Guardrails
* Use `@RequiredArgsConstructor` for standard constructor-based dependency injection. Avoid field `@Autowired`.
* Use `@Getter` and `@Setter` judiciously to eliminate boilerplate property accessors.
* **JPA Entity Warning**: **Never use `@Data` or `@EqualsAndHashCode` on JPA entities**. Generating automatic `equals` / `hashCode` on Hibernate bidirectional relations causes infinite recursive stack overflows. Use explicit ID-based equality or rely on Lombok `@Getter` / `@Setter` only.
* **Java 25 Preview Constructor Constraint (JEP 482: Flexible Constructor Bodies)**:
  When a class defines overloaded or secondary constructors (`this(...)`), do **not** use Lombok's `@RequiredArgsConstructor`. Under JDK 25 preview, Lombok's generated constructor bytecode places field initialization prior to calling explicit secondary constructors, causing JVM class verification failure: `VerifyError: Constructor must call super() or this() before return`. In classes with multiple constructor overloads (e.g. `DefaultDatasetEnrichmentService`), provide an explicit Java constructor annotated with `@Autowired`.

### 4.2 Error Handling & Problem Details
* Services throw domain-specific unchecked exceptions extending `RuntimeException` (e.g., `EntityNotFoundException`, `RateLimitExceededException`, `ValidationException`).
* A centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) intercepts exceptions and maps them to standard **RFC 7807 Problem Detail** responses containing `type`, `title`, `status`, `detail`, `code`, and `requestId`.

### 4.3 Threading & Bounded Concurrency
* Never use unbounded `Executors.newCachedThreadPool()` or direct `new Thread()`.
* Multi-row processing must execute through `EnrichmentTaskExecutor` or managed thread pools configured with:
  - Fixed core and max pool sizes (`enrichment.execution.concurrency: ${ENRICHMENT_CONCURRENCY:3}`).
  - Bounded work queues (`enrichment.execution.queue-capacity: ${ENRICHMENT_QUEUE_CAPACITY:500}`).
  - Explicit rejection policies (`CallerRunsPolicy` or custom logging discard).
* All async operations propagate the MDC context (`X-Correlation-ID`) across thread boundaries.

### 4.4 Defensive Data Sanitization (SSRF Protection)
* Any user-provided URL evaluated by crawlers or HTTP clients must pass `SecurityValidator` check:
  - Rejects `localhost`, `127.0.0.1`, `::1`.
  - Rejects private RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
  - Rejects cloud instance metadata endpoints (`169.254.169.254`).
