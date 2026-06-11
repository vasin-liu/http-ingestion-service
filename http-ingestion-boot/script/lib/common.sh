#!/usr/bin/env bash
# Shared runtime helpers for HTTP Ingestion Service packaged deployments (Linux/macOS).
# Sourced by bin/run.sh, health-check.sh.

set -euo pipefail

hi_now() {
  date '+%Y-%m-%d %H:%M:%S'
}

hi_log() {
  local level="$1"
  shift
  printf '[%s] [%s] %s\n' "$(hi_now)" "$level" "$*" >&2
}

hi_log_info() { hi_log "INFO" "$@"; }
hi_log_warn() { hi_log "WARN" "$@"; }
hi_log_error() { hi_log "ERROR" "$@"; }

hi_ensure_dir() {
  local dir="$1"
  if [[ ! -d "$dir" ]]; then
    mkdir -p "$dir" || {
      hi_log_error "Failed to create directory: $dir"
      return 1
    }
  fi
}

hi_resolve_path_from_root() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s' "$path"
  else
    printf '%s' "${ROOT_DIR}/${path#./}"
  fi
}

hi_init_paths() {
  local bin_dir="$1"
  if [[ -n "${HI_BIN_DIR:-}" ]]; then
    BIN_DIR="$(cd "$HI_BIN_DIR" && pwd)"
  else
    BIN_DIR="$(cd "$bin_dir" && pwd)"
  fi
  if [[ -n "${HI_PACKAGE_ROOT:-}" ]]; then
    ROOT_DIR="$(cd "$HI_PACKAGE_ROOT" && pwd)"
  else
    ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"
  fi
  LOG_DIR="${HI_LOG_DIR:-${ROOT_DIR}/logs}"
  CONF_DIR="${ROOT_DIR}/conf"
  DATA_DIR="${HI_DATA_DIR:-${ROOT_DIR}/data}"
  JVMDUMP_DIR="${HI_JVMDUMP_DIR:-${ROOT_DIR}/jvmdump}"
  HI_SERVICE_NAME="${HI_SERVICE_NAME:-http-ingestion-service}"
  LOG_FILE="${HI_LOG_FILE:-${LOG_DIR}/${HI_SERVICE_NAME}.log}"
  PID_FILE="${HI_PID_FILE:-${ROOT_DIR}/${HI_SERVICE_NAME}.pid}"
  HI_SERVER_PORT="${HI_SERVER_PORT:-8080}"
  HI_HEALTH_URL="${HI_HEALTH_URL:-http://127.0.0.1:${HI_SERVER_PORT}/actuator/health}"
  HI_START_WAIT_SEC="${HI_START_WAIT_SEC:-30}"
  HI_STOP_TIMEOUT_SEC="${HI_STOP_TIMEOUT_SEC:-60}"
  HI_DAEMON="${HI_DAEMON:-1}"
  HI_JAVA_MIN_VERSION="${HI_JAVA_MIN_VERSION:-21}"
  HI_JAVA_PROMPT="${HI_JAVA_PROMPT:-0}"
  HI_HEAP_MIN="${HI_HEAP_MIN:-512m}"
  HI_HEAP_MAX="${HI_HEAP_MAX:-1g}"
  META_DB_PATH="${HI_META_DB_PATH:-${DATA_DIR}}"
  LOG_DIR="$(hi_resolve_path_from_root "$LOG_DIR")"
  DATA_DIR="$(hi_resolve_path_from_root "$DATA_DIR")"
  META_DB_PATH="$(hi_resolve_path_from_root "$META_DB_PATH")"
  LOG_FILE="$(hi_resolve_path_from_root "$LOG_FILE")"
  PID_FILE="$(hi_resolve_path_from_root "$PID_FILE")"
  JVMDUMP_DIR="$(hi_resolve_path_from_root "$JVMDUMP_DIR")"
}

hi_apply_runtime_env() {
  export SERVER_PORT="${HI_SERVER_PORT}"
  export META_DB_PATH
  export EXTERNAL_PG_URL="${EXTERNAL_PG_URL:-}"
  export EXTERNAL_PG_USER="${EXTERNAL_PG_USER:-}"
  export EXTERNAL_PG_PASSWORD="${EXTERNAL_PG_PASSWORD:-}"
}

