# GCP VM Production Deployment Guide
## Data Enrichment AI Intelligence Platform

This document provides a simple, practical guide for deploying and operating the Data Enrichment AI Intelligence Platform on a Google Cloud Platform (GCP) Compute Engine Virtual Machine using Docker Compose and GitHub Actions.

---

## 1. Production Architecture & Network Exposure

The application runs as 9 containerized services inside an isolated Docker bridge network (`enrichment-network`). Only the **API Gateway** and **Frontend UI** are exposed publicly. All backend microservices communicate securely inside the Docker network.

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

### Port Accessibility Matrix

| Service | Container Name | Port | Network Scope | Healthcheck Endpoint |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `enrichment-api-gateway` | `9738` | **PUBLIC / EXTERNAL** | `http://localhost:9738/actuator/health` |
| **Frontend UI** | `enrichment-frontend` | `3000` | **PUBLIC / EXTERNAL** | `http://localhost:3000` |
| **Auth Service** | `enrichment-auth-service` | `9739` | **INTERNAL ONLY** | `http://localhost:9739/actuator/health` |
| **Research Service** | `enrichment-research-service` | `9741` | **INTERNAL ONLY** | `http://localhost:9741/actuator/health` |
| **AI Intelligent Service**| `enrichment-ai-intelligent-service` | `9742` | **INTERNAL ONLY** | `http://localhost:9742/actuator/health` |
| **Dataset Service** | `enrichment-dataset-service` | `9743` | **INTERNAL ONLY** | `http://localhost:9743/actuator/health` |
| **MySQL Database** | `enrichment-mysql` | `3306` | **INTERNAL ONLY** | `mysqladmin ping` |
| **Config Server** | `enrichment-config-server` | `9736` | **INTERNAL ONLY** | `http://localhost:9736/actuator/health` |
| **Discovery Server** | `enrichment-discovery-server` | `9737` | **INTERNAL ONLY** | `http://localhost:9737/actuator/health` |

---

## 2. Deployment Architecture & Flow

The repository is cloned directly onto the GCP VM, and Docker Compose builds and runs all services locally on the VM from the repository source trees. Neither Google Artifact Registry nor Docker Hub is required.

```
Developer (git push main)
   │
   ▼
GCP Compute Engine VM (/home/parvagr2/data-enrichment-ai-intelligence)
   │
   ├── 1. Fetch latest changes: git pull
   │
   ├── 2. Build local images: docker compose -f infrastructure/docker/docker-compose.prod.yml build
   │
   └── 3. Start containers:   docker compose -f infrastructure/docker/docker-compose.prod.yml up -d
```

---

## 3. GitHub Repository Configuration

Configure these values in:
**GitHub → Repository → Settings → Secrets and variables → Actions**

### A. GitHub Secrets (Sensitive Credentials)

| Secret Name | Type | What It Contains | Where It Comes From | Why It Is Needed |
| :--- | :--- | :--- | :--- | :--- |
| `GCP_SA_KEY` | **Secret** | Full JSON content of a GCP Service Account Key with `roles/artifactregistry.writer` | GCP IAM → Service Accounts → Keys | Allows GitHub Actions to authenticate and push built Docker images to Google Artifact Registry. |
| `GCP_SSH_PRIVATE_KEY` | **Secret** | OpenSSH private key (e.g. ED25519) | Generated locally (`ssh-keygen -t ed25519`) | Allows GitHub Actions to securely SSH into the GCP VM to run `deploy.sh`. |

### B. GitHub Variables (Non-Sensitive Configuration)

