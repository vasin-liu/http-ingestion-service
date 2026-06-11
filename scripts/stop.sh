#!/usr/bin/env bash
# Backward-compatible wrapper — use scripts/run.sh instead.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/run.sh" stop "$@"