hi_load_env_file() {
  local env_file="$1"
  hi_log_info "Loading configuration: ${env_file}"
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
  HI_JAVA_HOME="${HI_JAVA_HOME:-${JAVA_HOME:-}}"
  HI_SERVER_PORT="${HI_SERVER_PORT:-${SERVER_PORT:-8080}}"
  HI_META_DB_PATH="${HI_META_DB_PATH:-${META_DB_PATH:-../data}}"
  HI_JVM_OPTS="${HI_JVM_OPTS:-${JAVA_OPTS:-}}"
  HI_START_WAIT_SEC="${HI_START_WAIT_SEC:-${START_TIMEOUT:-30}}"
  HI_STOP_TIMEOUT_SEC="${HI_STOP_TIMEOUT_SEC:-${STOP_TIMEOUT:-60}}"
  if [[ -z "${HI_HEALTH_URL:-}" && -n "${HEALTH_PATH:-}" ]]; then
    local host="${SERVER_HOST:-127.0.0.1}"
    HI_HEALTH_URL="http://${host}:${HI_SERVER_PORT}${HEALTH_PATH}"
  fi
}

hi_load_config() {
  local env_file="${ROOT_DIR}/conf/service.env"
  if [[ -f "$env_file" ]]; then
    hi_load_env_file "$env_file"
  elif [[ -f "${ROOT_DIR}/scripts/env" ]]; then
    hi_load_env_file "${ROOT_DIR}/scripts/env"
  fi
  hi_init_paths "${BIN_DIR}"
}

hi_java_major_version() {
  local java_cmd="$1"
  "$java_cmd" -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{print ($1 == "1") ? $2 : $1}' | head -n 1
}

hi_java_version_ok() {
  local java_cmd="$1"
  local min_version="$2"
  local major
  major="$(hi_java_major_version "$java_cmd")"
  [[ -n "$major" && "$major" -ge "$min_version" ]]
}

