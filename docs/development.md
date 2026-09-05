# Development Guide

This guide covers local development, running services with Docker Compose, compiling individual microservices, and running test suites.

---

## 1. Prerequisites

* **Java**: JDK 21 or JDK 25
* **Build Tool**: Apache Maven 3.9+
* **Containerization**: Docker & Docker Compose v2+
* **Frontend**: Node.js 18+ (Node 20+ recommended), npm or yarn

---

## 2. Running with Docker Compose

The simplest way to start the entire environment (MySQL, all 3 backend services, and Next.js frontend) is using Docker Compose:

```bash
# Navigate to docker compose directory
cd infra/docker

# Build and start all services in the background
docker compose -f docker-compose.yml up --build -d

# Check running container statuses
docker ps --filter "name=enrichment-"
```

### Exposed Ports

| Container | Host Port | Function |
| :--- | :--- | :--- |
| `enrichment-frontend` | `3000` | Next.js Web Interface |
| `enrichment-research-service` | `9741` | Research & Evidence Engine |
| `enrichment-ai-intelligent-service` | `9742` | AI Cleansing & Fact Extraction |
| `enrichment-dataset-service` | `9743` | Dataset Jobs & Persistence |
| `enrichment-mysql` | `3306` | Relational Database |

---

## 3. Local Microservice Development

To run or debug microservices individually on the host machine:

### A. Start MySQL Dependency Only
```bash
docker compose -f infra/docker/docker-compose.yml up -d enrichment-mysql
```

### B. Compile & Run `dataset-service`
```bash
cd apps/backend/dataset-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9743"
```

### C. Compile & Run `ai-intelligent-service`
```bash
cd apps/backend/ai-intelligent-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9742"
```

### D. Compile & Run `research-service`
```bash
cd apps/backend/research-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9741"
```

### E. Run Next.js Frontend
```bash
cd apps/frontend
npm install
npm run dev
```
Open `http://localhost:3000` in your browser.

---

## 4. Running Automated Tests

All services contain comprehensive unit and integration test suites:

### Research Service Tests
```bash
cd apps/backend/research-service
mvn clean test
```
*Executes 120 tests covering query generation, source ranking, content cleaning, and evidence extraction.*

### AI Intelligent Service Tests
```bash
cd apps/backend/ai-intelligent-service
mvn clean test
```
*Executes tests verifying requirement interpretation, input cleansing, and grounded extraction.*

### Dataset Service Tests
```bash
cd apps/backend/dataset-service
mvn clean test
```
*Executes tests covering batch job lifecycle, row-level error isolation, and entity persistence.*

### Frontend Production Build Test
```bash
cd apps/frontend
npm run build
```
*Performs full TypeScript validation, JSX syntax verification, and Next.js static page optimization.*

---

## 5. Configuration & Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/enrichment_db` | MySQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | `enrichment_user` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `enrichment_pass` | Database password |
| `SPRING_AI_GEMINI_API_KEY` | *(empty / simulated)* | Google GenAI API key for Gemini |
| `TAVILY_API_KEY` | *(empty / mock provider)* | Tavily Search API key |
