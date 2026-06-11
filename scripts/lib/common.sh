#!/usr/bin/env bash
# Shared helpers for start/stop/status scripts (Linux/macOS)

set -euo pipefail

# Resolve project root: scripts/ -> parent
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

load_env() {
  local env_file="${PROJECT_ROOT}/scripts/env"
  if [[ -f "${env_file}" ]]; then
    # shellcheck disable=SC1090
    set -a
    source "${env_file}"
    set +a
  fi

  SERVER_PORT="${SERVER_PORT:-8080}"
  SERVER_HOST="${SERVER_HOST:-127.0.0.1}"
  JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m}"
  META_DB_PATH="${META_DB_PATH:-./data}"
  JAR_PATH="${JAR_PATH:-./http-ingestion-boot/target/http-ingestion-service.jar}"
  START_TIMEOUT="${START_TIMEOUT:-120}"
  STOP_TIMEOUT="${STOP_TIMEOUT:-60}"
  HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi

  # Normalize relative paths against project root
  if [[ "${META_DB_PATH}" != /* ]]; then
    META_DB_PATH="${PROJECT_ROOT}/${META_DB_PATH#./}"
  fi
  if [[ "${JAR_PATH}" != /* ]]; then
    JAR_PATH="${PROJECT_ROOT}/${JAR_PATH#./}"
  fi

  PID_FILE="${META_DB_PATH}/http-ingestion.pid"
  LOG_FILE="${META_DB_PATH}/http-ingestion.log"
}

is_running() {
  if [[ ! -f "${PID_FILE}" ]]; then
    return 1
  fi
  local pid
  pid="$(cat "${PID_FILE}")"
  if [[ -z "${pid}" ]]; then
    return 1
  fi
  kill -0 "${pid}" 2>/dev/null
}

health_url() {
  echo "http://${SERVER_HOST}:${SERVER_PORT}${HEALTH_PATH}"
}

wait_for_health() {
  local timeout="${1:-${START_TIMEOUT}}"
  local url
  url="$(health_url)"
  local elapsed=0

  while (( elapsed < timeout )); do
    if curl -sf "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}
