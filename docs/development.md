# Development & Operations Guide

This guide covers local environment setup, Docker Compose workflows, configuration parameters, compiling microservices, running test suites, and operational troubleshooting.

---

## 1. Prerequisites

* **Java**: JDK 21 or JDK 25 (Java 25 recommended)
* **Build Tool**: Apache Maven 3.9+
* **Containerization**: Docker & Docker Compose v2+
* **Frontend**: Node.js 20+ (Node 22 LTS recommended), npm 10+

---

## 2. Running with Docker Compose

The canonical Docker Compose configurations live in `infrastructure/docker/`.

### Option A: Complete Development Stack (Recommended)
Starts MySQL, all 3 backend Spring Boot microservices, and the Next.js frontend with live hot-reloading:

```bash
# Build and start all services in the background
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build

# View logs across all services
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f

# Check running container statuses
docker ps --filter "name=enrichment-"
```

#### Exposed Ports & Services

| Container | Host Port | Internal Port | Technology | Primary Role |
| :--- | :--- | :--- | :--- | :--- |
| `enrichment-frontend` | `3000` | `3000` | Next.js 15 / Node 22 | Web UI, Profiling, Dashboard |
| `enrichment-research-service` | `9741` | `9741` | Spring Boot 4 / Java 25 | Web Research & Evidence Engine |
| `enrichment-ai-intelligent-service` | `9742` | `9742` | Spring Boot 4 / Java 25 | Spring AI & Extraction Engine |
| `enrichment-dataset-service` | `9743` | `9743` | Spring Boot 4 / Java 25 | Batch Orchestration & Persistence |
| `enrichment-mysql` | `3306` | `3306` | MySQL 8.0 | Relational Persistence Store |

### Option B: Infrastructure Only (MySQL)
Run only the MySQL database in Docker when developing and debugging microservices locally in your IDE:

```bash
# Start MySQL only
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d

# Stop MySQL
docker compose -f infrastructure/docker/docker-compose-dev.yml down
```

### Option C: Production Stack & GCP VM Deployment
Starts the hardened 9-container production topology with internal network isolation and healthcheck dependency sequencing:

```bash
# Start full production stack
docker compose -f infrastructure/docker/docker-compose.prod.yml up -d --build

# View production status
docker compose -f infrastructure/docker/docker-compose.prod.yml ps
```
> For complete instructions on provisioning a fresh GCP VM, configuring VPC firewall rules, setting up swap memory, and executing database backups, see the **[GCP VM Deployment Guide](../infrastructure/docker/GCP_VM_DEPLOYMENT.md)**.

---

## 3. Local Microservice Development (Terminal / IDE)

When running services locally on the host machine:

### A. Environment Configuration
Create your `.env` file from the template in the root directory:
```bash
cp .env.example .env
```

Key environment variables:
```bash
# External AI & Search Providers (Optional: system uses deterministic fallbacks if omitted)
GEMINI_API_KEY=your_gemini_api_key_here
SEARCH_PROVIDER_API_KEY=your_tavily_api_key_here

# Database Configuration (matches docker-compose-dev.yml defaults)
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/enrichment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=enrichment_user
SPRING_DATASOURCE_PASSWORD=enrichment_password

# Bounded Concurrency
ENRICHMENT_CONCURRENCY_WORKERS=3
```

### B. Running Backend Services
Start each service in a separate terminal:

```bash
# 1. Start AI Intelligent Service (:9742)
cd apps/backend/ai-intelligent-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9742"

# 2. Start Research Service (:9741)
cd apps/backend/research-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9741"

# 3. Start Dataset Service (:9743)
cd apps/backend/dataset-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9743"
```

### C. Running Next.js Frontend (:3000)
```bash
cd apps/frontend
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## 4. Testing & Verification

### Running Automated Test Suites

```bash
# Run tests for ai-intelligent-service
cd apps/backend/ai-intelligent-service
mvn clean test

# Run tests for research-service
cd apps/backend/research-service
mvn clean test

# Run tests for dataset-service (17 unit tests including SSE & concurrency)
cd apps/backend/dataset-service
mvn clean test

# Build and type-check frontend
cd apps/frontend
npm run build
```

### Health Actuator Endpoints
Each Spring Boot service exposes Spring Actuator health probes:
* Research Service: `curl http://localhost:9741/actuator/health` &rarr; `{"status":"UP"}`
* AI Intelligent Service: `curl http://localhost:9742/actuator/health` &rarr; `{"status":"UP"}`
* Dataset Service: `curl http://localhost:9743/actuator/health` &rarr; `{"status":"UP"}`

### Postman API Verification
A complete Postman collection is located in `postman/`:
* Collection: `postman/data-enrichment-ai-intelligence.postman_collection.json`
* Environment: `postman/data-enrichment-local.postman_environment.json`
* See [Postman Guide](../postman/README.md) for details on running automated test suites via Newman.

---

## 5. Frontend & Backend Docker Hot-Reload & Compose Watch Mechanics

When running under `docker-compose-dev-all.yml` with Docker Compose Watch (`docker compose watch` or `docker compose up --watch`):

* **Docker Compose Watch (`develop.watch`)**: Monitors host source trees and syncs changes directly into containers:
  * **Backend services**: Edit changes under `src/` trigger `sync+restart` to `/app/src`. Manifest modifications (`pom.xml`, `Dockerfile`) trigger automatic container rebuilds (`action: rebuild`).
  * **Frontend**: Code edits trigger `sync` to `/app` with Next.js Turbopack fast refresh. Manifest modifications (`package.json`, `package-lock.json`, `Dockerfile`, `next.config.ts`) trigger container rebuilds (`action: rebuild`).
* **Volume Isolation**: Source directories are managed by `develop.watch` rather than host bind mounts under `volumes:`. This eliminates the Docker Compose warning (`path also declared by a bind mount volume, this path won't be monitored`) and ensures reliable cross-platform file monitoring.
* **Anonymous Volume Shadowing**: Anonymous volumes (`/app/node_modules` and `/app/.next`) protect containerized Linux binaries and the Turbopack cache.
* **Filesystem Polling**: `WATCHPACK_POLLING: "true"` is enabled for Windows/WSL2 filesystem event consistency.

---

For architecture and data models, see [System Architecture](architecture.md).  
For complete endpoint contracts, see [API Reference](api.md).  
For engineering decisions and ADRs, see [Architecture Decisions](decisions.md).
