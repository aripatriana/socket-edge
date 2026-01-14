#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
BASE_URL="$1"
shift

REFRESH=0

while getopts ":r:" opt; do
  case $opt in
    r)
      REFRESH="$OPTARG"
      ;;
    *)
      echo "Usage: socket_metrics.sh <base_url> [-r seconds]"
      exit 1
      ;;
  esac
done

if [ -z "$BASE_URL" ]; then
  echo "Usage: socket_metrics.sh <base_url> [-r seconds]"
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
  printf "${G}┌─────────────────────────────────────┬───────┬───────┬───────┬─────────┬─────────┬────────┬──────┬──────┬──────┬─────────┬───────┬───────┐${NC}\n"
}
print_sep() {
  printf "${G}├─────────────────────────────────────┼───────┼───────┼───────┼─────────┼─────────┼────────┼──────┼──────┼──────┼─────────┼───────┼───────┤${NC}\n"
}
print_bottom() {
  printf "${G}└─────────────────────────────────────┴───────┴───────┴───────┴─────────┴─────────┴────────┴──────┴──────┴──────┴─────────┴───────┴───────┘${NC}\n"
}

# ================= RENDER =================
render() {
  local json="$1"
  clear

  print_top
  printf "${G}│${NC} %-35s ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC} %7s ${G}│${NC} %7s ${G}│${NC} %6s ${G}│${NC} %4s ${G}│${NC} %4s ${G}│${NC} %4s ${G}│${NC} %7s ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC}\n" \
    "ID" "ALAT" "MLAT" "XLAT" "MSG_IN" "MSG_OUT" "QUEUE" "ATPS" "MTPS" "XTPS" "MSG_ERR" "LERR" "LMSG"

  print_sep

  $JQ -r '
    def fmt_ago(ms):
      if ms == 0 then "-" else
        (now * 1000 - ms) as $d
        | ($d / 1000 | floor) as $s
        | if $s < 60 then "\($s)s"
          elif $s < 3600 then "\(($s/60)|floor)m"
          elif $s < 86400 then "\(($s/3600)|floor)h"
          else "\(($s/86400)|floor)d"
          end
      end;

    def fmt_latency(ns):
      if ns == 0 then "-"
      elif ns < 1000 then
        "\(ns)ns"
      elif ns < 1000000 then
        "\( (ns / 1000 | floor) )us"
      elif ns < 1000000000 then
        "\( (ns / 1000000 | floor) )ms"
      elif ns < 60000000000 then
        "\( (ns / 1000000000 | floor) )s"
      elif ns < 3600000000000 then
        "\( (ns / 60000000000 | floor) )m"
      else
        "\( (ns / 3600000000000 | floor) )h"
      end;
      
    .result
    | sort_by(.name, .type, .id)
    | .[]
    | [
        .id,
        fmt_latency(.avgLatency),
        fmt_latency(.minLatency),
        fmt_latency(.maxLatency),
        .msgIn,
        .msgOut,
        .queue,
        .avgTps,
        .minTps,
        .maxTps,
        .errCnt,
        fmt_ago(.lastErr),
        fmt_ago(.lastMsg)
      ]
    | @tsv
  ' <<<"$json" |
  while IFS=$'\t' read -r id avg min max in out queue atps mtps xtps err lerr lmsg; do
    err_color="$G"
    [ "$err" -gt 0 ] && err_color="$R"

    printf "${G}│${NC} %-35s ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC} %7s ${G}│${NC} %7s ${G}│${NC} %6s ${G}│${NC} %4s ${G}│${NC} %4s ${G}│${NC} %4s ${G}│${NC} ${err_color}%7s${NC} ${G}│${NC} %5s ${G}│${NC} %5s ${G}│${NC}\n" \
      "$id" "$avg" "$min" "$max" "$in" "$out" "$queue" "$atps" "$mtps" "$xtps" "$err" "$lerr" "$lmsg"
  done

  print_bottom
}

# ================= MAIN =================
run_once() {
  JSON=$(curl -s "$BASE_URL/socket/metrics" | sed '1s/^\xEF\xBB\xBF//')

  if ! $JQ -e '
    has("result")
    and (.result | type == "array")
  ' <<<"$JSON" >/dev/null 2>&1; then
    echo "ERROR: unexpected JSON from $BASE_URL/socket/metrics"
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
