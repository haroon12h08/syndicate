#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "== Postgres =="
if docker ps --format '{{.Names}}' | grep -q '^syndicate-db$'; then
  echo "already running"
elif docker ps -a --format '{{.Names}}' | grep -q '^syndicate-db$'; then
  docker start syndicate-db
else
  docker run -d --name syndicate-db \
    -e POSTGRES_DB=syndicate -e POSTGRES_USER=syndicate -e POSTGRES_PASSWORD=syndicate \
    -p 5432:5432 postgres:16
fi

echo -n "Waiting for Postgres..."
until docker exec syndicate-db pg_isready -U syndicate >/dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " ready"

echo "== Backend =="
(cd "$ROOT_DIR/backend" && mvn -q spring-boot:run) &
BACKEND_PID=$!

echo "== Frontend =="
if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
  (cd "$ROOT_DIR/frontend" && npm install)
fi
(cd "$ROOT_DIR/frontend" && npm run dev) &
FRONTEND_PID=$!

cleanup() {
  echo ""
  echo "Stopping backend and frontend..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo -n "Waiting for backend..."
until curl -s -o /dev/null http://localhost:8080/api/health 2>/dev/null; do
  echo -n "."
  sleep 1
done
echo " ready"

echo ""
echo "Syndicate is running:"
echo "  Frontend: http://localhost:5173"
echo "  Backend:  http://localhost:8080/api"
echo ""
echo "Press Ctrl+C to stop everything (Postgres container keeps running for next time)."

wait
