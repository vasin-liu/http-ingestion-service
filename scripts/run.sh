#!/usr/bin/env bash
# Dev-mode wrapper: run from project root against http-ingestion-boot/target/*.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
export HI_PACKAGE_ROOT="${PROJECT_ROOT}"
export HI_BIN_DIR="${PROJECT_ROOT}/http-ingestion-boot/target"

exec "${PROJECT_ROOT}/http-ingestion-boot/script/run.sh" "$@"
