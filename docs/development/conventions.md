# Codebase Conventions & Design Philosophy

This document outlines the architectural patterns, package organization standards, coding conventions, and best practices enforced across the Data Enrichment AI Intelligence Platform.

---

## 1. Feature-Centric Package Organization

The platform adheres to a **feature/domain-oriented packaging philosophy**, established across `dataset-service`, `ai-intelligent-service`, and `research-service`.

### 1.1 Anti-Patterns Avoided
* **Generic Dumping Grounds**: Never create top-level dumping-ground packages like `service/`, `util/`, `common/`, `model/`, or `dto/` that aggregate dozens of unrelated classes.
* **Premature Micro-Packaging**: Do not create sub-packages (`validator/`, `mapper/`, `factory/`) if they contain only one or two trivial classes.
* **God Services**: Avoid monolithic 1,000+ line service classes that combine business orchestration, validation, HTTP calls, and data mapping.

### 1.2 Target Structure Standard
Group classes by cohesive business capabilities:

```
com.subdual.<service>/
├── api/                          # Public HTTP ingress layer
│   ├── <Feature>Controller.java
│   └── dto/
│       ├── request/              # Ingress request contracts (JSR-380 validated)
│       └── response/             # Public egress response models
├── <feature_a>/                  # Feature/Domain package
│   ├── <Feature>Service.java     # Focused public interface
│   ├── <Feature>ServiceImpl.java # Concrete orchestrator
│   └── <Feature>Helper.java      # Auxiliary algorithms / transformation
├── <feature_b>/
├── integration/                  # Outbound downstream integrations
│   ├── <service_name>/
│   │   ├── <Service>Client.java
│   │   └── dto/                  # Internal downstream data transfer objects
│   └── persistence/
└── config/                       # Spring configuration beans & security
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
| **Public API Request** | `api.dto.request` | Ingress payload accepted from web/SDK clients. Enforces `@NotBlank`, `@Size`, `@Valid`. | Very Low (Backwards Compatible) |
| **Public API Response** | `api.dto.response` | Egress JSON presented to external callers. Masks internal IDs and tokens. | Very Low (Backwards Compatible) |
| **Internal Client DTO** | `integration.<service>.dto` | Downstream REST payload exchanged with peer microservices. | Moderate (Internal Mesh Evolution) |
| **Persistence Entity** | `<feature>.entity` | MySQL Hibernate JPA entity. Manages table columns and relations. | Low (Schema Migrations) |

---

## 4. Coding & Framework Standards

### 4.1 Lombok Usage
* Use `@RequiredArgsConstructor` for constructor-based dependency injection. Avoid `@Autowired` on private fields.
* Use `@Getter` and `@Setter` judiciously.
* **JPA Entity Warning**: **Never use `@Data` or `@EqualsAndHashCode` on JPA entities**. Generating automatic `equals` / `hashCode` on Hibernate bidirectional relations causes infinite recursive stack overflows. Use explicit ID-based equality or rely on Lombok `@Getter` / `@Setter` only.

### 4.2 Error Handling & Problem Details
* Services throw domain-specific unchecked exceptions extending `RuntimeException` (e.g., `EntityNotFoundException`, `RateLimitExceededException`, `ValidationException`).
* A centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) intercepts exceptions and maps them to standard **RFC 7807 Problem Detail** responses containing `type`, `title`, `status`, `detail`, `code`, and `requestId`.

### 4.3 Threading & Bounded Concurrency
* Never use unbounded `Executors.newCachedThreadPool()` or direct `new Thread()`.
* Multi-row processing must execute through `BoundedExecutorService` or custom thread pools configured with:
  - Fixed core and max pool sizes (`enrichment.concurrency.workers`).
  - Fixed capacity work queues.
  - Explicit rejection policies (`CallerRunsPolicy` or custom logging discard).
* All async operations propagate the MDC context (`X-Correlation-ID`) across thread boundaries.

### 4.4 Defensive Data Sanitization (SSRF Protection)
* Any user-provided URL evaluated by crawlers or HTTP clients must pass `SecurityValidator` check:
  - Rejects `localhost`, `127.0.0.1`, `::1`.
  - Rejects private RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
  - Rejects cloud instance metadata endpoints (`169.254.169.254`).
