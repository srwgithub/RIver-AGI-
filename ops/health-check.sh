#!/usr/bin/env bash
set -u

ROOT="${RIVER_AGI_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
JAVA_URL="${JAVA_URL:-http://127.0.0.1:8080}"
PYTHON_URL="${PYTHON_URL:-http://127.0.0.1:5001}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:3000}"
failed=0

check() {
  local name="$1" url="$2"
  local code
  code="$(curl -sS -o /tmp/river-agi-health-check.$$ -w '%{http_code}' --max-time 10 "$url" || true)"
  if [[ "$code" =~ ^2 ]]; then
    echo "OK   $name ($code) $url"
  else
    echo "FAIL $name ($code) $url"
    failed=1
  fi
  rm -f "/tmp/river-agi-health-check.$$"
}

check "frontend" "$FRONTEND_URL/"
check "java" "$JAVA_URL/actuator/health"
check "python" "$PYTHON_URL/health"

if command -v mysqladmin >/dev/null 2>&1; then
  if mysqladmin ping --host="${DB_HOST:-127.0.0.1}" --port="${DB_PORT:-3306}" --silent >/dev/null 2>&1; then
    echo "OK   mysql"
  else
    echo "FAIL mysql"
    failed=1
  fi
else
  echo "INFO mysqladmin not installed; skipped MySQL check"
fi

exit "$failed"
