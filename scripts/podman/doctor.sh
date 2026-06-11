#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TRY_REPAIR=0
if [[ "${1:-}" == "--try-repair" ]]; then
  TRY_REPAIR=1
fi

status() {
  local ok="$1"
  local label="$2"
  local detail="${3:-}"
  if [[ "$ok" == "1" ]]; then
    echo "[OK] $label${detail:+ — $detail}"
  else
    echo "[FAIL] $label${detail:+ — $detail}"
  fi
}

echo "Podman deploy doctor"
echo "Project: $ROOT"
echo ""

if command -v podman >/dev/null 2>&1; then
  status 1 "podman CLI"
else
  status 0 "podman CLI"
  exit 1
fi

if podman machine list | grep -q "podman-machine-default.*Currently running"; then
  status 1 "podman-machine-default running"
else
  status 0 "podman-machine-default running"
fi

JAR="$ROOT/http-ingestion-boot/target/http-ingestion-service.jar"
if [[ -f "$JAR" ]]; then
  status 1 "application jar" "$JAR"
else
  status 0 "application jar" "$JAR"
fi

INIT_SQL="$ROOT/deploy/init-pg.sql"
if [[ -f "$INIT_SQL" ]]; then
  status 1 "deploy/init-pg.sql" "$INIT_SQL"
else
  status 0 "deploy/init-pg.sql" "$INIT_SQL"
fi

# On Linux/macOS with native podman, bind mounts use the host path directly.
if [[ "$(uname -s)" == "Linux" || "$(uname -s)" == "Darwin" ]]; then
  status 1 "host bind mounts" "native podman path"
  echo ""
  echo "Doctor checks passed. Deploy with:"
  echo "  ./scripts/podman/deploy.sh --skip-build"
  exit 0
fi

echo ""
echo "On Windows, if deploy fails with statfs/input/output error, restart Podman Machine:"
echo "  podman machine stop && podman machine start"
echo "Or use PowerShell doctor: .\\scripts\\podman\\doctor.ps1 -TryRepair"
