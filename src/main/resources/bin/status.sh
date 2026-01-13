#!/usr/bin/env bash

PID_FILE="$(cd "$(dirname "$0")/.." && pwd)/bin/app.pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "RUNNING (PID=$(cat "$PID_FILE"))"
else
  echo "STOPPED"
fi
