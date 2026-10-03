#!/usr/bin/env bash
#
# Development runner: Postgres in a container, backend and frontend from source with reload.
# For the product as deployed - one image, one port - use ./start.sh instead.
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/.run-logs"
mkdir -p "$LOG_DIR"

# Ensures a container named $1 exists, is running, and actually publishes
# host port $2. If a container exists but was created without that port
# published (this has happened before and silently breaks everything
# downstream with a confusing "connection refused"), it is recreated:
# its current state is committed to a throwaway image first so any data
# baked into its writable layer survives, then it's rebuilt onto a named
# docker volume ($1-data -> $3) so this can't happen silently again.
ensure_container() {
  local name="$1" host_port="$2" volume_target="$3" image="$4"; shift 4
  local run_extra_args=("$@")

  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    local mapped
    mapped=$(docker port "$name" "${host_port}/tcp" 2>/dev/null || true)
    if [ -z "$mapped" ]; then
      echo "  '$name' exists but does not publish port $host_port -- recreating (data preserved)"
      local backup_image="${name}-repair-$(date +%s)"
      docker commit "$name" "$backup_image" >/dev/null
      docker rm -f "$name" >/dev/null
      docker volume create "${name}-data" >/dev/null
      docker run -d --name "$name" -p "${host_port}:${host_port}" \
        -v "${name}-data:${volume_target}" \
        "${run_extra_args[@]}" \
        "$backup_image" >/dev/null
    elif ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
      docker start "$name" >/dev/null
    else
      echo "  already running"
    fi
  else
    docker volume create "${name}-data" >/dev/null
    docker run -d --name "$name" -p "${host_port}:${host_port}" \
      -v "${name}-data:${volume_target}" \
      "${run_extra_args[@]}" \
      "$image" >/dev/null
  fi
}

echo "== Postgres =="
ensure_container syndicate-db 5432 /var/lib/postgresql/data postgres:16 \
  -e POSTGRES_DB=syndicate -e POSTGRES_USER=syndicate -e POSTGRES_PASSWORD=syndicate

echo -n "Waiting for Postgres..."
until docker exec syndicate-db pg_isready -U syndicate >/dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " ready"

echo "== Backend =="
(cd "$ROOT_DIR/backend" && exec mvn -q spring-boot:run) > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

echo "== Frontend =="
if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
  (cd "$ROOT_DIR/frontend" && npm install)
fi
(cd "$ROOT_DIR/frontend" && exec npm run dev) > "$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

cleanup() {
  echo ""
  echo "Stopping backend and frontend..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo -n "Waiting for backend..."
while ! curl -s -o /dev/null http://localhost:8080/api/health 2>/dev/null; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo " FAILED"
    echo "Backend process exited. Last lines of $LOG_DIR/backend.log:"
    tail -n 40 "$LOG_DIR/backend.log"
    exit 1
  fi
  echo -n "."
  sleep 1
done
echo " ready"

echo ""
echo "Syndicate is running:"
echo "  Frontend: http://localhost:5173"
echo "  Backend:  http://localhost:8080/api"
echo "  Logs:     $LOG_DIR/backend.log , $LOG_DIR/frontend.log"
echo ""
echo "Press Ctrl+C to stop the backend and frontend (Postgres keeps running for next time)."

wait
