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
  echo "  jmetric.sh <base_url> --all [-r seconds]"
  echo "  jmetric.sh <base_url> -i ID [-r seconds]"
  echo "  jmetric.sh <base_url> -n NAME [-r seconds]"
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
  printf "${G}┌─────────────────────────────────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐${NC}\n"
}

print_sep() {
  printf "${G}├─────────────────────────────────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┼────────┤${NC}\n"
}

print_bottom() {
  printf "${G}└─────────────────────────────────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┘${NC}\n"
}


# ================= RENDER =================
render() {
  local json="$1"
  clear

  print_top
  printf "${G}│${NC} %-35s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC}\n" \
    "ID" \
    "ALAT" "MLAT" "XLAT" "P90" "P95" \
    "PTPS" "PMIN" "PMAX" "P90" "P95" \
    "TTPS" "TMIN" "TMAX" "P90" "P95"


  print_sep

  $JQ -r '
    def fmt_latency(ns):
      if ns == 0 then "-"
      elif ns < 1000 then "\(ns)ns"
      elif ns < 1000000 then "\((ns/1000)|floor)us"
      elif ns < 1000000000 then "\((ns/1000000)|floor)ms"
      else "\((ns/1000000000)|floor)s"
      end;
  
    .result
    | sort_by(.name, .type, .id)
    | .[]
    | [
        .id,
  
        fmt_latency(.avgLatency),
        fmt_latency(.minLatency),
        fmt_latency(.maxLatency),
        fmt_latency(.latencyP90Ns),
        fmt_latency(.latencyP95Ns),
  
        .pressureTps,
        .minPressureTps,
        .maxPressureTps,
        .pressureTpsP90,
        .pressureTpsP95,
  
        .throughputTps,
        .minThroughputTps,
        .maxThroughputTps,
        .throughputTpsP90,
        .throughputTpsP95
      ]
    | @tsv
  ' <<<"$json" |

  while IFS=$'\t' read -r \
    id \
    alat mlat xlat lp90 lp95 \
    ptps pmin pmax pp90 pp95 \
    ttps tmin tmax tp90 tp95
  do
    printf "${G}│${NC} %-35s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC} %6s ${G}│${NC}\n" \
      "$id" \
      "$alat" "$mlat" "$xlat" "$lp90" "$lp95" \
      "$ptps" "$pmin" "$pmax" "$pp90" "$pp95" \
      "$ttps" "$tmin" "$tmax" "$tp90" "$tp95"
  done

  print_bottom
}

# ================= MAIN =================
run_once() {
  if $ALL; then
    URL="$BASE_URL/socket/metrics?id=all"
  elif [[ -n "$ID" ]]; then
    URL="$BASE_URL/socket/metrics?id=$ID"
  elif [[ -n "$NAME" ]]; then
    URL="$BASE_URL/socket/metrics?name=$NAME"
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
