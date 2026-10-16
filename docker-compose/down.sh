#!/usr/bin/env bash
# Detiene el stack unificado (-v borra también volúmenes). Con --all detiene además el de la Registraduría.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
REG="$(cd .. && pwd)/../eudi-srv-pid-registraduria/docker-compose"
ALL=0; ARGS=()
for a in "$@"; do [[ "$a" == "--all" ]] && ALL=1 || ARGS+=("$a"); done
docker compose down "${ARGS[@]}"
if [[ $ALL == 1 ]]; then (cd "$REG" && docker compose down "${ARGS[@]}"); fi
