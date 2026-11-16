#!/usr/bin/env bash
set -u
API="${API:-http://localhost:8080}"
ADMIN_TOKEN="${CRADESK_ADMIN_TOKEN:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
RUN=$(date +%H%M%S)
JAR=$(mktemp)
LAST=$(mktemp)
trap 'rm -f "$JAR" "$LAST"' EXIT

# Imprime el título de un paso de la demo.
step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$1"; }

# Devuelve el token CSRF guardado en la cookie XSRF-TOKEN del navegador simulado.
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -1; }

# Hace una petición con la sesión y el token CSRF, muestra el cuerpo y el código, y guarda el cuerpo.
call() {
  local method=$1 path=$2 body=${3:-}
  printf '\033[2m%s %s %s\033[0m\n' "$method" "$path" "$body"
  local args=(-s -o "$LAST" -w '%{http_code}' -X "$method" "$API$path" -b "$JAR" -c "$JAR"
    -H "X-XSRF-TOKEN: $(xsrf)")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local code
  code=$(curl "${args[@]}")
  cat "$LAST"; printf '\n← HTTP %s\n' "$code"
}

# Hace una petición sin cookies con una clave de API; opcionalmente envía un archivo como cuerpo.
call_with_key() {
  local key=$1 method=$2 path=$3 file=${4:-}
  printf '\033[2m%s %s (Authorization: Bearer crd_…) %s\033[0m\n' "$method" "$path" "$file"
  local args=(-s -o "$LAST" -w '%{http_code}' -X "$method" "$API$path" -H "Authorization: Bearer $key")
  [ -n "$file" ] && args+=(-H 'Content-Type: application/vnd.cyclonedx+json' --data-binary "@$file")
  local code
  code=$(curl "${args[@]}")
  head -c 600 "$LAST"; printf '\n← HTTP %s\n' "$code"
}

# Extrae del último cuerpo recibido el valor de texto de un campo JSON.
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "$LAST" | head -1; }

step "1. API health → 200"
call GET /actuator/health

step "2. Get a CSRF token (cookie XSRF-TOKEN) → 204"
call GET /api/auth/csrf

step "3. 'Try the demo' button: read-only session → 204, then the sample products → 200"
call POST /api/auth/demo
call GET /api/products

step "4. The demo account cannot write → 403"
call POST /api/products '{"name":"Hacked","productClass":"DEFAULT"}'

step "5. Log out → 204 (logout also clears the CSRF token, so get a fresh one → 204)"
call POST /api/auth/logout
call GET /api/auth/csrf

if [ -z "$ADMIN_TOKEN" ]; then
  printf '\nSet CRADESK_ADMIN_TOKEN (same value as the API) to run the invitation, API key and SBOM steps.\n'
  exit 0
fi

step "6. Platform admin invites a manufacturer → 201 (the code is shown only once)"
printf '\033[2mPOST /api/admin/invitations (X-Admin-Token)\033[0m\n'
code=$(curl -s -o "$LAST" -w '%{http_code}' -X POST "$API/api/admin/invitations" \
  -H "X-Admin-Token: $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"maker-'"$RUN"'@example.com"}')
cat "$LAST"; printf '\n← HTTP %s\n' "$code"
INVITE=$(field code)
[ -n "$INVITE" ] || { echo "Could not create the invitation. Is CRADESK_ADMIN_TOKEN the same as the API's?"; exit 1; }

step "7. Accept the invitation: company + account + session → 201"
call POST /api/auth/accept-invitation \
  '{"code":"'"$INVITE"'","email":"maker-'"$RUN"'@example.com","password":"a-long-enough-password","organizationName":"Maker '"$RUN"' GmbH"}'

step "8. Register an IoT product → 201 and its firmware version 1.0.0 → 201"
call POST /api/products '{"name":"Smart Lock","productClass":"IMPORTANT_CLASS_I"}'
PRODUCT=$(field id)
call POST "/api/products/$PRODUCT/versions" '{"version":"1.0.0","releasedAt":"2026-11-01"}'
VERSION=$(field id)

step "9. Create an API key for the CLI → 201 (shown only once)"
call POST /api/api-keys '{"name":"CI pipeline"}'
KEY=$(field key)
KEY_ID=$(field id)

step "10. The CI pipeline uploads the CycloneDX SBOM with the key → 201"
call_with_key "$KEY" PUT "/api/products/$PRODUCT/versions/$VERSION/sbom" "$HERE/sample-sbom.cdx.json"

step "11. SBOM summary → 200 and the first page of components → 200"
call GET "/api/products/$PRODUCT/versions/$VERSION/sbom"
call GET "/api/products/$PRODUCT/versions/$VERSION/sbom/components?size=3"

step "12. The upload queued a vulnerability scan (OSV + CISA KEV); ask again → 202 and wait until it is DONE"
call POST "/api/products/$PRODUCT/versions/$VERSION/scan"
for i in $(seq 1 30); do
  curl -s -o "$LAST" -b "$JAR" "$API/api/products/$PRODUCT/versions/$VERSION/vulnerabilities"
  STATUS=$(field status)
  printf '  scan status: %s
' "${STATUS:-none}"
  [ "$STATUS" = "DONE" ] || [ "$STATUS" = "FAILED" ] && break
  sleep 3
done

step "13. Vulnerabilities of the version, KEV first → 200"
call GET "/api/products/$PRODUCT/versions/$VERSION/vulnerabilities"

step "14. Findings to review (actively exploited, from CISA KEV) → 200"
call GET /api/findings

step "15. An SPDX document is rejected with the reason → 400"
SPDX=$(mktemp)
printf '{"spdxVersion":"SPDX-2.3","SPDXID":"SPDXRef-DOCUMENT","name":"x","packages":[]}' > "$SPDX"
call_with_key "$KEY" PUT "/api/products/$PRODUCT/versions/$VERSION/sbom" "$SPDX"
rm -f "$SPDX"

step "16. Revoke the key → 204, then the same key → 401"
call DELETE "/api/api-keys/$KEY_ID"
call_with_key "$KEY" GET /api/products

step "17. Public 'Request access' form → 202"
call POST /api/access-requests '{"email":"curious-'"$RUN"'@example.com","company":"Curious Devices"}'

printf '\n\033[1;32m✔ Demo finished.\033[0m\n'
