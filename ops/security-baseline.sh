#!/usr/bin/env bash
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"
failed=0

check_file() {
  if [ -f "$1" ]; then echo "OK   $1"; else echo "FAIL missing $1"; failed=1; fi
}

check_file "$root/backend/src/main/resources/application-prod.yml"
check_file "$root/backend/.env.example"
check_file "$root/ops/nginx.conf.example"

if [ -f "$root/backend/.env.local" ]; then
  echo "INFO backend/.env.local exists and is ignored by Git"
else
  echo "WARN backend/.env.local is not configured on this machine"
fi

scan_file="/tmp/river-agi-secret-scan.$$"
if rg -n --hidden --glob '!**/.git/**' --glob '!**/.env.local' --glob '!**/target/**' \
  'sk-[A-Za-z0-9]{20,}' "$root" >"$scan_file" 2>/dev/null || \
  rg -n --hidden --glob '!**/.git/**' --glob '!**/.env.local' --glob '!**/target/**' \
  'password[[:space:]]*[:=][[:space:]]*[A-Za-z0-9_@#$%.-]{8,}' "$root" >>"$scan_file" 2>/dev/null; then
  echo "FAIL possible secret found; inspect $scan_file"
  failed=1
else
  echo "OK   no obvious secrets found in tracked source"
fi
rm -f "$scan_file"
exit "$failed"
