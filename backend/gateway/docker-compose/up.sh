#!/usr/bin/env bash
# Gateway HTTPS opcional (sslip.io, CA local). Requiere que el provider esté arriba (red newwallet-provider_default).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
docker compose up -d "$@"
