#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${SERVER_PORT:-8082}"
cd "$DIR"
export SKIP_BROWSER=1
./start.sh > "$DIR/smoke.log" 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT
ok=0
for _ in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${PORT}/" >/dev/null 2>&1; then ok=1; break; fi
  sleep 1
done
if [[ "$ok" != "1" ]]; then
  echo "SMOKE FAIL wms: app did not start on :$PORT"
  tail -n 80 "$DIR/smoke.log" || true
  exit 1
fi
html="$(curl -sS "http://127.0.0.1:${PORT}/")"
echo "$html" | grep -qiE '<html|<div id=.app' || { echo "SMOKE FAIL wms: / is not HTML"; exit 1; }
spa="$(curl -sS -o /tmp/wms-spa.body -w "%{http_code}" "http://127.0.0.1:${PORT}/dashboard")"
test "$spa" = "200"
# admin 用 PBKDF2 初始化，Tomcat 可能先于 ApplicationRunner 对外服务，登录需重试
body=""
login_ok=0
for _ in $(seq 1 30); do
  body="$(curl -sS -X POST "http://127.0.0.1:${PORT}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')"
  if echo "$body" | grep -q '"code":0' && echo "$body" | grep -q '"token"'; then
    login_ok=1
    break
  fi
  sleep 1
done
if [[ "$login_ok" != "1" ]]; then
  echo "SMOKE FAIL wms: login code != 0: $body"
  tail -n 80 "$DIR/smoke.log" || true
  exit 1
fi
echo "SMOKE OK wms :$PORT"
