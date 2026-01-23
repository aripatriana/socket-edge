#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

BASE_URL="${1:-}"
shift || true

ID=""
NAME=""
ALL=false
REFRESH=0

usage() {
  echo "Usage:"
  echo "  jqueue.sh <base_url> --all [-r seconds]"
  echo "  jqueue.sh <base_url> -i ID [-r seconds]"
  echo "  jqueue.sh <base_url> -n NAME [-r seconds]"
  exit 1
}

[[ -z "$BASE_URL" ]] && usage

# ---------- parse args ----------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)
      ALL=true
      shift
      ;;
    -i)
      ID="$2"
      shift 2
      ;;
    -n)
      NAME="$2"
      shift 2
      ;;
    -r)
      REFRESH="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      usage
      ;;
  esac
done

# ---------- validation ----------
if ! $ALL && [[ -z "$ID" && -z "$NAME" ]]; then
  echo "ERROR: must specify --all, -i ID, or -n NAME"
  exit 1
fi

if $ALL && ([[ -n "$ID" ]] || [[ -n "$NAME" ]]); then
  echo "ERROR: --all cannot be combined with -i or -n"
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

# Colors
G="\e[32m"; R="\e[31m"; NC="\e[0m"

# ================= TABLE =================
print_top() {
  printf "${G}┌─────────────────────────────────────┬─────────────────┬─────────────────┬─────────┬─────────┬────────────┬────────────┐${NC}\n"
}

print_sep() {
  printf "${G}├─────────────────────────────────────┼─────────────────┼─────────────────┼─────────┼─────────┼────────────┼────────────┤${NC}\n"
}

print_bottom() {
  printf "${G}└─────────────────────────────────────┴─────────────────┴─────────────────┴─────────┴─────────┴────────────┴────────────┘${NC}\n"
}


# ================= RENDER =================
render() {
  local json="$1"
  clear

  print_top
  printf "${G}│${NC} %-35s ${G}│${NC} %15s ${G}│${NC} %15s ${G}│${NC} %7s ${G}│${NC} %7s ${G}│${NC} %10s ${G}│${NC} %10s ${G}│${NC}\n" \
    "ID" "MSG_IN" "MSG_OUT" "QUEUE" "ERR" "LAST_ERR" "LAST_MSG"



  print_sep

  $JQ -r '
    .result
    | sort_by(.id)
    | .[]
    | [
        .id,
        .msgIn,
        .msgOut,
        .queue,
        .errCnt,
        .lastErr,
        .lastMsg
      ]
    | @tsv
  ' <<<"$json" |


  while IFS=$'\t' read -r \
    id in out queue err lastErr lastMsg
  do
    printf "${G}│${NC} %-35s ${G}│${NC} %15s ${G}│${NC} %15s ${G}│${NC} %7s ${G}│${NC} %7s ${G}│${NC} %10s ${G}│${NC} %10s ${G}│${NC}\n" \
      "$id" "$in" "$out" "$queue" "$err" "$lastErr" "$lastMsg"
  done


  print_bottom
}

# ================= MAIN =================
run_once() {
  if $ALL; then
    URL="$BASE_URL/socket/queues?id=all"
  elif [[ -n "$ID" ]]; then
    URL="$BASE_URL/socket/queues?id=$ID"
  elif [[ -n "$NAME" ]]; then
    URL="$BASE_URL/socket/queues?name=$NAME"
  else
    usage
  fi

  JSON=$(curl -s "$URL" | sed '1s/^\xEF\xBB\xBF//')

  if ! $JQ -e '
    has("result")
    and (.result | type == "array")
  ' <<<"$JSON" >/dev/null 2>&1; then
    echo "ERROR: unexpected JSON from $URL"
    echo "$JSON"
    exit 1
  fi

  render "$JSON"
}


if [ "$REFRESH" -gt 0 ]; then
  while true; do
    run_once
    sleep "$REFRESH"
  done
else
  run_once
fi
