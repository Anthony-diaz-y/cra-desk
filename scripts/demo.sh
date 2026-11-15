#!/usr/bin/env bash
# CRA Desk — live demo of the catalog API.
# Prereqs: Docker running, `docker compose up -d`, and the API started with
#   cd api && set -a && . ../.env && set +a && ./mvnw spring-boot:run
# Then, from the repo root:  bash scripts/demo.sh
set -u
API="${API:-http://localhost:8080}"
RUN=$(date +%H%M%S)   # unique suffix so the demo can run many times

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$1"; }
call() { # method path [json]
  local method=$1 path=$2 body=${3:-}
  printf '\033[2m%s %s %s\033[0m\n' "$method" "$path" "$body"
  if [ -n "$body" ]; then
    curl -s -w '\n← HTTP %{http_code}\n' -X "$method" "$API$path" -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -w '\n← HTTP %{http_code}\n' -X "$method" "$API$path"
  fi
}
id_of() { sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p' | head -1; }

step "1. API health"
call GET /actuator/health

step "2. Register a product (an IoT smart lock, CRA 'Important Class I')"
LOCK=$(curl -s -X POST "$API/api/products" -H 'Content-Type: application/json' \
  -d '{"name":"Demo Smart Lock '"$RUN"'","description":"Wi-Fi door lock","productClass":"IMPORTANT_CLASS_I"}' | id_of)
[ -n "$LOCK" ] || { echo "Could not create the demo product. Is the API running at $API?"; exit 1; }
call GET "/api/products/$LOCK"

step "3. Validation: blank name → 400 with per-field errors"
call POST /api/products '{"name":"   ","productClass":"DEFAULT"}'

step "4. Duplicate name, different case → 409 Conflict"
call POST /api/products '{"name":"demo smart lock '"$RUN"'","productClass":"DEFAULT"}'

step "5. Register two firmware versions"
call POST "/api/products/$LOCK/versions" '{"version":"1.0.0","releasedAt":"2026-09-01"}'
call POST "/api/products/$LOCK/versions" '{"version":" 1.1.0 ","releasedAt":"2026-11-01"}'

step "6. List versions (newest first; note ' 1.1.0 ' was trimmed)"
call GET "/api/products/$LOCK/versions"

step "7. Protected delete: a product with versions is a compliance record → 409"
call DELETE "/api/products/$LOCK"

step "8. Unknown product → 404 problem details"
call GET /api/products/00000000-0000-0000-0000-000000000000

printf '\n\033[1;32m✔ Demo finished.\033[0m\n'
