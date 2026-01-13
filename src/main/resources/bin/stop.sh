#!/usr/bin/env bash

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)
PID_FILE="$BASE_DIR/bin/app.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "PID file not found. Application may not be running."
  exit 1
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
  echo "Process $PID not running. Cleaning PID file."
  rm -f "$PID_FILE"
  exit 0
fi

echo "Stopping socket-edge (PID=$PID)..."

# Graceful shutdown
kill "$PID"

TIMEOUT=20
while kill -0 "$PID" 2>/dev/null; do
  if [ $TIMEOUT -le 0 ]; then
    echo "Graceful shutdown timeout. Forcing kill..."
    kill -9 "$PID"
    break
  fi
  sleep 1
  TIMEOUT=$((TIMEOUT - 1))
done

rm -f "$PID_FILE"
echo "Stopped."
