#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if ! command -v podman >/dev/null 2>&1; then
  echo "podman not found on PATH" >&2
  exit 1
fi

JAR="$ROOT/http-ingestion-boot/target/http-ingestion-service.jar"
if [[ "${1:-}" != "--skip-build" ]]; then
  ./mvnw -pl http-ingestion-boot -am package -DskipTests
fi
if [[ ! -f "$JAR" ]]; then
  echo "Missing jar: $JAR" >&2
  exit 1
fi

mkdir -p "$ROOT/data"
cd "$ROOT/deploy"
podman compose -f podman-compose.yml up -d

for _ in $(seq 1 60); do
  if podman compose -f podman-compose.yml exec -T postgres pg_isready -U postgres >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[deploy] Applying PostgreSQL schema from deploy/init-pg.sql ..."
podman compose -f podman-compose.yml exec -T postgres psql -U postgres -d postgres < "$ROOT/deploy/init-pg.sql"

HEALTH_URL="http://localhost:8080/actuator/health"
for _ in $(seq 1 90); do
  if curl -sf "$HEALTH_URL" >/dev/null; then
    echo ""
    echo "HTTP Ingestion is ready."
    echo "  UI/API : http://localhost:8080"
    echo "  Health : $HEALTH_URL"
    echo "  Mock   : http://localhost:8080/mock/..."
    echo "  PG     : localhost:5432 (postgres/postgres)"
    echo "  Kafka  : localhost:9092 (in-cluster kafka:9092)"
    echo ""
    echo "Teardown: ./scripts/podman/teardown.sh"
    exit 0
  fi
  sleep 2
done

echo "Service did not become healthy at $HEALTH_URL" >&2
exit 1
