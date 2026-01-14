#!/usr/bin/env bash

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)
CONF_FILE="$BASE_DIR/conf/system.conf"

SOCKET_STATUS_SCRIPT="$BASE_DIR/bin/socket_status.sh"
SOCKET_METRICS_SCRIPT="$BASE_DIR/bin/socket_metrics.sh"   

if [ ! -f "$CONF_FILE" ]; then
  echo "ERROR: system.conf not found"
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
  curl -s -w "\nHTTP %{http_code}\n" "$BASE_URL$1"
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
  status)
    shift
    exec "$SOCKET_STATUS_SCRIPT" "$BASE_URL" "$@"
    ;;
  metrics)                                    
    shift
    exec "$SOCKET_METRICS_SCRIPT" "$BASE_URL" "$@"
    ;;
  *)
    echo "Usage: se {validate|-t|reload|status|metrics}"
    exit 1
    ;;
esac
