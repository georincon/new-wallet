#!/usr/bin/env bash
# Levanta todo New Wallet: el stack unificado (onboarding + provider) y, aparte, el stack de la Registraduría.
#   ./up.sh                          levanta todo (si algún puerto choca, aborta sin tocar nada)
#   NW_APPLY_LOGIN_BINDING=1 ./up.sh además activa el login automático (issuer_state) de newwallet-app
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
ROOT="$(cd .. && pwd)"
REG="$ROOT/../eudi-srv-pid-registraduria/docker-compose"

# 0) Puertos reconfigurados por una ejecución anterior (./check-ports.sh --fix los guarda en .env)
[[ -f .env ]] && { set -a; . ./.env; set +a; }

# 1) IP local del PC (la usa el teléfono) y URL pública del provider
if [[ -z "${NW_HOST_IP:-}" ]]; then
  NW_HOST_IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)"
fi
export NW_HOST_IP
export NW_PROVIDER_PUBLIC_URL="${NW_PROVIDER_PUBLIC_URL:-http://${NW_HOST_IP}:${NW_PROVIDER_PORT:-8097}}"
echo ">> IP del PC: $NW_HOST_IP   provider: $NW_PROVIDER_PUBLIC_URL"

# 2) Verificación de puertos: si algo choca, se reconfigura a un puerto libre (los parametrizados) y se relanza
if ! ./check-ports.sh; then
  echo ">> Intentando reconfigurar los puertos que chocan..."
  if ! ./check-ports.sh --fix; then
    echo; echo "Quedan colisiones que no se pueden mover solas: detén ese servicio y vuelve a ejecutar ./up.sh"; exit 1
  fi
  [[ -f .env ]] && { set -a; . ./.env; set +a; }
  export NW_PROVIDER_PUBLIC_URL="http://${NW_HOST_IP}:${NW_PROVIDER_PORT:-8097}"
fi
export NW_ONBOARDING_BACKEND_PORT="${BACKEND_PORT:-8195}"

# 3) PKIs propias (si faltan)
[[ -f "$ROOT/backend/wallet-provider/docker/newwallet/keystore.jks" ]] || { echo ">> PKI del provider..."; "$ROOT/backend/wallet-provider/scripts/generar-pki-newwallet.sh"; }
[[ -f "$REG/pki-dev/ca.crt" ]] || { echo ">> PKI del issuer Registraduría..."; (cd "$REG" && ./scripts/generar-pki-registraduria.sh); }

# 4) Provider suelto -> proyecto unificado
if [[ "$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' newwallet-provider 2>/dev/null || true)" == "newwallet-provider" ]]; then
  echo ">> Migrando el provider suelto al proyecto 'newwallet' (se recrean sus contenedores y su BD de desarrollo)..."
  docker compose -f "$ROOT/backend/wallet-provider/docker/docker-compose.yaml" down
fi

# 5) Stack unificado (onboarding + provider) y stack de la Registraduría (proyecto propio, en su carpeta)
docker compose up -d --build
(cd "$REG" && docker compose up -d --build)
docker compose ps
(cd "$REG" && docker compose ps)

# 6) Login automático (issuer_state) para newwallet-app: opt-in, porque quita la contraseña a ese cliente
if [[ "${NW_APPLY_LOGIN_BINDING:-0}" == "1" ]]; then
  echo ">> Esperando a Keycloak..."
  for _ in $(seq 1 60); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' keycloak-reg 2>/dev/null)" == "healthy" ]] && break; sleep 3
  done
  "$REG/keycloak/apply-issuer-state-binding.sh"
else
  echo ">> Login automático NO activado. Para activarlo (tras cada recreación de keycloak-reg):"
  echo "   $REG/keycloak/apply-issuer-state-binding.sh"
fi

echo
echo "App: ./gradlew :app:assembleDevDebug -PbackendHost=$NW_HOST_IP -PbackendPort=${BACKEND_PORT:-8195} -Pnw.walletprovider.url=$NW_PROVIDER_PUBLIC_URL -Pnw.issuer.url=$(grep -m1 -oP 'ISSUER_PUBLICURL=\K\S+' "$REG/docker-compose.yaml")"
