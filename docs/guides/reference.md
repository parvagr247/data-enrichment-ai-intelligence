# Configuration Reference & Domain Glossary

This document serves as the unified reference for all environment variables, Spring Boot configuration properties, and platform domain terminology across the microservices mesh.

---

## Table of Contents
- [1. Environment Variables Matrix](#1-environment-variables-matrix)
- [2. Spring Boot Properties by Service](#2-spring-boot-properties-by-service)
  - [2.1 API Gateway (api-gateway)](#21-api-gateway-api-gateway)
  - [2.2 Auth Service (auth-service)](#22-auth-service-auth-service)
  - [2.3 Dataset Service (dataset-service)](#23-dataset-service-dataset-service)
  - [2.4 Research Service (research-service)](#24-research-service-research-service)
  - [2.5 AI Intelligent Service (ai-intelligent-service)](#25-ai-intelligent-service-ai-intelligent-service)
  - [2.6 Config Server (config-server)](#26-config-server-config-server)
  - [2.7 Discovery Server (discovery-server)](#27-discovery-server-discovery-server)
- [3. Domain Glossary & Terminology](#3-domain-glossary--terminology)

---

## 1. Environment Variables Matrix

| Environment Variable | Target Service(s) | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `SERVER_PORT` | All backend services | Service specific (see below) | HTTP port the microservice binds to |
| `SPRING_PROFILES_ACTIVE` | All backend services | `dev` | Active Spring profile (`dev`, `prod`, `docker`) |
| `SPRING_DATASOURCE_URL` | `dataset-service`, `auth-service` | `jdbc:mysql://localhost:3306/enrichment_db...` | JDBC connection string |
| `SPRING_DATASOURCE_USERNAME`| `dataset-service`, `auth-service` | `enrichment_user` | MySQL user name |
| `SPRING_DATASOURCE_PASSWORD`| `dataset-service`, `auth-service` | `enrichment_password` | MySQL user password |
| `JWT_SECRET` | `api-gateway`, `auth-service` | *(Required in prod)* | HMAC-SHA256 signing secret (min 256 bits) |
| `JWT_EXPIRATION_MS` | `auth-service` | `86400000` (24h) | Token validity duration in milliseconds |
| `GATEWAY_API_KEY` | `api-gateway` | `""` (disabled) | Optional static API key checked via `X-API-Key` |
| `GATEWAY_CORS_ALLOWED_ORIGINS`| `api-gateway` | `http://localhost:3000` | Comma-delimited CORS allowed origin URLs |
| `GEMINI_API_KEY` | `ai-intelligent-service` | `""` (activates heuristic fallback) | Google AI Studio API key |
| `SEARCH_PROVIDER_NAME` | `research-service` | `tavily` | Search provider engine (`tavily`, `mock`) |
| `SEARCH_PROVIDER_API_KEY` | `research-service` | `""` | Search API credentials (required for Tavily) |
| `ENRICHMENT_CONCURRENCY` | `dataset-service` | `3` | Worker thread pool core/max size per job |
| `ENRICHMENT_QUEUE_CAPACITY` | `dataset-service` | `500` | Bounded task queue capacity for row enrichment |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | All Eureka clients | `http://localhost:8761/eureka/` | Service registry URL |
| `SPRING_CLOUD_CONFIG_URI` | All Config clients | `http://localhost:8888` | Central configuration server URI |
| `NEXT_PUBLIC_GATEWAY_URL` | `apps/frontend` | `http://localhost:8080` | Public API Gateway URL for browser HTTP/SSE calls |

---

## 2. Spring Boot Properties by Service

### 2.1 API Gateway (`api-gateway`)
* **Default Port**: `8080` (or `9738` in production)
* Properties:
  ```yaml
  jwt:
    secret: ${JWT_SECRET}
  gateway:
    api-key: ${GATEWAY_API_KEY:}
    cors:
      allowed-origins: ${GATEWAY_CORS_ALLOWED_ORIGINS:http://localhost:3000}
  spring:
    cloud:
      gateway:
        server:
          webmvc:
            routes:
              - id: research-service-sync
                uri: ${RESEARCH_SERVICE_URL:http://localhost:9741}
                predicates:
                  - Path=/api/v1/research
              - id: ai-service-v1
                uri: ${AI_INTELLIGENT_SERVICE_URL:http://localhost:9742}
                predicates:
                  - Path=/api/v1/ai/**
              - id: dataset-service-v1-enrichment
                uri: ${DATASET_SERVICE_URL:http://localhost:9743}
                predicates:
                  - Path=/api/v1/enrichment/**
  ```

### 2.2 Auth Service (`auth-service`)
* **Default Port**: `9739`
* Properties:
  ```yaml
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL}
      username: ${SPRING_DATASOURCE_USERNAME}
      password: ${SPRING_DATASOURCE_PASSWORD}
  ```

### 2.3 Dataset Service (`dataset-service`)
* **Default Port**: `9743`
* Properties:
  ```yaml
  enrichment:
    execution:
      concurrency: ${ENRICHMENT_CONCURRENCY:3}
      queue-capacity: ${ENRICHMENT_QUEUE_CAPACITY:500}
    http:
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
  services:
    research:
      url: ${RESEARCH_SERVICE_URL:http://localhost:9741}
    ai:
      url: ${AI_INTELLIGENT_SERVICE_URL:http://localhost:9742}
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL}
      username: ${SPRING_DATASOURCE_USERNAME}
      password: ${SPRING_DATASOURCE_PASSWORD}
    jpa:
      hibernate:
        ddl-auto: validate
    flyway:
      enabled: true
  ```

### 2.4 Research Service (`research-service`)
* **Default Port**: `9741`
* Properties:
  ```yaml
  research:
    search:
      provider: ${SEARCH_PROVIDER_NAME:tavily}
      api-key: ${SEARCH_PROVIDER_API_KEY:}
      max-results: 5
    crawler:
      user-agent: "DataEnrichmentBot/1.0 (+https://enrichment.platform/bot)"
      connect-timeout-ms: 5000
      read-timeout-ms: 10000
      max-content-length-bytes: 524288 # 500 KB
    security:
      allow-private-ips: false # SSRF protection guard
  ```

### 2.5 AI Intelligent Service (`ai-intelligent-service`)
* **Default Port**: `9742`
* Properties:
  ```yaml
  spring:
    ai:
      gemini:
        api-key: ${GEMINI_API_KEY:}
        chat:
          options:
            model: ${GEMINI_MODEL:gemini-2.5-flash}
            temperature: 0.1
  ai:
    extraction:
      timeout-seconds: 15
      max-retries: 2
      verbatim-quote-required: true
  ```

### 2.6 Config Server (`config-server`)
* **Default Port**: `9736` (or `8888`)
* Properties:
  ```yaml
  spring:
    cloud:
      config:
        server:
          native:
            search-locations: file:/config
  ```

### 2.7 Discovery Server (`discovery-server`)
* **Default Port**: `9737` (or `8761`)
* Properties:
  ```yaml
  eureka:
    client:
      register-with-eureka: false
      fetch-registry: false
    server:
      enable-self-preservation: true
  ```

---

## 3. Domain Glossary & Terminology

### Anti-Spoofing Architecture
An ingress security design in `api-gateway` where all client-supplied identity headers (`X-User-Id`, `X-User-Email`) are unconditionally stripped by a request wrapper. After verifying the client's JWT Bearer token, the Gateway injects verified `X-User-Id` downstream.

### Bounded Concurrency
A concurrency design pattern implemented via `EnrichmentTaskExecutor` that constrains parallel row processing to a fixed thread pool (`enrichment.execution.concurrency: 3`) with a bounded queue (`queue-capacity: 500`) and `CallerRunsPolicy` backpressure.

### Confidence Tier
A discrete classification (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`) assigned to an enriched attribute:
* **`HIGH`**: Corroborated across multiple independent domains or anchored by an exact verbatim quote from an authoritative primary domain.
* **`MEDIUM`**: Sourced from a single reputable secondary source or verified search snippet.
* **`LOW`**: Inferred from contextual heuristics or uncorroborated third-party snippets.
* **`UNKNOWN`**: Attribute requested but no verifiable source evidence was discovered.

### Corroboration
The process of independently confirming a factual attribute across two or more distinct web domains. Corroboration increases the attribute's confidence score and detects conflicting information.

### Delimiter Sniffing
An automated heuristic in `CsvDatasetParser` that inspects the first several lines of an uploaded text file to identify its delimiter (comma `,`, semicolon `;`, tab `\t`, or pipe `|`), eliminating manual CSV format configuration.

### Deterministic Fallback
An architectural resilience pattern where operations (Spring AI LLM extraction or web crawling) transparently fall back to deterministic regex, heuristic parsing, or mock providers upon external API quota exhaustion, network timeouts, or rate limits.

### Evidence Tuple
The immutable factual triad `(value, exactQuote, sourceUrl)` backing every enriched attribute. No fact is admitted into the platform database without this grounding proof.

### IDOR Protection (Insecure Direct Object Reference)
A multi-tenant data boundary pattern where all batch jobs and persisted entities are bound to the owner's `userId`. Requests to access jobs or entities owned by another user are rejected with `404 Not Found` or `403 Forbidden`.

### Malformed Row Isolation
An ingestion safeguard in `CsvDatasetParser` that detects and quarantines rows with ragged column counts, unclosed quotes, or encoding corruption into isolated `MalformedRow` reports without failing valid rows.

### Non-Destructive Export
The guarantee that dataset export operations (CSV / XLSX) preserve all original user columns, ordering, and formatting unaltered, appending newly discovered attributes with an `Enriched_` prefix.

### Problem Details (RFC 7807)
The IETF standard specification for HTTP API error responses (`application/problem+json`). Formatted as JSON with fields: `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId`, and `timestamp`.

### Seed Cleansing
The pre-processing pipeline in `ai-intelligent-service` that normalizes raw entity input: stripping corporate suffixes (`Inc.`, `LLC`), removing honorifics (`Dr.`, `Ph.D.`), stripping URL tracking parameters (`utm_source`), and resolving canonical domain names.

### SSRF Protection (Server-Side Request Forgery)
A defensive validation guard in `SecurityValidator` (`research-service`) that checks all target URLs before dispatching HTTP web crawlers, blocking `localhost`, loopback addresses, RFC 1918 private subnets, and cloud instance metadata endpoints.

### Verbatim Evidence Grounding (Zero-Hallucination Principle)
The invariant enforced by `ai-intelligent-service` requiring every LLM-extracted claim to be accompanied by a verbatim snippet (`exactQuote`). The service verifies programmatically that `sourceText.contains(exactQuote)`. If the quote cannot be found verbatim in the crawled text, the fact is rejected.
