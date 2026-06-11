#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/deploy"
podman compose -f podman-compose.yml down -v
echo "Podman stack stopped."
