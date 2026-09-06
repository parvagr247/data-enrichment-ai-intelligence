#!/usr/bin/env bash
# ==============================================================================
# Production Deployment Script (GCP VM Local Build & Run)
# Data Enrichment AI Intelligence Platform
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "======================================================================"
echo " Starting Data Enrichment AI Intelligence Platform Deployment"
echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "======================================================================"

# 1. Load local .env if available
if [ -f .env ]; then
  echo "[INFO] Loading configuration from ${SCRIPT_DIR}/.env"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "[WARN] No .env file found at ${SCRIPT_DIR}/.env; using environment variables."
fi

# 2. Validate essential runtime secrets
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ] || [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "[ERROR] MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD must be configured in environment or .env"
  exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "[ERROR] JWT_SECRET must be configured in environment or .env"
  exit 1
fi

if [ -z "${VM_IP:-}" ]; then
  echo "[WARN] VM_IP is not set. Defaulting frontend gateway URL to localhost."
fi

# 3. Check Docker & Docker Compose installation
if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker command not found. Please install Docker."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] docker compose plugin not found. Please install docker-compose-plugin."
  exit 1
fi

# 4. Build container images locally from source
echo "----------------------------------------------------------------------"
echo " Building container images locally from repository source..."
echo "----------------------------------------------------------------------"
docker compose -f docker-compose.prod.yml build

# 5. Start / update containers in background
echo "----------------------------------------------------------------------"
echo " Starting production container stack..."
echo "----------------------------------------------------------------------"
docker compose -f docker-compose.prod.yml up -d --remove-orphans

# 6. Healthcheck verification loop
echo "----------------------------------------------------------------------"
echo " Verifying service health status..."
echo "----------------------------------------------------------------------"

MAX_ATTEMPTS=30
SLEEP_INTERVAL=5
ATTEMPT=1
ALL_HEALTHY=false

while [ ${ATTEMPT} -le ${MAX_ATTEMPTS} ]; do
  echo "[INFO] Health check attempt ${ATTEMPT}/${MAX_ATTEMPTS} (elapsed: $(( (ATTEMPT - 1) * SLEEP_INTERVAL ))s)..."

  # Check Gateway Actuator health
  GATEWAY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9738/actuator/health 2>/dev/null || echo "000")
  # Check Frontend HTTP status
  FRONTEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null || echo "000")

  # Inspect Docker Compose container health states
  UNHEALTHY_COUNT=$(docker compose -f docker-compose.prod.yml ps --format json 2>/dev/null | grep -ic '"unhealthy"' || true)
  EXITED_COUNT=$(docker compose -f docker-compose.prod.yml ps --format json 2>/dev/null | grep -ic '"exited"' || true)

  if [ "${GATEWAY_STATUS}" = "200" ] && [ "${FRONTEND_STATUS}" = "200" ] && [ "${UNHEALTHY_COUNT}" -eq 0 ] && [ "${EXITED_COUNT}" -eq 0 ]; then
    ALL_HEALTHY=true
    break
  fi

  echo "       Gateway (:9738): ${GATEWAY_STATUS} | Frontend (:3000): ${FRONTEND_STATUS} | Unhealthy: ${UNHEALTHY_COUNT} | Exited: ${EXITED_COUNT}"
  sleep ${SLEEP_INTERVAL}
  ATTEMPT=$(( ATTEMPT + 1 ))
done

echo "======================================================================"
echo " Container Status Summary"
echo "======================================================================"
docker compose -f docker-compose.prod.yml ps

if [ "${ALL_HEALTHY}" = true ]; then
  echo "======================================================================"
  echo " [SUCCESS] Deployment completed successfully! All services healthy."
  echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo " Gateway:  http://${VM_IP:-localhost}:9738"
  echo " Frontend: http://${VM_IP:-localhost}:3000"
  echo "======================================================================"
  exit 0
else
  echo "======================================================================"
  echo " [FAILURE] Deployment health verification failed after $(( MAX_ATTEMPTS * SLEEP_INTERVAL ))s."
  echo " Dumping recent logs of failing containers..."
  echo "======================================================================"
  docker compose -f docker-compose.prod.yml logs --tail=50
  exit 1
fi