| Variable Name | Type | What It Contains | Where It Comes From | Why It Is Needed |
| :--- | :--- | :--- | :--- | :--- |
| `GCP_PROJECT_ID` | **Variable** | GCP Project ID (e.g., `my-enrichment-prod`) | Google Cloud Console | Identifies target GCP project for Artifact Registry. |
| `GCP_REGION` | **Variable** | Artifact Registry region (e.g., `us-central1`) | Chosen at repository creation | Constructs registry domain (`<region>-docker.pkg.dev`). |
| `GCP_ARTIFACT_REPOSITORY` | **Variable** | Name of the Artifact Registry repository (e.g., `enrichment-repo`) | GCP Artifact Registry | Identifies destination repository for container images. |
| `GCP_VM_HOST` | **Variable** | External IP address of your GCP VM | GCP Compute Engine console | Target host for SSH connection. |
| `GCP_VM_USER` | **Variable** | SSH username on the VM (e.g., `ubuntu`) | VM login user | Target username for SSH authentication. |
| `GCP_VM_APP_DIR` | **Variable** *(Optional)* | Project root directory on VM (defaults to `~/data-enrichment-ai-intelligence`) | Filesystem path on VM | Directory where `deploy.sh` and Compose files reside. |
| `GCP_VM_SSH_PORT` | **Variable** *(Optional)* | SSH port (defaults to `22`) | Custom SSH configuration | Allows non-standard SSH ports if hardened. |
| `NEXT_PUBLIC_GATEWAY_URL` | **Variable** *(Optional)* | Public Gateway URL baked into Frontend (e.g. `http://<VM_IP>:9738`) | Gateway IP/domain | Next.js build-time argument for API routing. |

---

## 4. Personal Access Token (PAT) Note

> **No GitHub Personal Access Token (PAT) is required for the current deployment setup.**
>
> Container images are built in GitHub Actions and pushed directly to Google Artifact Registry. Deployment assets (`docker-compose.prod.yml`, `deploy.sh`, `config/*.yml`) are synced to the VM by GitHub Actions over SSH. The VM does not pull code from GitHub during automated deployments.

---

## 5. SSH Authentication Setup

GitHub Actions connects to the VM using dedicated SSH key-pair authentication.

### Step 1: Generate an SSH Key Pair (on your local machine)
```bash
ssh-keygen -t ed25519 -f ./gcp_deploy_key -C "github-actions-deployer"
```

### Step 2: Put the Public Key on the GCP VM
Add the contents of `./gcp_deploy_key.pub` to the VM's `~/.ssh/authorized_keys`:
```bash
# On the GCP VM
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "<CONTENTS_OF_gcp_deploy_key.pub>" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### Step 3: Store the Private Key in GitHub Secrets
Copy the entire contents of `./gcp_deploy_key` (including `-----BEGIN OPENSSH PRIVATE KEY-----`) into GitHub Secret:
`GCP_SSH_PRIVATE_KEY`

---

## 6. Environment Variables & Production Secrets Matrix

| Variable | Target Location | Category | Source / Description |
| :--- | :--- | :--- | :--- |
| `IMAGE_PREFIX` | VM `.env` & GitHub Actions | Registry Configuration | `us-central1-docker.pkg.dev/<PROJECT_ID>/<REPO>/` (with trailing slash) |
| `IMAGE_TAG` | VM `.env` & GitHub Actions | Image Version | Set to immutable Git SHA (`${{ github.sha }}`) or `latest` |
| `APP_ENV` | VM `.env` | Environment | Set to `production` |
| `COMPOSE_PROJECT_NAME`| VM `.env` | Compose Config | Set to `data-enrichment-production` |
| `MYSQL_ROOT_PASSWORD` | VM `.env` | Application Secret | Generated once manually (`openssl rand -hex 16`) |
| `MYSQL_DATABASE` | VM `.env` | DB Config | Set to `enrichment_db` |
| `MYSQL_USER` | VM `.env` | DB Config | Set to `enrichment_user` |
| `MYSQL_PASSWORD` | VM `.env` | Application Secret | Generated once manually (`openssl rand -hex 16`) |
| `JWT_SECRET` | VM `.env` | Application Secret | 256-bit base64 secret (`openssl rand -base64 32`) |
| `JWT_EXPIRATION_MS` | VM `.env` | Auth Config | Default: `86400000` (24 hours) |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | VM `.env` | Security Config | `http://<VM_PUBLIC_IP>:3000,http://localhost:3000` |
| `NEXT_PUBLIC_GATEWAY_URL` | VM `.env` | Frontend Config | `http://<VM_PUBLIC_IP>:9738` |
| `GEMINI_API_KEY` | VM `.env` | External API Secret | Google AI Studio key (or `mock-key` if `AI_MOCK_MODE=true`) |
| `SEARCH_PROVIDER_NAME`| VM `.env` | Search Config | `tavily` or `mock` |
| `SEARCH_PROVIDER_API_KEY` | VM `.env` | External API Secret | Tavily API key (required if `tavily`) |

