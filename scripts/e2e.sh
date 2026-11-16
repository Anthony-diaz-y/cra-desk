#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API_PORT="${E2E_API_PORT:-8081}"
WEB_PORT="${E2E_WEB_PORT:-3100}"
FEEDS_PORT="${FAKE_FEEDS_PORT:-4010}"
LOGS="$ROOT/web/test-results/e2e-logs"
PIDS=()

# Detiene los procesos que arrancó el script.
cleanup() {
  local status=$?
  for pid in "${PIDS[@]:-}"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done
  exit "$status"
}
trap cleanup EXIT

# Espera hasta que la URL responda o se acabe el tiempo.
wait_for() {
  local url=$1 name=$2
  for _ in $(seq 1 120); do
    curl -s -o /dev/null "$url" && return 0
    sleep 1
  done
  echo "$name did not start; see $LOGS" >&2
  exit 1
}

# Indica si un puerto local ya está en uso.
port_busy() {
  curl -s -o /dev/null --max-time 1 "http://127.0.0.1:$1" && return 0 || return 1
}

for port in "$API_PORT" "$WEB_PORT" "$FEEDS_PORT"; do
  if port_busy "$port"; then
    echo "Port $port is already in use. Stop that process or set E2E_API_PORT / E2E_WEB_PORT / FAKE_FEEDS_PORT." >&2
    exit 1
  fi
done

set -a
. "$ROOT/.env"
set +a
mkdir -p "$LOGS"
export CRADESK_ADMIN_TOKEN="${E2E_ADMIN_TOKEN:-e2e-admin-token-0123456789abcdefghijklmnop}"

docker compose -f "$ROOT/docker-compose.yml" up -d --wait postgres > /dev/null
docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -q -U cradesk -d cradesk \
  -c "DROP DATABASE IF EXISTS cradesk_e2e WITH (FORCE)" -c "CREATE DATABASE cradesk_e2e" > /dev/null

(cd "$ROOT/api" && ./mvnw -q -DskipTests package)
(cd "$ROOT/web" && CRADESK_API_URL="http://localhost:$API_PORT" npm run build > "$LOGS/web-build.log" 2>&1)

FAKE_FEEDS_PORT="$FEEDS_PORT" exec node "$ROOT/web/e2e/fake-feeds.mjs" > "$LOGS/fake-feeds.log" 2>&1 &
PIDS+=($!)

(cd "$ROOT/api" && SERVER_PORT="$API_PORT" \
  DATABASE_URL="jdbc:postgresql://localhost:5432/cradesk_e2e" \
  CRADESK_OSV_BASE_URL="http://127.0.0.1:$FEEDS_PORT" \
  CRADESK_KEV_URL="http://127.0.0.1:$FEEDS_PORT/kev.json" \
  CRADESK_AI_PROVIDER=template \
  exec java -jar target/api-0.0.1-SNAPSHOT.jar > "$LOGS/api.log" 2>&1) &
PIDS+=($!)

(cd "$ROOT/web" && CRADESK_API_URL="http://localhost:$API_PORT" exec node node_modules/next/dist/bin/next start -p "$WEB_PORT" > "$LOGS/web.log" 2>&1) &
PIDS+=($!)

wait_for "http://127.0.0.1:$API_PORT/actuator/health" "API"
wait_for "http://127.0.0.1:$WEB_PORT/" "Web"

cd "$ROOT/web"
npx playwright install chromium > /dev/null
E2E_BASE_URL="http://localhost:$WEB_PORT" E2E_API_URL="http://localhost:$API_PORT" npx playwright test
