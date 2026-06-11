#!/usr/bin/env bash
# Start/stop the HTTP Ingestion Service process.

set -euo pipefail

HI_BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${HI_BIN_DIR}/lib/common.sh"

hi_main "$@"
