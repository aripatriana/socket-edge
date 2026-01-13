#!/usr/bin/env bash

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)

export JAVA_HOME=/usr/lib/jvm/java-21-zulu-openjdk-jdk
export PATH="$JAVA_HOME/bin:$PATH"

LIB_DIR="$BASE_DIR/lib"
CONF_DIR="$BASE_DIR/conf"
LOG_DIR="$BASE_DIR/log"
PID_FILE="$BASE_DIR/bin/app.pid"

JAVA_OPTS=(
  -Xms512m
  -Xmx1024m
  -Dfile.encoding=UTF-8
  -Dlogback.configurationFile="$CONF_DIR/logback.xml"
  -DLOG_HOME="$LOG_DIR"
  -Dbase.dir="$BASE_DIR"
)

# ⚠️ PAKAI ABSOLUTE PATH
CLASSPATH="$LIB_DIR/*"

echo "Using CLASSPATH=$CLASSPATH"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Application already running (PID=$(cat "$PID_FILE"))"
  exit 1
fi

echo "Starting socket-edge..."

java "${JAVA_OPTS[@]}" \
  -cp "$CLASSPATH" \
  com.socket.edge.SystemBootstrap &

APP_PID=$!
echo $APP_PID > "$PID_FILE"

echo "Started with PID $APP_PID"
