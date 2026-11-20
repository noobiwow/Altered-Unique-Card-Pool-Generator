#!/usr/bin/env bash
# Starts the backend (http://localhost:8080) and the Vite dev server
# (http://localhost:5173, proxies /api to the backend) with live reload.
# Ctrl+C stops both.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d backend/cardsdata/Unique ]; then
  ./update-cardsdata.sh
fi
if [ ! -d frontend_vue/node_modules ]; then
  (cd frontend_vue && npm ci)
fi

(cd backend && mvn -q spring-boot:run) &
BACKEND=$!
(cd frontend_vue && npm run dev) &
FRONTEND=$!

trap 'kill $BACKEND $FRONTEND 2>/dev/null || true' INT TERM EXIT
# Stop everything as soon as one of them exits (works with macOS' bash 3.2).
while kill -0 $BACKEND 2>/dev/null && kill -0 $FRONTEND 2>/dev/null; do
  sleep 1
done
