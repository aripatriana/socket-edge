#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
BASE_URL="$1"
shift

REFRESH=0   # 0 = run once

while getopts ":r:" opt; do
  case $opt in
    r) REFRESH="$OPTARG" ;;
    *) echo "Usage: info.sh <base_url> [-r seconds]"; exit 1 ;;
  esac
done

[ -z "$BASE_URL" ] && { echo "Usage: info.sh <base_url>"; exit 1; }

# Detect jq
if [ -x "$BASE_DIR/lib/jq-linux64" ]; then
  JQ="$BASE_DIR/lib/jq-linux64"
elif command -v jq >/dev/null 2>&1; then
  JQ="$(command -v jq)"
else
  echo "ERROR: jq not found"
  exit 1
fi

# Colors
G="\e[32m"; R="\e[31m"; NC="\e[0m"

# ================= TABLE DRAW =================
print_top() {
  printf "${G}┌──────────────────────────────────┬──────────────────────┬──────────────────────┬────────────┬──────────┬──────────┬──────────┬────────┐${NC}\n"
}
print_sep() {
  printf "${G}├──────────────────────────────────┼──────────────────────┼──────────────────────┼────────────┼──────────┼──────────┼──────────┼────────┤${NC}\n"
}
print_bottom() {
  printf "${G}└──────────────────────────────────┴──────────────────────┴──────────────────────┴────────────┴──────────┴──────────┴──────────┴────────┘${NC}\n"
}

render() {
  local json="$1"
  clear

  print_top
  printf "${G}│${NC} %-32s ${G}│${NC} %-20s ${G}│${NC} %-20s ${G}│${NC} %-10s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC}\n" \
    "ID" "LOCAL_HOST" "REMOTE_HOST" "CONNECTED" "UPTIME" "LAST_C" "LAST_D" "STATUS"
  print_sep

  $JQ -r '
    def fmt_duration(ms):
      (ms / 1000 | floor) as $s
      | ($s / 3600 | floor) as $h
      | (($s % 3600) / 60 | floor) as $m
      | ($s % 60) as $sec
      | if $h > 0 then
          "\($h)h\($m)m\($sec)s"
        elif $m > 0 then
          "\($m)m\($sec)s"
        else
          "\($sec)s"
        end;

    def fmt_since(ts):
      if ts > 0
      then fmt_duration((now * 1000 | floor) - ts)
      else "-"
      end;
  
    .result.socketStatus
    | sort_by(.name, .type, .id)
    | .[]
    | [
        .id,
        (.localHost // "-"),
        (.remoteHost // "-"),
        (.active | tostring),
         fmt_since(.startTime),
         fmt_since(.lastConnect),
         fmt_since(.lastDisconnect),
        .status
      ]
    | @tsv
  ' <<<"$json" |
  while IFS=$'\t' read -r id local remote conn uptime lc ld status; do
    conn_color="$G"
    [[ "$conn" -eq 0 ]] && conn_color="$R"

    printf "${G}│${NC} %-32s ${G}│${NC} %-20s ${G}│${NC} %-20s ${G}│${NC} ${conn_color}%-10s${NC} ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC}\n" \
      "$id" "$local" "$remote" "$conn" "$uptime" "$lc" "$ld" "$status"
  done

  print_bottom
}

# ================= MAIN =================
run_once() {
  JSON=$(curl -s "$BASE_URL/socket/status" | sed '1s/^\xEF\xBF//')

  if ! $JQ -e 'has("result") and (.result | has("socketStatus"))' <<<"$JSON" >/dev/null; then
    echo "ERROR: invalid JSON"
    echo "$JSON"
    exit 1
  fi

  render "$JSON"
}

[ "$REFRESH" -gt 0 ] && while true; do run_once; sleep "$REFRESH"; done || run_once
