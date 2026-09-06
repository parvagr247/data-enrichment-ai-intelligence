# Docker Infrastructure & CI/CD Deployment Guide
## Data Enrichment AI Intelligence Platform

This directory contains the production Docker Compose topologies, container configurations, deployment automation scripts, and documentation for both local development and automated CI/CD deployments to Google Cloud Platform (GCP) Compute Engine virtual machines.

---

## Table of Contents

1. [Local Development](#1-local-development)
2. [Production Architecture](#2-production-architecture)
3. [GCP VM Setup (Fresh Machine)](#3-gcp-vm-setup-fresh-machine)
4. [Google Artifact Registry Setup](#4-google-artifact-registry-setup)
5. [GitHub Secrets & Variables Configuration](#5-github-secrets--variables-configuration)
6. [SSH Deployment Configuration](#6-ssh-deployment-configuration)
7. [Production Environment Configuration (.env)](#7-production-environment-configuration-env)
8. [Manual Deployment Procedure](#8-manual-deployment-procedure)
9. [GitHub Actions Automated CI/CD Pipeline](#9-github-actions-automated-cicd-pipeline)
10. [Log Streaming & Diagnostics](#10-log-streaming--diagnostics)
11. [Health Checks & Verification](#11-health-checks--verification)
12. [Rollback Procedure](#12-rollback-procedure)
13. [Comprehensive Troubleshooting](#13-comprehensive-troubleshooting)

---

## 1. Local Development

For local engineering, you can run the full multi-service stack or only the database layer.

### Option A: Run Full Stack Locally
```bash
# From repository root
cd infrastructure/docker

# Copy development environment file
cp .env.example .env

# Start all microservices in detached mode (builds images locally)
docker compose up -d

# Verify all services are healthy
docker compose ps

# Access local endpoints:
# Frontend UI:   http://localhost:3000
# API Gateway:   http://localhost:9738
```

### Option B: Run MySQL Database Only (Microservices in IDE)
If running Spring Boot microservices inside an IDE (IntelliJ, VS Code, Eclipse):
```bash
cd infrastructure/docker
docker compose -f docker-compose-dev.yml up -d
# MySQL will be available on localhost:3306
```

---

## 2. Production Architecture

In production, external clients (browsers) communicate exclusively with two public entrypoints: **Frontend UI (`:3000`)** and **API Gateway (`:9738`)**. All 7 backend microservices and MySQL run inside an isolated Docker bridge network (`enrichment-network`) and do not expose ports to the public internet.

```
                     Internet / Client Browser
                                 │
                                 ▼
                     GCP VM Public IP Address
                                 │
             ┌───────────────────┴───────────────────┐
             ▼ (Port 3000)                           ▼ (Port 9738)
   ┌───────────────────┐                   ┌───────────────────┐
   │    Frontend UI    │                   │    API Gateway    │
   │   (Next.js 15)    │                   │  (Spring Cloud)   │
   └───────────────────┘                   └─────────┬─────────┘
             │                                       │
  (Browser calls Gateway)                            │
             └───────────────────────────────────────┤
                                                     ▼
                  ┌──────────────────────────────────┴──────────────────────────────────┐
                  │ Internal Docker Bridge Network ("enrichment-network")                │
                  │                                                                     │
                  ▼                                  ▼                                  ▼
        ┌───────────────────┐              ┌───────────────────┐              ┌───────────────────┐
        │   Auth Service    │              │ Research Service  │              │  Dataset Service  │
        │      (:9739)      │              │      (:9741)      │              │      (:9743)      │
        └─────────┬─────────┘              └─────────┬─────────┘              └─────────┬─────────┘
                  │                                  │ (Internal HTTP)                  │
                  │                                  ▼                                  │
                  │                        ┌───────────────────┐                        │
                  │                        │  AI Intelligent   │                        │
                  │                        │  Service (:9742)  │                        │
                  │                        └───────────────────┘                        │
                  │                                                                     │
                  └──────────────────────────────────┬──────────────────────────────────┘
                                                     │
                                                     ▼
                                           ┌───────────────────┐
                                           │     MySQL 8.0     │
                                           │  (:3306 Internal) │
                                           └─────────┬─────────┘
                                                     │
                               ┌─────────────────────┴─────────────────────┐
                               ▼                                           ▼
                     ┌───────────────────┐                       ┌───────────────────┐
                     │   Config Server   │                       │ Discovery Server  │
                     │  (:9736 Internal) │                       │  (:9737 Internal) │
                     └───────────────────┘                       └───────────────────┘
```

### Microservice Roles & Ports:
1. **API Gateway (`:9738`)**: Public reverse proxy, validates JWT tokens and optional API keys, proxies routes to downstream services.
2. **Frontend UI (`:3000`)**: Next.js 15 SSR & client dashboard with Server-Sent Events (SSE) streaming.
3. **Auth Service (`:9739`)**: Private internal service managing BCrypt user authentication, registration, and JWT generation/validation.
4. **Research Service (`:9741`)**: Private internal service executing web searches (Tavily/Mock), content scraping, source deduplication, and evidence extraction.
5. **AI Intelligent Service (`:9742`)**: Private internal service integrating Google Gemini 2.0 Flash for entity extraction and profile normalization.
6. **Dataset Service (`:9743`)**: Private internal service managing datasets, async entity enrichment queues, and CSV/JSON exports.
7. **MySQL (`:3306`)**: Database with persistent volume storage (`mysql_data`) and automatic Flyway migrations.
8. **Config Server (`:9736`)**: Spring Cloud Config Server reading YAML configuration files from `/config`.
9. **Discovery Server (`:9737`)**: Netflix Eureka service registry providing dynamic service discovery.

---

## 3. GCP VM Setup (Fresh Machine)

### Step 1: Provision the VM
- **Recommended Machine**: `e2-standard-4` (4 vCPUs, 16 GB RAM, 50 GB SSD).
- **Operating System**: Ubuntu 24.04 LTS (x86_64).
- **Network Tags**: `enrichment-platform,http-server,https-server`.

```bash
gcloud compute instances create enrichment-platform-vm \
    --project="<YOUR_GCP_PROJECT_ID>" \
    --zone="us-central1-a" \
    --machine-type="e2-standard-4" \
    --network-interface="network-tier=PREMIUM,subnet=default" \
    --scopes="https://www.googleapis.com/auth/cloud-platform" \
    --tags="enrichment-platform,http-server,https-server" \
    --create-disk="auto-delete=yes,boot=yes,image=projects/ubuntu-os-cloud/global/images/family/ubuntu-2404-lts-amd64,mode=rw,size=50,type=pd-balanced"
```

### Step 2: Configure VPC Firewall Rules
Allow public inbound access on ports `22` (SSH), `3000` (Frontend), and `9738` (API Gateway):
```bash
# Allow SSH
gcloud compute firewall-rules create allow-enrichment-ssh \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:22 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# Allow Frontend UI
gcloud compute firewall-rules create allow-enrichment-frontend \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:3000 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# Allow API Gateway
gcloud compute firewall-rules create allow-enrichment-gateway \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:9738 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform
```

### Step 3: Install Docker & Dependencies on VM
Connect to your VM via SSH and run:
```bash
# Update APT index and install prerequisites
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg

# Add Docker GPG key and official repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Allow non-root user to run Docker
sudo usermod -aG docker $USER
newgrp docker

# Configure 4GB Swap Space (essential for Java build & memory stability)
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 4. Google Artifact Registry Setup

Create a Docker repository in Google Artifact Registry to store the built container images:

```bash
# Enable Artifact Registry API
gcloud services enable artifactregistry.googleapis.com

# Create the repository (e.g. enrichment-repo in us-central1)
gcloud artifacts repositories create enrichment-repo \
    --repository-format=docker \
    --location=us-central1 \
    --description="Data Enrichment Platform Docker Images"

# Verify repository creation
gcloud artifacts repositories list
```

---

## 5. GitHub Secrets & Variables Configuration

To enable automated CI/CD via GitHub Actions, navigate to **GitHub Repository Settings > Secrets and variables > Actions**:

### A. GitHub Secrets (Sensitive Credentials)

| Secret Name | Required | Description | Example / How to Generate |
| :--- | :--- | :--- | :--- |
| `GCP_SA_KEY` | **Yes** | Service Account JSON key with `roles/artifactregistry.writer` permissions | `{"type": "service_account", "project_id": ...}` |
| `GCP_SSH_PRIVATE_KEY` | **Yes** | OpenSSH Private Key used to authenticate with the GCP VM | Generated via `ssh-keygen -t ed25519` |

#### Creating the GCP Service Account for GitHub Actions:
```bash
# Create service account
gcloud iam service-accounts create github-deployer \
    --display-name="GitHub Actions CI/CD Deployer"

# Grant Artifact Registry Writer role
gcloud projects add-iam-policy-binding <GCP_PROJECT_ID> \
    --member="serviceAccount:github-deployer@<GCP_PROJECT_ID>.iam.gserviceaccount.com" \
    --role="roles/artifactregistry.writer"

# Generate JSON Key
gcloud iam service-accounts keys create sa-key.json \
    --iam-account="github-deployer@<GCP_PROJECT_ID>.iam.gserviceaccount.com"

# Paste contents of sa-key.json into GitHub Secret GCP_SA_KEY
```

### B. GitHub Variables (Non-Sensitive Configuration)

| Variable Name | Required | Description | Example |
| :--- | :--- | :--- | :--- |
| `GCP_PROJECT_ID` | **Yes** | GCP Project ID | `my-enrichment-prod` |
| `GCP_REGION` | **Yes** | Region of Artifact Registry | `us-central1` |
| `GCP_ARTIFACT_REPOSITORY` | **Yes** | Name of the Artifact Registry repository | `enrichment-repo` |
| `GCP_VM_HOST` | **Yes** | External IP address of your GCP VM | `34.123.45.67` |
| `GCP_VM_USER` | **Yes** | SSH user on the VM | `ubuntu` |
| `GCP_VM_APP_DIR` | No | App directory on VM (defaults to `~/data-enrichment-ai-intelligence`) | `/home/ubuntu/data-enrichment-ai-intelligence` |
| `GCP_VM_SSH_PORT` | No | SSH Port (defaults to `22`) | `22` |
| `NEXT_PUBLIC_GATEWAY_URL` | No | Production Gateway URL baked into Frontend (defaults to `http://<VM_IP>:9738`) | `http://34.123.45.67:9738` |

---

## 6. SSH Deployment Configuration

Generate a dedicated deployment SSH key pair on your local machine:
```bash
ssh-keygen -t ed25519 -f ./gcp_deploy_key -C "github-actions-deployer"
```

1. Add the **private key** (`./gcp_deploy_key`) to GitHub Secret `GCP_SSH_PRIVATE_KEY`.
2. Add the **public key** (`./gcp_deploy_key.pub`) to the GCP VM:
   ```bash
   # On the GCP VM
   mkdir -p ~/.ssh && chmod 700 ~/.ssh
   echo "<PASTE CONTENTS OF gcp_deploy_key.pub>" >> ~/.ssh/authorized_keys
   chmod 600 ~/.ssh/authorized_keys
   ```

---

## 7. Production Environment Configuration (.env)

On the GCP VM, production runtime secrets live securely inside `infrastructure/docker/.env`.

```bash
# On the GCP VM:
cd ~/data-enrichment-ai-intelligence/infrastructure/docker
cp .env.example .env
nano .env
```

### Production `.env` Example:
```bash
# ------------------------------------------------------------------------------
# 1. CONTAINER REGISTRY & IMAGE TAG
# ------------------------------------------------------------------------------
IMAGE_PREFIX=us-central1-docker.pkg.dev/my-gcp-project/enrichment-repo/
IMAGE_TAG=latest

# ------------------------------------------------------------------------------
# 2. APPLICATION & ENVIRONMENT
# ------------------------------------------------------------------------------
APP_ENV=production
COMPOSE_PROJECT_NAME=data-enrichment-production

# ------------------------------------------------------------------------------
# 3. DATABASE (MySQL :3306)
# ------------------------------------------------------------------------------
MYSQL_ROOT_PASSWORD=StrongRootPassword_123!
MYSQL_DATABASE=enrichment_db
MYSQL_USER=enrichment_user
MYSQL_PASSWORD=StrongUserPassword_123!

# ------------------------------------------------------------------------------
# 4. AUTHENTICATION & SECURITY (Auth Service :9739 & Gateway :9738)
# ------------------------------------------------------------------------------
JWT_SECRET=super-secure-jwt-signing-secret-key-at-least-256-bits-long
JWT_EXPIRATION_MS=86400000
GATEWAY_API_KEY=
GATEWAY_CORS_ALLOWED_ORIGINS=http://<VM_PUBLIC_IP>:3000,http://localhost:3000

# ------------------------------------------------------------------------------
# 5. AI INTELLIGENT SERVICE (:9742)
# ------------------------------------------------------------------------------
GEMINI_API_KEY=your_gemini_api_key
AI_MODEL=gemini-2.0-flash
AI_MOCK_MODE=false

# ------------------------------------------------------------------------------
# 6. RESEARCH SERVICE (:9741)
# ------------------------------------------------------------------------------
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=your_tavily_api_key
SEARCH_PROVIDER_BASE_URL=https://api.tavily.com
SEARCH_DISCOVERY_MAX_RESULTS=5
SEARCH_DISCOVERY_TIMEOUT_MS=4000
WEB_FETCH_CONNECT_TIMEOUT_MS=3000
WEB_FETCH_READ_TIMEOUT_MS=5000
WEB_FETCH_MAX_RESPONSE_SIZE_MB=5
RESEARCH_MAX_SOURCES=5
RESEARCH_MAX_CONTENT_LENGTH=50000

# ------------------------------------------------------------------------------
# 7. DATASET SERVICE (:9743)
# ------------------------------------------------------------------------------
ENRICHMENT_CONCURRENCY=3
ENRICHMENT_QUEUE_CAPACITY=500
ENRICHMENT_ENTITY_TIMEOUT=60
ENRICHMENT_JOB_TIMEOUT=30
ENRICHMENT_AI_ENABLED=true

# ------------------------------------------------------------------------------
# 8. FRONTEND DASHBOARD (:3000)
# ------------------------------------------------------------------------------
NEXT_PUBLIC_GATEWAY_URL=http://<VM_PUBLIC_IP>:9738
NEXT_PUBLIC_GATEWAY_API_KEY=
```

---

## 8. Manual Deployment Procedure

To manually deploy or update the stack on the VM using `deploy.sh`:

```bash
# 1. Connect to GCP VM
ssh ubuntu@<VM_PUBLIC_IP>

# 2. Navigate to docker directory
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

# 3. Pull images and launch services (using latest tag or specific Git commit SHA)
./deploy.sh latest

# Or specify a Git commit SHA:
# ./deploy.sh 8a3b5c7
```

`deploy.sh` automatically:
1. Validates required environment variables from `.env`.
2. Authenticates Docker to Google Artifact Registry if needed.
3. Pulls container images from Artifact Registry.
4. Starts/updates containers in detached mode without deleting database volumes.
5. Polls the API Gateway, Frontend, and internal microservices until they report `healthy`.

---

## 9. GitHub Actions Automated CI/CD Pipeline

When code is pushed to the repository, two workflows execute:

```
[Git Commit / Pull Request]
          │
          ▼
┌───────────────────────────────────────┐
│     .github/workflows/ci.yml          │
├───────────────────────────────────────┤
│ 1. Backend Test Matrix (Java 25)      │
│    - ai-intelligent-service           │
│    - api-gateway                      │
│    - auth-service                     │
│    - config-server                    │
│    - dataset-service                  │
│    - discovery-server                 │
│    - research-service                 │
│ 2. Frontend CI (Node 22)              │
│    - npm ci                           │
│    - npm run lint                     │
│    - npm run build                    │
│ 3. Docker Compose Validation          │
└───────────────────────────────────────┘
          │ (On Push to main)
          ▼
┌───────────────────────────────────────┐
│     .github/workflows/deploy.yml      │
├───────────────────────────────────────┤
│ 1. Build & Push (Matrix)              │
│    - Builds 8 container images        │
│    - Tags with ${{ github.sha }}      │
│    - Pushes to Artifact Registry      │
│ 2. Deploy to GCP VM                   │
│    - Connects via secure SSH          │
│    - Syncs compose & config files     │
│    - Executes deploy.sh with Git SHA  │
│    - Verifies healthchecks            │
└───────────────────────────────────────┘
```

### Safety & Concurrency Protections:
- **Serial Concurrency**: `concurrency: production-deployment` with `cancel-in-progress: false` ensures multiple pushes run sequentially and never corrupt deployment state.
- **Fail-Fast**: If any test, lint, or image build fails, deployment is automatically prevented.
- **Health Verification**: If containers fail to become healthy within 120 seconds, the workflow fails and dumps container logs.

---

## 10. Log Streaming & Diagnostics

Use Docker Compose to monitor containers in real time:

```bash
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

# Stream logs across all 9 containers
docker compose -f docker-compose.prod.yml logs -f

# Stream logs for a specific service
docker compose -f docker-compose.prod.yml logs -f --tail=100 api-gateway
docker compose -f docker-compose.prod.yml logs -f --tail=100 auth-service
docker compose -f docker-compose.prod.yml logs -f --tail=100 dataset-service
docker compose -f docker-compose.prod.yml logs -f --tail=100 research-service
docker compose -f docker-compose.prod.yml logs -f --tail=100 ai-intelligent-service

# Inspect resource utilization (CPU / RAM)
docker stats --no-stream
```

---

## 11. Health Checks & Verification

### A. Automated Actuator Endpoints

From the VM host or via curl:

```bash
# 1. API Gateway (Public :9738)
curl -s http://localhost:9738/actuator/health | jq .

# 2. Frontend Dashboard (Public :3000)
curl -s -I http://localhost:3000 | head -n 5

# 3. Config Server (Internal :9736)
docker exec enrichment-config-server curl -s http://localhost:9736/actuator/health | jq .

# 4. Discovery Server / Eureka (Internal :9737)
docker exec enrichment-discovery-server curl -s http://localhost:9737/actuator/health | jq .

# 5. Auth Service (Internal :9739)
docker exec enrichment-auth-service curl -s http://localhost:9739/actuator/health | jq .

# 6. Research Service (Internal :9741)
docker exec enrichment-research-service curl -s http://localhost:9741/actuator/health | jq .

# 7. AI Intelligent Service (Internal :9742)
docker exec enrichment-ai-intelligent-service curl -s http://localhost:9742/actuator/health | jq .

# 8. Dataset Service (Internal :9743)
docker exec enrichment-dataset-service curl -s http://localhost:9743/actuator/health | jq .

# 9. MySQL Ping (Internal :3306)
docker exec enrichment-mysql mysqladmin ping -h localhost --silent && echo "MySQL is healthy"
```

### B. Compose Health Status Table
```bash
docker compose -f docker-compose.prod.yml ps
```
All containers should display status `Up (healthy)`.

---

## 12. Rollback Procedure

Because all container images are tagged with immutable Git commit SHAs, you can instantly rollback the entire stack to any previous commit:

```bash
# 1. Identify the previous stable commit SHA from GitHub or git log
PREVIOUS_SHA=a1b2c3d4e5f6

# 2. SSH into the GCP VM
ssh ubuntu@<VM_PUBLIC_IP>
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

# 3. Execute deploy.sh with the previous commit SHA
./deploy.sh ${PREVIOUS_SHA}
```

This pulls the exact previous images from Artifact Registry and recreates the containers in seconds without deleting the MySQL persistent volume.

---

## 13. Comprehensive Troubleshooting

### 1. Config Server Unavailable (`Connection refused: config-server:9736`)
- **Cause**: Microservice started before `config-server` finished loading native configuration.
- **Fix**: Check config-server logs: `docker compose -f docker-compose.prod.yml logs config-server`. Ensure `/config` volume is mounted and YAML files are present in `config/`.

### 2. Eureka Registration Failure (`Cannot execute request on any known server`)
- **Cause**: Microservices attempted registration before Eureka finished initialization.
- **Fix**: `docker-compose.prod.yml` enforces `depends_on: discovery-server: condition: service_healthy`. If Eureka is unhealthy, verify its container health with `docker compose logs discovery-server`.

### 3. Gateway Cannot Reach Downstream Services (`503 Service Unavailable`)
- **Cause**: Microservices have not yet completed heartbeat registration with Eureka (can take 15-30s on startup).
- **Fix**: Wait 30 seconds for heartbeat synchronization or inspect Eureka registry via `docker exec enrichment-discovery-server curl -s http://localhost:9737/eureka/apps`.

### 4. MySQL Unavailable / Database Connection Refused
- **Cause**: MySQL 8.0 initializes the data directory on first launch, taking ~20-30s.
- **Fix**: `mysqladmin ping` healthcheck ensures microservices wait for MySQL readiness. Do not run `docker compose down -v` as that deletes the database directory.

### 5. Flyway Migration Failure
- **Cause**: Schema mismatch or corrupted migration lock table.
- **Fix**: Inspect Flyway migration history:
  ```bash
  docker exec -it enrichment-mysql mysql -u enrichment_user -p"StrongUserPassword_123!" enrichment_db -e "SELECT * FROM flyway_schema_history;"
  ```
  Never alter existing migration scripts in version control. Always create a new versioned migration script (`V2__...sql`).

### 6. Frontend API Failure / CORS Error in Browser
- **Cause**: Browser cannot connect to Gateway IP or Gateway blocked the origin.
- **Fix**: Ensure `GATEWAY_CORS_ALLOWED_ORIGINS` in `.env` includes `http://<VM_PUBLIC_IP>:3000`. Ensure `NEXT_PUBLIC_GATEWAY_URL` points to `http://<VM_PUBLIC_IP>:9738`.

### 7. Google Artifact Registry Authentication Failure
- **Cause**: VM or GitHub Action lacks permission to read/write to the registry.
- **Fix**:
  - For GitHub Actions: Ensure Service Account has `roles/artifactregistry.writer`.
  - For GCP VM: Ensure VM instance scope includes `https://www.googleapis.com/auth/cloud-platform` or run `gcloud auth configure-docker <region>-docker.pkg.dev`.

### 8. SSH Failure in GitHub Actions (`Host key verification failed` or `Permission denied`)
- **Cause**: Incorrect private key or host key mismatch.
- **Fix**:
  - Verify `GCP_SSH_PRIVATE_KEY` matches the public key in `~/.ssh/authorized_keys` on the VM.
  - Test connecting manually: `ssh -i ./gcp_deploy_key ubuntu@<VM_PUBLIC_IP>`.
  - Ensure VM firewall allows port 22 (`allow-enrichment-ssh`).
