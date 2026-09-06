# Docker Development Commands

> Status: Active
> Version: 0.2
> Last Updated: 2026-09-04

## Purpose

This document provides the reference commands and workflows for running the Data Enrichment & Research Engine local development environment using Docker Compose and Docker Compose Watch.

---

## Prerequisites

* **Docker** installed and running (Docker Desktop 25+ or Docker Engine).
* **Docker Compose** v2.22+ supporting `docker compose watch`.
* Environment file configured at `infrastructure/docker/.env`.

---

## First-Time Setup

Before starting the containers for the first time, ensure `infrastructure/docker/.env` exists:

```powershell
# Copy template to .env
Copy-Item infrastructure/docker/.env.example infrastructure/docker/.env
```

Review and adjust any credentials if desired (defaults are pre-configured for local development). Note that `MYSQL_USER` must remain a non-root user (e.g., `enrichment_user`), as official MySQL images reject `MYSQL_USER=root`.

---

## Primary Development Workflow (Run from Project Root)

All commands below are intended to be executed from the **repository root**:
`P:\Agentic AI\Enrichment Platform\data-enrichment-ai-intelligence`

### 1. Start Complete Stack with Automatic Watch (Recommended)

Start all services (MySQL, research-service, ai-intelligent-service, dataset-service, frontend) and continuously watch for file changes:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up --watch
```

*(Optional: add `--env-file infrastructure/docker/.env` if defining custom environment variable overrides)*

#### How Automatic Change Detection Works:
* **Java Source Changes (`apps/backend/*/src/**`)**:
  Compose Watch syncs the modified `.java` file directly into `/app/src` and restarts only that specific service container (`action: sync+restart`). Maven recompiles the updated file using the cached dependency layer and restarts the service. **Other services, frontend, and MySQL are not touched or rebuilt.**
* **Backend Build Changes (`pom.xml` / `Dockerfile`)**:
  Compose Watch detects manifest changes and triggers an in-place `rebuild` for that specific backend service only.
* **Frontend Source Changes (`apps/frontend/**`)**:
  Compose Watch syncs modified files (`.tsx`, `.ts`, `.css`) into the running frontend container (`action: sync`). Next.js Fast Refresh automatically reloads in the browser without container restarts or image rebuilds.
* **Frontend Build Changes (`package.json` / `package-lock.json` / `Dockerfile`)**:
  Compose Watch triggers an automatic `rebuild` of the frontend image.

---

### 2. Start in Detached Mode (Background)

To run the complete platform in the background without active log streaming:

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml up -d
```
> **Note on Hot-Reloading in Detached Mode**:
> All backend microservices and the Next.js frontend mount source files into their containers via `volumes:` with anonymous volume shadowing for `node_modules` and `.next`. Next.js file polling (`WATCHPACK_POLLING=true`) ensures that host edits hot-reload immediately in the browser even when running in detached mode (`up -d`) without an active `--watch` session.


To run only the infrastructure (MySQL database):

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev.yml up -d
```

---

### 3. Check Service Status

View the status of all running services, ports, and healthchecks:

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml ps
```

---

### 4. View Service Logs

Stream logs for a specific service:

```powershell
# MySQL logs
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml logs -f mysql

# Research service logs
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml logs -f research-service

# AI Intelligent service logs
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml logs -f ai-intelligent-service

# Dataset service logs
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml logs -f dataset-service

# Frontend logs
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml logs -f frontend
```

---

### 5. Rebuild One Service Manually

To manually force a clean rebuild of a single service without rebuilding the rest of the stack:

```powershell
# Rebuild research-service only
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml build research-service

# Rebuild frontend only
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml build frontend
```

---

### 6. Stop Stack (Preserve Persistent Data)

Stop and remove running containers and networks while preserving database data:

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml down
```

---

### 7. Reset Development Database (Delete Volume)

To completely reset the development database from scratch (e.g., to purge test records or repair corrupt redo logs):

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml down -v
```

> [!WARNING]
> The `-v` flag deletes all Docker named volumes (`mysql_data` and `maven_cache`).
> * On the subsequent `up`, MySQL will perform a clean initialization from scratch.
> * Never run `-v` in normal everyday stopping workflows unless you intend to wipe local test database state.

---

### 8. Validate Compose Configuration

Render and validate the resolved Compose configuration:

```powershell
docker compose --env-file infrastructure/docker/.env -f infrastructure/docker/docker-compose-dev-all.yml config
```

---

## Maven & Layer Caching Architecture

1. **Docker Layer Cache**: Each backend Dockerfile executes `./mvnw dependency:resolve -B` *before* copying `src/`. As long as `pom.xml` does not change, this layer is `CACHED` by Docker, skipping all remote repository downloads during image builds.
2. **Runtime Volume Cache (`maven_cache`)**: A shared named volume `maven_cache` is mounted at `/root/.m2` across all Spring Boot services. Any runtime plugins or dependencies downloaded during execution persist across container restarts, eliminating repeat downloads.
