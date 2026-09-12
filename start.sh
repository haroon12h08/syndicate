#!/usr/bin/env bash
#
# Boots the whole Syndicate stack with one command.
#
#   ./start.sh            build if needed, then start everything
#   ./start.sh --rebuild  force a rebuild of the app images
#   ./start.sh --logs     follow logs after starting
#   ./start.sh --down     stop everything
#
set -euo pipefail

cd "$(dirname "$0")"

BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'
YELLOW=$'\033[33m'; BLUE=$'\033[34m'; RESET=$'\033[0m'

step()  { printf "%s==>%s %s%s%s\n" "$BLUE" "$RESET" "$BOLD" "$1" "$RESET"; }
ok()    { printf "  %s✓%s %s\n" "$GREEN" "$RESET" "$1"; }
warn()  { printf "  %s!%s %s\n" "$YELLOW" "$RESET" "$1"; }
fail()  { printf "  %s✗%s %s\n" "$RED" "$RESET" "$1"; exit 1; }
note()  { printf "    %s%s%s\n" "$DIM" "$1" "$RESET"; }

REBUILD=""
FOLLOW_LOGS=""
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD="--build" ;;
    --logs)    FOLLOW_LOGS="1" ;;
    --down)    step "Stopping Syndicate"; docker compose down; ok "Stopped."; exit 0 ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) fail "Unknown option: $arg" ;;
  esac
done

# ---------------------------------------------------------------- prerequisites
step "Checking prerequisites"

command -v docker >/dev/null 2>&1 || fail "Docker is not installed. See https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required (try: docker compose version)"
docker info >/dev/null 2>&1 || fail "Docker is installed but not running. Start Docker and retry."
ok "Docker and Compose are available"

# ---------------------------------------------------------------- environment
step "Preparing environment"

if [ ! -f .env ]; then
  cp .env.example .env
  ok "Created .env from .env.example"
  warn "Using development defaults. Change SYNDICATE_JWT_SECRET before exposing this to anyone."
else
  ok ".env already present"
fi

set -a; . ./.env; set +a
API_PORT="${SYNDICATE_API_PORT:-8080}"
WEB_PORT="${SYNDICATE_WEB_PORT:-5173}"

for port in "$API_PORT" "$WEB_PORT"; do
  if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":${port} "; then
    warn "Port ${port} is already in use — the stack may fail to bind it."
    note "Change SYNDICATE_API_PORT / SYNDICATE_WEB_PORT in .env, or stop the other process."
  fi
done

# ---------------------------------------------------------------- build & start
step "Starting services"
note "postgres · rabbitmq · api (with Flyway migrations) · web"

# Compose waits on the healthchecks, so the API only starts once its dependencies are ready.
if [ -n "$REBUILD" ]; then
  docker compose up -d --build
else
  docker compose up -d --build
fi

# ---------------------------------------------------------------- readiness
step "Waiting for the API to become healthy"
note "First run compiles the backend and installs Tesseract; this can take a few minutes."

DEADLINE=$(( $(date +%s) + 420 ))
until curl -fsS "http://localhost:${API_PORT}/api/health" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    printf "\n"
    warn "API did not report healthy in time. Recent logs:"
    docker compose logs --tail=40 api || true
    fail "Startup failed. Run 'docker compose logs -f api' to investigate."
  fi
  if ! docker compose ps --status running --services 2>/dev/null | grep -q '^api$'; then
    printf "\n"
    docker compose logs --tail=40 api || true
    fail "The api container stopped unexpectedly."
  fi
  printf "."
  sleep 3
done
printf "\n"
ok "API is healthy (migrations applied)"

until curl -fsS "http://localhost:${WEB_PORT}" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    fail "Web server did not respond on port ${WEB_PORT}."
  fi
  sleep 2
done
ok "Web app is serving"

# ---------------------------------------------------------------- summary
printf "\n%sSyndicate is running.%s\n\n" "$BOLD" "$RESET"
printf "  %sWeb app%s        http://localhost:%s\n" "$BOLD" "$RESET" "$WEB_PORT"
printf "  %sAPI%s            http://localhost:%s/api\n" "$BOLD" "$RESET" "$API_PORT"
printf "  %sRabbitMQ UI%s    http://localhost:%s  (%s / %s)\n" "$BOLD" "$RESET" \
  "${SYNDICATE_RABBITMQ_MANAGEMENT_PORT:-15672}" "${SYNDICATE_RABBITMQ_USER:-guest}" "${SYNDICATE_RABBITMQ_PASSWORD:-guest}"
printf "\n  %sFirst steps%s   Register an account, add a company, open a transaction,\n" "$DIM" "$RESET"
printf "                 create a workstream, then upload a PDF on the Documents tab.\n"
printf "\n  %sLogs%s          docker compose logs -f api\n" "$DIM" "$RESET"
printf "  %sStop%s          ./start.sh --down\n\n" "$DIM" "$RESET"

if [ -n "$FOLLOW_LOGS" ]; then
  docker compose logs -f
fi
