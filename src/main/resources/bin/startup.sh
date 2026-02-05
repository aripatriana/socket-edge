#!/usr/bin/env bash

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)

BIN_DIR="$BASE_DIR/bin"
LIB_DIR="$BASE_DIR/lib"
CONF_DIR="$BASE_DIR/conf"
LOG_DIR="$BASE_DIR/log"
PID_FILE="$BIN_DIR/app.pid"

# =========================
# Parse arguments
# =========================
SERVER_MODE=""

case "$1" in
  --standalone)
    SERVER_MODE="standalone"
    ;;
  --cluster)
    SERVER_MODE="cluster"
    ;;
  *)
    echo "ERROR: server mode is required"
    echo "Usage:"
    echo "  ./startup.sh --standalone"
    echo "  ./startup.sh --cluster"
    exit 1
    ;;
esac

# =========================
# load env & JVM opts
# =========================
if [ -f "$BIN_DIR/setenv.sh" ]; then
  source "$BIN_DIR/setenv.sh"
else
  echo "setenv.sh not found"
  exit 1
fi

JAVA_OPTS=(
  -Dfile.encoding=UTF-8
  -Dlogback.configurationFile="$CONF_DIR/logback.xml"
  -DLOG_HOME="$LOG_DIR"
  -Dbase.dir="$BASE_DIR"
  -Dserver.mode="$SERVER_MODE"
)

CLASSPATH="$LIB_DIR/*"

echo "JAVA_HOME=$JAVA_HOME"
echo "CLASSPATH=$CLASSPATH"
echo "SERVER_MODE=$SERVER_MODE"
echo "SOCKET_EDGE_OPTS=$SOCKET_EDGE_OPTS"

# =========================
# Check running process
# =========================
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Application already running (PID=$(cat "$PID_FILE"))"
  exit 1
fi

# =========================
# Start application
# =========================
echo "Starting jalin-iso-loadbalancer (mode=$SERVER_MODE)..."

java $SOCKET_EDGE_OPTS \
  "${JAVA_OPTS[@]}" \
  -cp "$CLASSPATH" \
  com.socket.edge.SystemBootstrap \
  > /dev/null 2>&1 &

APP_PID=$!
echo "$APP_PID" > "$PID_FILE"

echo "Started with PID $APP_PID"
