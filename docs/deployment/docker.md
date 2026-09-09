# Docker Topologies & Container Deployment

This document explains the Docker Compose container configurations, live hot-reloading mechanics, bridge network security, and healthcheck dependency chains across local and production environments.

---

## 1. Docker Compose Topologies Overview

The platform provides three canonical Docker Compose configurations inside `infrastructure/docker/`:

| Compose File | Purpose | Active Services | Network Isolation |
| :--- | :--- | :--- | :--- |
| **`docker-compose-dev-all.yml`** | Full local development with live code sync | Frontend, Research, AI, Dataset, MySQL | All service ports mapped to host |
| **`docker-compose-dev.yml`** | Infrastructure-only for local IDE microservice runs | MySQL 8.0 only | Only Port 3306 mapped to host |
| **`docker-compose.prod.yml`** | Hardened 9-container production deployment | 7 microservices + Frontend + MySQL | Only Ports 3000 & 9738 mapped to host |

---

## 2. Full Development Stack (`docker-compose-dev-all.yml`)

This configuration runs the application containers with **Docker Compose Watch** (`develop.watch`) for instant hot-reloading:

```bash
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build
```

### Hot-Reload & File Sync Architecture
Rather than relying on brittle bind mounts that degrade on Windows/macOS, `docker-compose-dev-all.yml` leverages native `develop.watch` rules:

```yaml
develop:
  watch:
    - action: sync+restart
      path: ./apps/backend/dataset-service/src
      target: /app/src
    - action: rebuild
      path: ./apps/backend/dataset-service/pom.xml
```

* **Backend Services (`src/`)**: Edits to Java source files trigger `sync+restart`, quickly synchronizing the changed class files into the container.
* **Manifest Changes (`pom.xml`, `package.json`, `Dockerfile`)**: Automatically trigger a complete container image rebuild (`action: rebuild`).
* **Frontend Fast Refresh**: Next.js source changes sync directly to `/app`, picked up instantaneously by Turbopack with `WATCHPACK_POLLING: "true"` enabled.
* **Anonymous Volume Shadowing**: Anonymous volumes (`/app/node_modules` and `/app/.next`) ensure container Linux binaries are isolated from the host OS.

---

## 3. Infrastructure-Only Stack (`docker-compose-dev.yml`)

Designed for developers running Spring Boot applications and the Next.js frontend directly in an IDE (IntelliJ, VS Code, or Eclipse):

```bash
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d
```

* Launches **MySQL 8.0** on `localhost:3306`.
* Configures persistent volume `mysql_dev_data`.
* Performs continuous healthcheck via `mysqladmin ping -h localhost --silent`.

---

## 4. Production Stack (`docker-compose.prod.yml`)

The production topology runs 9 containerized services inside an isolated Docker bridge network (`enrichment-network`):

```
                        Public Internet
                               │
               ┌───────────────┴───────────────┐
               ▼ (Port 3000)                   ▼ (Port 9738 / 8080)
     ┌───────────────────┐           ┌───────────────────┐
     │ enrichment-       │           │ enrichment-       │
     │ frontend          │           │ api-gateway       │
     └───────────────────┘           └─────────┬─────────┘
                                               │
                   ┌───────────────────────────┴───────────────────────────┐
                   │       Isolated Docker Bridge: enrichment-network      │
                   │                                                       │
                   ▼                       ▼                               ▼
         ┌───────────────────┐   ┌───────────────────┐           ┌───────────────────┐
         │ enrichment-       │   │ enrichment-       │           │ enrichment-       │
         │ auth-service      │   │ research-service  │           │ dataset-service   │
         │ (:9739 Internal)  │   │ (:9741 Internal)  │           │ (:9743 Internal)  │
         └─────────┬─────────┘   └─────────┬─────────┘           └─────────┬─────────┘
                   │                       │                               │
                   │                       ▼                               │
                   │             ┌───────────────────┐                     │
                   │             │ enrichment-       │                     │
                   │             │ ai-service        │                     │
                   │             │ (:9742 Internal)  │                     │
                   │             └───────────────────┘                     │
                   │                                                       │
                   └───────────────────────┬───────────────────────────────┘
                                           │
                                           ▼
                                 ┌───────────────────┐
                                 │ enrichment-mysql  │
                                 │ (:3306 Internal)  │
                                 └───────────────────┘
```

### 4.1 Production Port Accessibility Matrix

| Container Name | Service | Internal Port | Host Port Exposed | Network Exposure |
| :--- | :--- | :--- | :--- | :--- |
| `enrichment-api-gateway` | Spring Cloud Gateway | `9738` | `9738` | **PUBLIC INGRESS** |
| `enrichment-frontend` | Next.js 15 Client | `3000` | `3000` | **PUBLIC WEB UI** |
| `enrichment-auth-service` | User Auth & Tokens | `9739` | None | **INTERNAL ONLY** |
| `enrichment-research-service`| Crawling & Evidence | `9741` | None | **INTERNAL ONLY** |
| `enrichment-ai-intelligent-service` | LLM & Heuristics | `9742` | None | **INTERNAL ONLY** |
| `enrichment-dataset-service` | Batch & Persistence | `9743` | None | **INTERNAL ONLY** |
| `enrichment-mysql` | Relational Store | `3306` | None | **INTERNAL ONLY** |
| `enrichment-config-server` | Centralized Config | `9736` | None | **INTERNAL ONLY** |
| `enrichment-discovery-server`| Eureka Registry | `9737` | None | **INTERNAL ONLY** |

> [!IMPORTANT]
> In production, microservices have **no host port mappings**. All communication is restricted to the internal Docker network. External requests MUST route through `enrichment-api-gateway`, which validates authentication and strips spoofed headers.

---

## 5. Healthchecks & Startup Sequencing

Production microservices define explicit health probes and dependency triggers to ensure orderly startup:

```yaml
depends_on:
  enrichment-mysql:
    condition: service_healthy
  enrichment-discovery-server:
    condition: service_healthy
```

1. **MySQL 8.0** starts first; health is validated via `mysqladmin ping`.
2. **Config Server** and **Discovery Server** start once MySQL is healthy.
3. **Business Microservices** (`auth`, `research`, `ai`, `dataset`) start once Eureka and MySQL are healthy.
4. **API Gateway** starts once business services are healthy and registered.
5. **Frontend UI** starts and connects to the public API Gateway.
