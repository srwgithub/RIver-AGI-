#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -f "$SCRIPT_DIR/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env.local"
  set +a
fi

export HOST="${HOST:-0.0.0.0}"
export PORT="5001"
export DL_ENGINE_ENABLED="true"
export DL_ENGINE_URL="http://127.0.0.1:5001"

echo "Starting Python prediction engine on ${HOST}:${PORT}"
"$SCRIPT_DIR/prediction-engine/.venv/bin/python" "$SCRIPT_DIR/prediction-engine/app.py" &
PYTHON_PID=$!

cleanup() {
  kill "$PYTHON_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting Java backend on http://127.0.0.1:8080"
exec java -jar "$SCRIPT_DIR/target/river-agi-backend-0.0.1-SNAPSHOT.jar" --spring.profiles.active=mysql