---

## 7. Initial Fresh GCP VM Setup (Step-by-Step)

Follow these steps once on a fresh machine:

### Step 1: Create the VM Instance
- **Machine Type**: `e2-standard-4` (4 vCPUs, 16 GB RAM).
- **Boot Disk**: Ubuntu 24.04 LTS, 50 GB SSD (`pd-balanced`).
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
Allow public access only to SSH (`22`), Frontend (`3000`), and Gateway (`9738`):
```bash
# SSH Access (Port 22)
gcloud compute firewall-rules create allow-enrichment-ssh \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:22 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# Frontend UI (Port 3000)
gcloud compute firewall-rules create allow-enrichment-frontend \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:3000 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# API Gateway (Port 9738)
gcloud compute firewall-rules create allow-enrichment-gateway \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:9738 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform
```

### Step 3: Install Docker CE & Docker Compose Plugin
Connect to the VM via SSH and execute:
```bash
# 1. Install prerequisites
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg

# 2. Add Docker official repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 3. Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# 4. Configure 4GB Swapfile (prevents JVM OOM kills)
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Step 4: Configure Project Directory & Environment
```bash
# Clone the repository once to initialize directories
git clone https://github.com/parvagr247/data-enrichment-ai-intelligence.git ~/data-enrichment-ai-intelligence
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

# Create production .env file
cp .env.example .env
nano .env
```

**Essential `.env` values to edit:**
```bash
VM_IP=$(curl -s ifconfig.me)

NEXT_PUBLIC_GATEWAY_URL=http://${VM_IP}:9738
GATEWAY_CORS_ALLOWED_ORIGINS=http://${VM_IP}:3000,http://localhost:3000

MYSQL_ROOT_PASSWORD=your_strong_root_password
MYSQL_PASSWORD=your_strong_user_password
JWT_SECRET=$(openssl rand -base64 32)

GEMINI_API_KEY=your_gemini_api_key
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=your_tavily_api_key
```

### Step 5: Perform Initial Deployment (Local VM Build)
```bash
cd ~/data-enrichment-ai-intelligence/infrastructure/docker
chmod +x deploy.sh
./deploy.sh
```

---

## 8. Subsequent Deployments

### A. Automated Deployment via GitHub Actions (Recommended)
Simply push your changes to `main`:
```bash
git push origin main
```
GitHub Actions will:
1. Run test suites and linting via `.github/workflows/ci.yml`.
2. Build and push all 8 tagged images to Google Artifact Registry.
3. SSH to the VM, sync files, and execute `./deploy.sh ${{ github.sha }}`.
4. Verify health endpoints and output deployment status.

### B. Manual Deployment (Directly on the VM)
If you need to redeploy or roll back manually on the VM:
```bash
# Connect to VM
ssh ubuntu@<VM_PUBLIC_IP>

# Run deployment script with latest or a specific Git SHA
cd ~/data-enrichment-ai-intelligence/infrastructure/docker
./deploy.sh <optional-commit-sha-or-latest>
```

---

## 9. Verification Commands

Run these on the VM to verify system health:

```bash
# 1. Check container health status overview
docker compose -f ~/data-enrichment-ai-intelligence/infrastructure/docker/docker-compose.prod.yml ps

