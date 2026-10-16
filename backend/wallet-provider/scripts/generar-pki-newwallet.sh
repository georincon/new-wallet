#!/usr/bin/env bash
#
# Genera la CA raiz de New Wallet Wallet Provider y el certificado de firma (EC P-256) con el que
# se firman las Wallet Instance Attestations y Key Attestations. Crea el keystore JKS que
# consume el contenedor (docker/newwallet/keystore.jks, alias "newwallet-provider").
#
# Solo raiz.crt (parte publica) se sube a la lista de confianza. Las llaves privadas viven en
# docker/secretos/pki/ (gitignored).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/docker/secretos/pki"
KEYSTORE="$REPO_ROOT/docker/newwallet/keystore.jks"
STOREPASS="${NW_KEYSTORE_PASSWORD:-newwallet-keys}"
KEYPASS="${NW_KEY_PASSWORD:-newwallet-signing}"
ALIAS="newwallet-provider"

SUBJECT_ROOT="/C=CO/O=New Wallet/CN=New Wallet Provider Root CA"
SUBJECT_SIGN="/C=CO/O=New Wallet/CN=New Wallet Provider"

mkdir -p "$SECRETS_DIR" "$(dirname "$KEYSTORE")"
cd "$SECRETS_DIR"

if [[ -f raiz.key && "${1:-}" != "--force" ]]; then
  echo "Ya existe PKI en $SECRETS_DIR (usa --force para regenerar)"; exit 1
fi

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out raiz.key
openssl req -new -x509 -key raiz.key -sha256 -days 3650 -out raiz.crt -subj "$SUBJECT_ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out firma.key
openssl req -new -key firma.key -out firma.csr -subj "$SUBJECT_SIGN"
openssl x509 -req -in firma.csr -CA raiz.crt -CAkey raiz.key -CAcreateserial -days 730 -sha256 \
  -out firma.crt \
  -extfile <(printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature")
rm -f firma.csr raiz.srl

openssl pkcs12 -export -inkey firma.key -in firma.crt -certfile raiz.crt \
  -name "$ALIAS" -out firma.p12 -passout "pass:${KEYPASS}"

rm -f "$KEYSTORE"
keytool -importkeystore -srckeystore firma.p12 -srcstoretype PKCS12 -srcstorepass "$KEYPASS" \
  -srcalias "$ALIAS" -destkeystore "$KEYSTORE" -deststoretype JKS -deststorepass "$STOREPASS" \
  -destalias "$ALIAS" -destkeypass "$KEYPASS"

echo "Listo. Keystore: $KEYSTORE"
echo "Raiz publica (para la lista de confianza): $SECRETS_DIR/raiz.crt"
