# Local Development & Environment Setup

This guide walks you through setting up, configuring, and running the Data Enrichment AI Intelligence Platform on your local machine.

---

## 1. Prerequisites

Before starting, ensure the following toolchains are installed on your host system:

* **Java Development Kit (JDK)**: JDK 25 recommended (or JDK 21+ LTS).
  ```bash
  java -version
  ```
* **Build Tool**: Apache Maven 3.9+.
  ```bash
  mvn -version
  ```
* **Node.js & Package Manager**: Node.js 20+ (Node 22 LTS recommended) and npm 10+.
  ```bash
  node -v && npm -v
  ```
* **Container Runtime**: Docker Desktop or Docker Engine v24+ with Docker Compose v2+.
  ```bash
  docker compose version
  ```

---

## 2. Environment Configuration

The root directory contains an environment configuration template:

```bash
cp .env.example .env
```

Edit `.env` to configure your external credentials, persistence credentials, and runtime concurrency limits:

```bash
# ==============================================================================
# External AI & Search Providers
# (Optional: system automatically activates deterministic heuristic fallbacks if omitted)
# ==============================================================================
GEMINI_API_KEY=your_gemini_api_key_here
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=your_tavily_api_key_here

# ==============================================================================
# Database Configuration (MySQL 8.0)
# ==============================================================================
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/enrichment_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=enrichment_user
SPRING_DATASOURCE_PASSWORD=enrichment_password

# ==============================================================================
# Security & Auth Configuration
# ==============================================================================
JWT_SECRET=your_base64_256bit_secret_key_here_must_be_long_enough
JWT_EXPIRATION_MS=86400000

# ==============================================================================
# Runtime Concurrency & Tuning
# ==============================================================================
ENRICHMENT_CONCURRENCY_WORKERS=3
HTTP_CLIENT_CONNECT_TIMEOUT_MS=5000
HTTP_CLIENT_READ_TIMEOUT_MS=15000
```

> [!NOTE]
> External API keys (`GEMINI_API_KEY`, `SEARCH_PROVIDER_API_KEY`) are optional for local development. If omitted, the platform activates deterministic mock search providers and heuristic extraction, allowing offline execution.

---

## 3. Workflow Options

### Option A: Complete Docker Compose Stack (All-in-One)
The fastest way to launch the entire system (MySQL, backend Spring Boot microservices, and Next.js frontend with live hot-reloading):

```bash
# Build and launch all containers
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build

# Stream logs from all services
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f

# Inspect container health
docker compose -f infrastructure/docker/docker-compose-dev-all.yml ps
```

* Frontend UI: [http://localhost:3000](http://localhost:3000)
* API Gateway Ingress: [http://localhost:8080](http://localhost:8080)
* MySQL Database: `localhost:3306`

---

### Option B: Hybrid Development (Recommended for Active Coding)
Run the MySQL database in Docker, while running backend microservices and frontend directly in your IDE or terminal. This provides instant compilation, debugger attachment, and rapid test-driven iteration.

#### 1. Start MySQL Container Only
```bash
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d
```

#### 2. Start Core Infrastructure (Config & Discovery)
If developing in full microservice mode:
```bash
# Terminal 1: Discovery Server (:8761 / :9737)
cd apps/backend/discovery-server
mvn spring-boot:run

# Terminal 2: Config Server (:8888 / :9736)
cd apps/backend/config-server
mvn spring-boot:run
```

#### 3. Start Business Microservices
```bash
# Terminal 3: AI Intelligent Service (:9742)
cd apps/backend/ai-intelligent-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9742"

# Terminal 4: Research Service (:9741)
cd apps/backend/research-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9741"

# Terminal 5: Dataset Service (:9743)
cd apps/backend/dataset-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9743"

# Terminal 6 (Optional): Auth Service (:9739) & API Gateway (:8080)
cd apps/backend/auth-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9739"
```

#### 4. Start Next.js Frontend (:3000)
```bash
# Terminal 7: Next.js Frontend
cd apps/frontend
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000).

---

## 4. Troubleshooting Common Startup Issues

| Symptom | Cause | Resolution |
| :--- | :--- | :--- |
| `CommunicationsException: Communications link failure` | MySQL container not fully initialized | MySQL takes 15–20s on first boot to create schemas. Run `docker compose -f infrastructure/docker/docker-compose-dev.yml ps` and verify it is healthy before starting `dataset-service`. |
| `Port 3306 / 9741 / 3000 already in use` | Zombie process or existing container running | Kill existing container (`docker compose down`) or find the PID using `netstat -ano \| findstr :<port>` (Windows) or `lsof -i :<port>` (macOS/Linux). |
| `503 Service Unavailable` from Gateway | Downstream microservice not registered with Eureka | Microservices require 15–30s to complete initial Eureka discovery heartbeat. Check Eureka dashboard at [http://localhost:8761](http://localhost:8761). |
| `OutOfMemoryError: Java heap space` | Multiple Spring Boot services running locally on constrained RAM | Run services with constrained JVM memory: `-Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m"`. |
| Frontend API calls failing with CORS error | Gateway or direct service CORS configuration mismatch | Verify `GATEWAY_CORS_ALLOWED_ORIGINS` in `.env` includes `http://localhost:3000`. |
