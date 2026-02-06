#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

BASE_URL="${1:-}"
shift || true

REFRESH=0

usage() {
  echo "Usage:"
  echo "  jcache.sh <base_url> [-r seconds]"
  echo
  echo "Example:"
  echo "  jcache.sh http://127.0.0.1:9001"
  echo "  jcache.sh http://127.0.0.1:9001 -r 1"
  exit 1
}

[[ -z "$BASE_URL" ]] && usage

# ---------- jq ----------
if [ -x "$BASE_DIR/lib/jq-linux64" ]; then
  JQ="$BASE_DIR/lib/jq-linux64"
elif command -v jq >/dev/null 2>&1; then
  JQ="$(command -v jq)"
else
  echo "ERROR: jq not found (expected $BASE_DIR/lib/jq-linux64 or jq in PATH)"
  exit 1
fi

# ---------- parse args ----------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -r) REFRESH="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

URL="$BASE_URL/count-cache"

run_once() {
  JSON=$(curl -sf "$URL" | sed '1s/^\xEF\xBB\xBF//')

  COUNT=$(
    echo "$JSON" | $JQ -r '
      if (has("result")) then .result
      else
        error("missing field: result")
      end
    '
  )

  clear
  echo "CorrelationStoreCount $COUNT"
}

if [[ "$REFRESH" -gt 0 ]]; then
  while true; do
    run_once
    sleep "$REFRESH"
  done
else
  run_once
fi
