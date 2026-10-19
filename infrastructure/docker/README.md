# Docker Infrastructure & Compose Topologies

This directory contains Docker Compose files and container configurations for local development and cloud production deployment.

---

## Compose Topologies

| File | Purpose | Active Services | Host Port Mappings |
| :--- | :--- | :--- | :--- |
| **`docker-compose-dev-all.yml`** | Full local development with live code sync | Frontend, Research, AI, Dataset, MySQL | All service ports mapped |
| **`docker-compose-dev.yml`** | Infrastructure-only for local IDE runs | MySQL 8.0 only | Port 3306 mapped |
| **`docker-compose.prod.yml`** | Hardened 9-container production deployment | 7 microservices + Frontend + MySQL | Ports 3000 & 9738 only |

---

## Quick Start

### Run Full Stack Locally
```bash
# Copy development environment file
cp .env.example .env

# Start all microservices in detached mode with live watch
docker compose -f docker-compose-dev-all.yml up -d --build
```

### Run MySQL Only (Microservices in IDE)
```bash
docker compose -f docker-compose-dev.yml up -d
```

---

## Authoritative Documentation

All detailed deployment specifications, networking architecture, and operations guides have been centralized:

* 🐳 **[Docker Compose Topologies & Hot-Reloading Guide](../../docs/deployment/docker.md)**
* ☁️ **[GCP VM Production Deployment & CI/CD Guide](../../docs/deployment/gcp-vm-deployment.md)**
* 🏛️ **[System Architecture & Network Isolation](../../docs/architecture/overview.md)**
* ⚙️ **[Unified Configuration Reference](../../docs/reference/configuration.md)**
