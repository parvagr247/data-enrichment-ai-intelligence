# Config Server

The **Config Server** (`com.subdual.config_server`) provides centralized, externalized, and environment-aware configuration for all backend microservices.

---

## 1. Core Responsibilities

* **Centralized Configuration**: Decouples application settings, database credentials, timeouts, and provider API keys from compiled JAR files.
* **Native File-Based Storage**: Operates with Spring Cloud Config Server's `native` search profile, reading versioned YAML configuration files directly from the `/config` directory mount.
* **Fault-Tolerant Client Fallback**: Client services bind via `optional:configserver:...`. If the Config Server is down or unreachable during startup, services fall back cleanly to their embedded `application.yaml` defaults without crashing.

---

## 2. Configuration Profiles & Property Files

Configurations are located in the repository root directory `config/`:

| File | Associated Service | Primary Settings Defined |
| :--- | :--- | :--- |
| **`application.yml`** | All Microservices | Eureka client discovery, Actuator health probes, Logback MDC pattern. |
| **`auth-service.yml`** | `auth-service` (:9739) | MySQL datasource, Flyway migrations, JWT secret and token expiration. |
| **`research-service.yml`** | `research-service` (:9741) | Search provider selection, crawler socket timeouts, buffer caps, and max sources. |
| **`ai-intelligent-service.yml`** | `ai-intelligent-service` (:9742) | Gemini model name (`gemini-3.5-flash-lite`), temperature, and fallback thresholds. |
| **`dataset-service.yml`** | `dataset-service` (:9743) | Bounded worker pool concurrency, queue size, JPA pool, and downstream service URLs. |
| **`api-gateway.yml`** | `api-gateway` (:9738) | Route definitions, CORS allowed origins, and anti-spoofing settings. |

---

## 3. Client Microservice Integration

Client microservices connect to the configuration server using Spring Boot's config import syntax:

```yaml
# Inside application.yaml of client microservices
spring:
  config:
    import: "optional:configserver:${CONFIG_SERVER_URL:http://localhost:9736}"
```

If `CONFIG_SERVER_URL` is omitted or the connection times out, Spring Boot logs a warning and proceeds with embedded defaults.
