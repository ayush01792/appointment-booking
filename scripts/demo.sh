#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v docker >/dev/null
command -v python3 >/dev/null
TASK_DEMO_PROJECT="appointment-demo-$(python3 -c 'import uuid; print(uuid.uuid4().hex[:12])')"
export DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
export DEMO_STAFF_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
compose() { docker compose --project-name "$TASK_DEMO_PROJECT" -f "$TASK_ROOT/compose.demo.yml" "$@"; }
cleanup() {
  local result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then compose logs --tail=60 >&2 || true; fi
  # This random project owns only disposable demo data; normal development data is separate.
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "$result"
}
trap cleanup EXIT
if [ "${DEMO_SKIP_BUILD:-false}" != "true" ]; then
  docker build --tag appointment-booking:local "$TASK_ROOT"
fi
compose up -d
TASK_DEMO_ADDRESS="$(compose port app 8080)"
export DEMO_BASE_URL="http://$TASK_DEMO_ADDRESS"
python3 "$TASK_ROOT/scripts/demo.py" "$@"
