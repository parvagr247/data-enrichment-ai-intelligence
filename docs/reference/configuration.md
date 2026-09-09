# Unified Configuration Reference

This reference catalogs all environment variables and Spring Boot application properties across the microservices mesh and frontend application.

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
| `ENRICHMENT_CONCURRENCY_WORKERS` | `dataset-service` | `3` | Number of concurrent worker threads per job |
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
        routes:
          - id: auth-service
            uri: lb://AUTH-SERVICE
            predicates:
              - Path=/api/v1/auth/**
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
    concurrency:
      workers: ${ENRICHMENT_CONCURRENCY_WORKERS:3}
    http:
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
  services:
    research:
      url: ${RESEARCH_SERVICE_URL:http://localhost:9741}
    ai:
      url: ${AI_SERVICE_URL:http://localhost:9742}
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
      max-content-length-bytes: 1048576 # 1 MB
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
            model: gemini-2.5-flash
            temperature: 0.1
  ai:
    extraction:
      timeout-seconds: 15
      max-retries: 2
      verbatim-quote-required: true
  ```

### 2.6 Config Server (`config-server`)
* **Default Port**: `8888` (or `9736` in production)
* Properties:
  ```yaml
  spring:
    cloud:
      config:
        server:
          native:
            search-locations: classpath:/config
  ```

### 2.7 Discovery Server (`discovery-server`)
* **Default Port**: `8761` (or `9737` in production)
* Properties:
  ```yaml
  eureka:
    client:
      register-with-eureka: false
      fetch-registry: false
    server:
      enable-self-preservation: false
  ```