# 2. Check public API Gateway health
curl -s http://localhost:9738/actuator/health | grep -o '"status":"[^"]*"'

# 3. Check public Frontend response
curl -s -I http://localhost:3000 | head -n 1

# 4. Check internal microservices via container execution
docker exec enrichment-config-server curl -s http://localhost:9736/actuator/health
docker exec enrichment-discovery-server curl -s http://localhost:9737/actuator/health
docker exec enrichment-auth-service curl -s http://localhost:9739/actuator/health
docker exec enrichment-research-service curl -s http://localhost:9741/actuator/health
docker exec enrichment-ai-intelligent-service curl -s http://localhost:9742/actuator/health
docker exec enrichment-dataset-service curl -s http://localhost:9743/actuator/health

# 5. Check MySQL health
docker exec enrichment-mysql mysqladmin ping -h localhost --silent && echo "MySQL is healthy"
```

---

## 10. Rollback Procedure

Because all Docker images are tagged with immutable Git commit SHAs, you can instantly rollback without rebuilding:

```bash
# SSH into VM
ssh ubuntu@<VM_PUBLIC_IP>
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

# Run deploy.sh with the previous stable commit SHA
./deploy.sh <PREVIOUS_STABLE_GIT_SHA>
```

Docker Compose will pull `<PREVIOUS_STABLE_GIT_SHA>` from Artifact Registry and update the containers. The MySQL database volume (`mysql_data`) is never destroyed.

---

## 11. Troubleshooting

| Issue | Diagnostic Command | Quick Resolution |
| :--- | :--- | :--- |
| **Docker not running** | `sudo systemctl status docker` | Start daemon: `sudo systemctl start docker`. |
| **Containers failing / exiting** | `docker compose -f docker-compose.prod.yml ps -a` | Inspect logs: `docker compose -f docker-compose.prod.yml logs <service-name>`. |
| **Port already in use (9738 or 3000)** | `sudo ss -tulpn | grep -E '9738|3000'` | Identify and stop conflicting process or old container: `docker stop <id>`. |
| **GitHub Actions SSH connection failure** | `ssh -v -i ~/.ssh/id_deploy ubuntu@<VM_IP>` | Ensure `GCP_SSH_PRIVATE_KEY` matches `~/.ssh/authorized_keys` and port 22 is open in GCP VPC firewall. |
| **`deploy.sh` Permission Denied** | `ls -l deploy.sh` | Add execution permission: `chmod +x deploy.sh`. |
| **Missing environment variable error** | `grep MYSQL_ROOT_PASSWORD .env` | Ensure `.env` exists in `infrastructure/docker/` with all required passwords set. |
| **Service returns 503 from Gateway** | `docker exec enrichment-discovery-server curl -s http://localhost:9737/eureka/apps` | Wait 20–30s for initial Eureka registration heartbeats to stabilize. |
| **Database connection refused on startup** | `docker logs enrichment-mysql` | MySQL takes ~25s to initialize on first boot. Dependency sequencing will wait until MySQL healthcheck passes. |

---

## 12. Security Notes

1. **No Credentials in Git**: Never commit `.env`, private SSH keys, service account keys, or API tokens to version control.
2. **Strict Port Boundaries**: Only ports `22` (SSH), `3000` (Frontend), and `9738` (Gateway) should be allowed through GCP VPC Firewall. All internal microservice ports (`3306`, `9736`, `9737`, `9739`, `9741`, `9742`, `9743`) must remain closed externally.
3. **Database Volume Preservation**: Never run `docker compose down -v` in production, as `-v` deletes the persistent MySQL volume (`mysql_data`). Use `docker compose down` or `deploy.sh` which preserves volumes.
4. **Least-Privilege GitHub Actions**: The GCP Service Account only requires `roles/artifactregistry.writer`. It does not need Project Owner or Compute Admin roles.
