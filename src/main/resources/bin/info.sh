#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BASE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
BASE_URL="$1"
shift

REFRESH=0   # 0 = run once

while getopts ":r:" opt; do
  case $opt in
    r)
      REFRESH="$OPTARG"
      ;;
    *)
      echo "Usage: info.sh <base_url> [-r seconds]"
      exit 1
      ;;
  esac
done


if [ -z "$BASE_URL" ]; then
  echo "Usage: info.sh <base_url>"
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
G="\e[32m"
R="\e[31m"
NC="\e[0m"

# ================= TABLE DRAW =================
print_top() {
  printf "${G}┌──────────┬────────┬────────────────────────┬────────────┬────────────┬────────┐${NC}\n"
}
print_sep() {
  printf "${G}├──────────┼────────┼────────────────────────┼────────────┼────────────┼────────┤${NC}\n"
}
print_bottom() {
  printf "${G}└──────────┴────────┴────────────────────────┴────────────┴────────────┴────────┘${NC}\n"
}


render() {
  local json="$1"

  clear
  print_top
  printf "${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC} %-22s ${G}│${NC} %-10s ${G}│${NC} %-10s ${G}│${NC} %-6s ${G}│${NC}\n" \
    "NAME" "TYPE" "HOST" "ACTIVE C" "ACTIVE S" "STATUS"
  print_sep

  $JQ -r '
    .socketInfo[]
    | . as $s
    | {
        name: .name,
        type: (if (.id | test("_server-")) then "server" else "client" end),
        host: (
          if (.id | test("_server-"))
          then ":" + (.id | split("-")[-1])
          else (.id | split("-client-")[1])
          end
        ),
        ac: .activeClient,
        as: .activeServer,
        st: .status
      }
    | [.name, .type, .host, .ac, .as, .st]
    | @tsv
  ' <<<"$json" |
  while IFS=$'\t' read -r name type host ac as status; do
    color="$G"
    [[ "$status" != "UP" ]] && color="$R"

    printf "${color}│${NC} %-8s ${color}│${NC} %-6s ${color}│${NC} %-22s ${color}│${NC} %-10s ${color}│${NC} %-10s ${color}│${NC} %-6s ${color}│${NC}\n" \
      "$name" "$type" "$host" "$ac" "$as" "$status"
  done

  print_bottom
  echo
  echo -e "${G}Uptime:${NC} $($JQ -r '.uptime' <<<"$json")"
}


# ================= MAIN LOOP =================
run_once() {
  JSON=$(curl -s "$BASE_URL/info" | sed '1s/^\xEF\xBB\xBF//')

  if ! $JQ -e '
    has("socketInfo")
    and (.socketInfo | length >= 0)
    and has("uptime")
  ' <<<"$JSON" >/dev/null 2>&1; then
    echo "ERROR: unexpected JSON from $BASE_URL/info"
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
