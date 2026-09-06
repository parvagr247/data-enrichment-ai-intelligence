# Docker Infrastructure & Container Orchestration

This directory contains the canonical Docker Compose configurations and environment templates for the Data Enrichment AI Intelligence Platform.

---

## Compose Files

| File | Purpose | Services Included |
| :--- | :--- | :--- |
| **`docker-compose-dev-all.yml`** | **Complete local stack** (Recommended) | MySQL (`:3306`), `research-service` (`:9741`), `ai-intelligent-service` (`:9742`), `dataset-service` (`:9743`), `frontend` (`:3000`) with live hot-reloading |
| **`docker-compose-dev.yml`** | **Minimal database only** | MySQL (`:3306`) for local host/IDE microservice debugging |

---

## Common Workflows

```bash
# Start complete stack with rebuild
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build

# View logs across all services
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f

# Stop complete stack
docker compose -f infrastructure/docker/docker-compose-dev-all.yml down

# Start database only (for local IDE development)
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d
```

For complete development details and hot-reload volume mechanics, see the [Development & Operations Guide](../../docs/development.md).