hi_resolve_java() {
  local java_cmd=""
  local candidate=""

  if [[ -n "${HI_JAVA_HOME:-}" && -x "${HI_JAVA_HOME}/bin/java" ]]; then
    candidate="${HI_JAVA_HOME}/bin/java"
    if hi_java_version_ok "$candidate" "$HI_JAVA_MIN_VERSION"; then
      java_cmd="$candidate"
    else
      hi_log_warn "HI_JAVA_HOME Java version < ${HI_JAVA_MIN_VERSION}: ${candidate}"
    fi
  fi

  if [[ -z "$java_cmd" && -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    candidate="${JAVA_HOME}/bin/java"
    if hi_java_version_ok "$candidate" "$HI_JAVA_MIN_VERSION"; then
      java_cmd="$candidate"
    else
      hi_log_warn "JAVA_HOME Java version < ${HI_JAVA_MIN_VERSION}: ${candidate}"
    fi
  fi

  if [[ -z "$java_cmd" ]] && command -v java >/dev/null 2>&1; then
    candidate="$(command -v java)"
    if hi_java_version_ok "$candidate" "$HI_JAVA_MIN_VERSION"; then
      java_cmd="$candidate"
    fi
  fi

  if [[ -z "$java_cmd" && "${HI_JAVA_PROMPT}" == "1" && -t 0 ]]; then
    hi_log_warn "JDK ${HI_JAVA_MIN_VERSION}+ not found in HI_JAVA_HOME, JAVA_HOME, or PATH."
    while [[ -z "$java_cmd" ]]; do
      read -r -p "Enter JDK ${HI_JAVA_MIN_VERSION}+ home path: " input_home
      if [[ -x "${input_home}/bin/java" ]] && hi_java_version_ok "${input_home}/bin/java" "$HI_JAVA_MIN_VERSION"; then
        java_cmd="${input_home}/bin/java"
        hi_log_info "Using interactive Java: ${java_cmd}"
      else
        hi_log_error "Invalid JDK path or version: ${input_home}"
      fi
    done
  fi

  if [[ -z "$java_cmd" ]]; then
    hi_log_error "JDK ${HI_JAVA_MIN_VERSION}+ is required."
    hi_log_error "Set HI_JAVA_HOME in conf/service.env (see conf/service.env.example)."
    return 1
  fi

  JAVA_CMD="$java_cmd"
  hi_log_info "Java: $("$JAVA_CMD" -version 2>&1 | head -n 1)"
}

hi_find_app_jar() {
  local exact="${BIN_DIR}/${HI_SERVICE_NAME}.jar"
  if [[ -f "$exact" ]]; then
    APP_JAR="$exact"
    hi_log_info "Application JAR: ${APP_JAR}"
    return 0
  fi
  APP_JAR="$(find "${BIN_DIR}" -maxdepth 1 -type f -name "${HI_SERVICE_NAME}-*.jar" 2>/dev/null | sort -V | tail -n 1)"
  if [[ -z "$APP_JAR" ]]; then
    hi_log_error "Application JAR not found in ${BIN_DIR}"
    return 1
  fi
  hi_log_info "Application JAR: ${APP_JAR}"
}

hi_build_jvm_opts() {
  JVM_OPTS=()
  if [[ "${HI_DAEMON}" == "0" ]]; then
    JVM_OPTS+=(-XX:MaxRAMPercentage=95.0)
  else
    JVM_OPTS+=(-Xms"${HI_HEAP_MIN}" -Xmx"${HI_HEAP_MAX}")
    JVM_OPTS+=(-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m)
  fi
  JVM_OPTS+=(-XX:+UseG1GC)
  JVM_OPTS+=(-XX:+HeapDumpOnOutOfMemoryError)
  JVM_OPTS+=(-XX:HeapDumpPath="${JVMDUMP_DIR}/${HI_SERVICE_NAME}.hprof")
  if [[ -n "${HI_JVM_OPTS:-}" ]]; then
    # shellcheck disable=SC2206
    JVM_OPTS+=(${HI_JVM_OPTS})
  fi
}

hi_build_spring_args() {
  SPRING_ARGS=()
  if [[ -d "${CONF_DIR}" ]]; then
    SPRING_ARGS+=(--spring.config.additional-location="${CONF_DIR}/")
  fi
  if [[ -n "${HI_SPRING_PROFILES_ACTIVE:-}" ]]; then
    SPRING_ARGS+=(--spring.profiles.active="${HI_SPRING_PROFILES_ACTIVE}")
  fi
  if [[ -f "${CONF_DIR}/logback-spring.xml" ]]; then
    SPRING_ARGS+=(--logging.config="${CONF_DIR}/logback-spring.xml")
  fi
  if [[ -n "${HI_SERVER_PORT:-}" ]]; then
    SPRING_ARGS+=(--server.port="${HI_SERVER_PORT}")
  fi
  if [[ -n "${HI_SPRING_ARGS:-}" ]]; then
    # shellcheck disable=SC2206
    SPRING_ARGS+=(${HI_SPRING_ARGS})
  fi
}

hi_read_pid() {
  if [[ -f "$PID_FILE" ]]; then
    tr -d '[:space:]' < "$PID_FILE"
  fi
}

hi_is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

hi_service_status() {
  local pid
  pid="$(hi_read_pid)"
  if hi_is_running "$pid"; then
    hi_log_info "${HI_SERVICE_NAME} is running (pid=${pid})"
    hi_log_info "Log file: ${LOG_FILE}"
    hi_log_info "Health URL: ${HI_HEALTH_URL}"
    hi_log_info "Meta DB path: ${META_DB_PATH}"
    return 0
  fi
  if [[ -n "$pid" ]]; then
    hi_log_warn "Stale PID file removed: ${PID_FILE} (pid=${pid})"
    rm -f "$PID_FILE"
  fi
  hi_log_info "${HI_SERVICE_NAME} is not running"
  return 1
}

hi_health_check_quiet() {
  local code body
  body="$(curl -s --connect-timeout 3 --max-time 8 "${HI_HEALTH_URL}" 2>/dev/null || true)"
  [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]
}

hi_service_start() {
  local pid
  pid="$(hi_read_pid)"
  if hi_is_running "$pid"; then
    hi_log_info "${HI_SERVICE_NAME} already running (pid=${pid})"
    return 0
  fi
  [[ -n "$pid" ]] && rm -f "$PID_FILE"

  hi_ensure_dir "$LOG_DIR"
  hi_ensure_dir "$DATA_DIR"
  hi_ensure_dir "$META_DB_PATH"
  hi_ensure_dir "$JVMDUMP_DIR"
  hi_resolve_java
  hi_find_app_jar
  hi_apply_runtime_env
  hi_build_jvm_opts
  hi_build_spring_args

  local -a cmd
  hi_log_info "Launch mode: java -jar"
  cmd=("$JAVA_CMD" "${JVM_OPTS[@]}" -jar "$APP_JAR" "${SPRING_ARGS[@]}")

  if [[ "${HI_DAEMON}" == "0" ]]; then
    hi_log_info "Starting ${HI_SERVICE_NAME} in foreground ..."
    hi_log_info "Press Ctrl+C to stop."
    exec "${cmd[@]}"
  fi

  hi_log_info "Starting ${HI_SERVICE_NAME} in background ..."
  hi_log_info "Stdout/stderr -> ${LOG_FILE}"
  nohup "${cmd[@]}" >> "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  hi_log_info "Started with pid=$(cat "$PID_FILE")"

  local waited=0
  while [[ "$waited" -lt "${HI_START_WAIT_SEC}" ]]; do
    sleep 1
    waited=$((waited + 1))
    if hi_health_check_quiet; then
      hi_log_info "Health check passed: ${HI_HEALTH_URL}"
      return 0
    fi
  done
  hi_log_warn "Process started but health check not yet OK (${HI_HEALTH_URL})"
  hi_log_warn "Tail logs: tail -f ${LOG_FILE}"
}

hi_service_stop() {
  local pid
  pid="$(hi_read_pid)"
  if ! hi_is_running "$pid"; then
    hi_log_info "${HI_SERVICE_NAME} is not running"
    rm -f "$PID_FILE"
    return 0
  fi

  hi_log_info "Stopping ${HI_SERVICE_NAME} (pid=${pid}) ..."
  kill -TERM "$pid" 2>/dev/null || true
  local waited=0
  while hi_is_running "$pid" && [[ "$waited" -lt "${HI_STOP_TIMEOUT_SEC}" ]]; do
    sleep 1
    waited=$((waited + 1))
  done
  if hi_is_running "$pid"; then
    hi_log_warn "Graceful stop timed out after ${HI_STOP_TIMEOUT_SEC}s; sending SIGKILL"
    kill -KILL "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  hi_log_info "${HI_SERVICE_NAME} stopped"
}

hi_service_restart() {
  hi_service_stop
  hi_service_start
}

hi_health_check() {
  hi_log_info "Checking ${HI_HEALTH_URL} ..."
  local tmp body code
  tmp="$(mktemp)"
  code="$(curl -s -o "$tmp" -w '%{http_code}' --connect-timeout 5 --max-time 10 "${HI_HEALTH_URL}" 2>/dev/null || echo "000")"
  if [[ "$code" != "200" ]]; then
    hi_log_error "Health check failed: HTTP ${code} (${HI_HEALTH_URL})"
    rm -f "$tmp"
    return 1
  fi
  body="$(cat "$tmp")"
  rm -f "$tmp"
  if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$body"; then
    hi_log_error "Health check failed: response status is not UP"
    hi_log_error "Body: ${body}"
    return 1
  fi
  hi_log_info "Health check OK: ${body}"
  return 0
}

hi_print_usage() {
  cat <<EOF

HTTP Ingestion Service runtime script
  Package root : ${ROOT_DIR}
  Service      : ${HI_SERVICE_NAME}
  Config file  : ${ROOT_DIR}/conf/service.env (optional, see service.env.example)

Commands:
  start [0|1]   Start service (1=background default, 0=foreground)
  stop          Stop service gracefully
  restart       Restart service
  status        Show running state
  health        Run HTTP health check
  help          Show this help

Environment overrides (also set in conf/service.env):
  HI_JAVA_HOME, HI_SERVER_PORT, HI_META_DB_PATH, HI_SPRING_PROFILES_ACTIVE, HI_DAEMON, HI_LOG_DIR

EOF
}

hi_parse_start_daemon_arg() {
  if [[ "${1:-}" == "0" || "${1:-}" == "1" ]]; then
    HI_DAEMON="$1"
  fi
  if [[ "${HI_FOREGROUND:-0}" == "1" ]]; then
    HI_DAEMON=0
  fi
}

hi_main() {
  local action="${1:-help}"
  hi_init_paths "$HI_BIN_DIR"
  hi_load_config
  hi_parse_start_daemon_arg "${2:-}"

  case "$action" in
    start) hi_service_start ;;
    stop) hi_service_stop ;;
    restart) hi_service_restart ;;
    status) hi_service_status ;;
    health) hi_health_check ;;
    help|-h|--help) hi_print_usage ;;
    *)
      hi_log_error "Unknown command: ${action}"
      hi_print_usage
      return 1
      ;;
  esac
}
