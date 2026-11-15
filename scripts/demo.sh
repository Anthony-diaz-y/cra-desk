#!/usr/bin/env bash
set -u
API="${API:-http://localhost:8080}"
ADMIN_TOKEN="${CRADESK_ADMIN_TOKEN:-}"
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

# Hace una petición sin cookies usando una clave de API, muestra el cuerpo y el código.
call_with_key() {
  local key=$1 method=$2 path=$3
  printf '\033[2m%s %s (Authorization: Bearer crd_…)\033[0m\n' "$method" "$path"
  local code
  code=$(curl -s -o "$LAST" -w '%{http_code}' -X "$method" "$API$path" -H "Authorization: Bearer $key")
  cat "$LAST"; printf '\n← HTTP %s\n' "$code"
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
  printf '\nSet CRADESK_ADMIN_TOKEN (same value as the API) to run the invitation and API key steps.\n'
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

step "8. Register an IoT product in the new company → 201"
call POST /api/products '{"name":"Smart Lock","productClass":"IMPORTANT_CLASS_I"}'

step "9. Create an API key for the CLI → 201 (shown only once)"
call POST /api/api-keys '{"name":"CI pipeline"}'
KEY=$(field key)
KEY_ID=$(field id)

step "10. The CLI calls the API with the key, no cookies → 200"
call_with_key "$KEY" GET /api/products

step "11. Revoke the key → 204, then the same key → 401"
call DELETE "/api/api-keys/$KEY_ID"
call_with_key "$KEY" GET /api/products

step "12. Public 'Request access' form → 202"
call POST /api/access-requests '{"email":"curious-'"$RUN"'@example.com","company":"Curious Devices"}'

printf '\n\033[1;32m✔ Demo finished.\033[0m\n'
