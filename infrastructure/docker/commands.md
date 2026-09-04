# Docker Development Commands

> Status: Active
> Version: 0.1
> Last Updated: 2026-09-04

## Purpose

This document provides the minimal reference commands for running and managing the local Docker development infrastructure (MySQL) for the Data Enrichment & Research Engine.

---

## Prerequisites

* **Docker** installed and running.
* **Docker Compose** v2+ accessible via the `docker compose` CLI.
* Local environment configuration file present at `infrastructure/docker/.env`.

---

## First-Time Setup

Before starting the container for the first time, ensure the environment file exists in the Docker directory:

```powershell
# Copy the example environment template to .env (if not already created)
Copy-Item infrastructure/docker/.env.example infrastructure/docker/.env
```

The environment file requires the following configuration variables:
* `MYSQL_ROOT_PASSWORD`: Root user password.
* `MYSQL_DATABASE`: Default application database name (e.g., `enrichment_db`).
* `MYSQL_USER`: Application database username (e.g., `enrichment_user`).
* `MYSQL_PASSWORD`: Application database user password.

---

## Development Commands (Run from Project Root)

All commands below are intended to be executed from the **repository root**:
`P:\Agentic AI\Enrichment Platform\data-enrichment-ai-intelligence`

### Start Infrastructure

Start the MySQL service in detached mode:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml up -d
```

*(Note: `--build` is omitted as the infrastructure utilizes official pre-built images with no local Dockerfile build steps).*

### Check Status

List running containers and their current state:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml ps
```

### Check MySQL Health

Inspect the container healthcheck status:

```powershell
docker inspect --format "{{.State.Health.Status}}" enrichment-mysql
```

### View Logs

Display recent logs for the MySQL service:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml logs mysql
```

### Follow Logs

Continuously stream logs from the MySQL service:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml logs -f mysql
```

### Restart Service

Restart the running MySQL container:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml restart mysql
```

### Stop Infrastructure

Halt running containers without removing them or discarding container state:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml stop
```

### Down (Tear Down Containers & Network)

Stop and remove the container and bridge network (`enrichment-network`), preserving the persistent data volume:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml down
```

> **Difference between `stop` and `down`:**
> * `stop`: Pauses the running container processes. Container instances and networks remain intact.
> * `down`: Stops and completely removes the containers and network. The named volume (`mysql_data`) remains intact.

### Validate Compose Configuration

Verify and render the resolved Compose configuration:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev.yml config
```

---

## Persistent Data Warning

> [!WARNING]
> Database records are persisted in the named Docker volume `mysql_data`.
> * Normal development commands such as `stop` and `down` preserve this volume and its stored data.
> * Manually deleting this volume destroys all local MySQL database tables and ingested entity records.
> * Do not execute destructive volume pruning or removal commands as part of routine development.

---

## Alternative: Running from `infrastructure/docker/`

If operating directly inside the `infrastructure/docker` directory:

```powershell
cd infrastructure/docker

# Start
docker compose -f docker-compose-dev.yml up -d

# Stop
docker compose -f docker-compose-dev.yml stop

# Down
docker compose -f docker-compose-dev.yml down
```

---

## Complete Development Stack

The all-in-one local development stack (`docker-compose-dev-all.yml`) orchestrates MySQL, all three backend microservices (`research-service`, `ai-intelligent-service`, `dataset-service`), and the Next.js frontend together.

### Start Everything

Build images (if modified) and run the complete stack in detached mode:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build
```

### Check Status

List running containers across the full stack and their current state:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml ps
```

### View Logs

Display and follow real-time logs across all services:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f
```

To stream logs from an individual service (e.g., `research-service`):

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f research-service
```

### Stop Everything

Stop active containers without tearing down networks or removing container states:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml stop
```

### Down (Tear Down All Containers & Network)

Stop and remove all containers and the shared bridge network (`enrichment-network`), while preserving the persistent `mysql_data` volume:

```powershell
docker compose -f infrastructure/docker/docker-compose-dev-all.yml down
```

### Running from `infrastructure/docker/` Directly

```powershell
cd infrastructure/docker

# Start all
docker compose -f docker-compose-dev-all.yml up -d

# Check status
docker compose -f docker-compose-dev-all.yml ps

# View logs
docker compose -f docker-compose-dev-all.yml logs -f

# Down
docker compose -f docker-compose-dev-all.yml down
```

