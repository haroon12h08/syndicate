#!/usr/bin/env bash
#
# Starts Syndicate.
#
#   ./start.sh            start it (builds on first run)
#   ./start.sh --rebuild  rebuild the image, then start
#   ./start.sh --logs     follow the log after starting
#   ./start.sh --stop     stop it
#
# Needs Docker. Everything else — secrets, the database, migrations — is handled here.
set -euo pipefail
cd "$(dirname "$0")"

BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
step() { printf "%s==>%s %s%s%s\n" "$BLUE" "$RESET" "$BOLD" "$1" "$RESET"; }
ok()   { printf "  %s✓%s %s\n" "$GREEN" "$RESET" "$1"; }
fail() { printf "  %s✗%s %s\n" "$RED" "$RESET" "$1"; exit 1; }
note() { printf "    %s%s%s\n" "$DIM" "$1" "$RESET"; }

REBUILD=""; FOLLOW=""
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD="--build" ;;
    --logs)    FOLLOW="1" ;;
    --stop|--down) step "Stopping"; docker compose down; ok "Stopped. Your data is kept."; exit 0 ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) fail "Unknown option: $arg" ;;
  esac
done

step "Checking Docker"
command -v docker >/dev/null 2>&1 || fail "Docker is not installed. See https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required (try: docker compose version)"
docker info >/dev/null 2>&1 || fail "Docker is installed but not running. Start Docker and try again."
ok "Docker is ready"

# ---------------------------------------------------------------- secrets
# Generated once and kept in .env. Nobody has to invent a password to run this.
secret() { openssl rand -base64 "$1" 2>/dev/null | tr -d '\n=+/' || head -c "$1" /dev/urandom | base64 | tr -d '\n=+/'; }

step "Preparing configuration"
touch .env
add_if_missing() {
  if ! grep -q "^$1=" .env 2>/dev/null; then
    printf '%s=%s\n' "$1" "$2" >> .env
    ok "Generated $1"
  fi
}
add_if_missing SYNDICATE_DB_PASSWORD "$(secret 24)"
add_if_missing SYNDICATE_JWT_SECRET "$(secret 48)"
add_if_missing SYNDICATE_PORT 8080
set -a; . ./.env; set +a
PORT="${SYNDICATE_PORT:-8080}"
add_if_missing SYNDICATE_PUBLIC_URL "http://localhost:${PORT}"
set -a; . ./.env; set +a
ok "Configuration ready (.env)"

step "Starting Syndicate"
note "postgres · syndicate (web app, API and background work in one process)"
note "The first run builds the image; expect a few minutes."
docker compose up -d --build ${REBUILD:+--force-recreate}

step "Waiting for it to come up"
DEADLINE=$(( $(date +%s) + 600 ))
until curl -fsS "http://localhost:${PORT}/api/health" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    printf "\n"; docker compose logs --tail=40 app || true
    fail "Syndicate did not start in time. Run 'docker compose logs -f app' to see why."
  fi
  if ! docker compose ps --status running --services 2>/dev/null | grep -q '^app$'; then
    printf "\n"; docker compose logs --tail=40 app || true
    fail "Syndicate stopped unexpectedly."
  fi
  printf "."; sleep 3
done
printf "\n"; ok "API is healthy and the database is migrated"

# The app shell must actually be served, not just the API: a build that forgot the web app
# would otherwise look like a successful start.
if ! curl -fsS "http://localhost:${PORT}/" | grep -qi "<div id=\"root\""; then
  fail "The API is up but the web app is not being served. Try ./start.sh --rebuild"
fi
ok "Web app is being served"

printf "\n%sSyndicate is running at %shttp://localhost:%s%s\n\n" "$BOLD" "$RESET$BOLD" "$PORT" "$RESET"
printf "  %sOpen it and create an account — the first account you make owns its organisation.%s\n" "$DIM" "$RESET"
printf "\n  %sLogs%s  docker compose logs -f app\n" "$DIM" "$RESET"
printf "  %sStop%s  ./start.sh --stop\n\n" "$DIM" "$RESET"

[ -n "$FOLLOW" ] && docker compose logs -f app
exit 0
