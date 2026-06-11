#!/usr/bin/env bash
# HTTP health check for keepalive, systemd, and manual checks.

set -euo pipefail

HI_BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/common.sh
source "${HI_BIN_DIR}/lib/common.sh"

hi_init_paths "$HI_BIN_DIR"
hi_load_config
hi_health_check
