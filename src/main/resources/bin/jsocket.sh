#!/usr/bin/env bash

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)
CONF_FILE="$BASE_DIR/conf/system.conf"

JQUEUE_SCRIPT="$BASE_DIR/bin/jqueue.sh" 
JINFO_SCRIPT="$BASE_DIR/bin/jinfo.sh"
JMETRIC_SCRIPT="$BASE_DIR/bin/jmetric.sh"
JCONTROL_SCRIPT="$BASE_DIR/bin/jcontrol.sh"   

if [ ! -f "$CONF_FILE" ]; then
  echo "ERROR: system.conf not found"
  exit 1
fi

# Detect jq
if [ -x "$BASE_DIR/lib/jq-linux64" ]; then
  JQ="$BASE_DIR/lib/jq-linux64"
elif command -v jq >/dev/null 2>&1; then
  JQ="$(command -v jq)"
else
  echo "ERROR: jq not found (expected $BASE_DIR/lib/jq-linux64 or jq in PATH)"
  exit 1
fi


PORT=$(awk '
  BEGIN { in_server=0 }
  /^[[:space:]]*server[[:space:]]*\{/ { in_server=1; next }
  in_server && /^[[:space:]]*port[[:space:]]*=/ {
    sub(/.*=/, "", $0)
    gsub(/[[:space:]\r]/, "", $0)
    print $0
    exit
  }
  in_server && /^[[:space:]]*\}/ { in_server=0 }
' "$CONF_FILE")

if [ -z "$PORT" ]; then
  echo "ERROR: server.port not found in system.conf"
  exit 1
fi

if ! [[ "$PORT" =~ ^[0-9]+$ ]]; then
  echo "ERROR: invalid server.port [$PORT]"
  exit 1
fi

BASE_URL="http://localhost:${PORT}"

call() {
  RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL$1")

  BODY=$(echo "$RESPONSE" | sed '$d')
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

  STATUS=$(echo "$BODY" | $JQ -r '.status // "UNKNOWN"')
  MESSAGE=$(echo "$BODY" | $JQ -r '.message // ""')

  echo "STATUS: $STATUS"
  echo "message:"
  printf "%b\n" "$MESSAGE"
  echo
  echo "HTTP $HTTP_CODE"
}

case "$1" in
  validate|-t)
    echo "Validating configuration (port=$PORT)..."
    call "/config/validate"
    ;;
  reload)
    echo "Reloading configuration (port=$PORT)..."
    call "/config/reload"
    ;;
  info)
    shift
    exec "$JINFO_SCRIPT" "$BASE_URL" "$@"
    ;;
  metric)                                    
    shift
    exec "$JMETRIC_SCRIPT" "$BASE_URL" "$@"
    ;;
  queue)                                    
    shift
    exec "$JQUEUE_SCRIPT" "$BASE_URL" "$@"
    ;;
  start|stop|restart)
    ACTION="$1"
    shift
    exec "$JCONTROL_SCRIPT" "$BASE_URL" "$ACTION" "$@"
    ;;
  *)
    echo "Usage: se {validate|-t|reload|status|metrics|start|stop|restart}"
    exit 1
    ;;
esac
