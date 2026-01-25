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
  echo "  jinfo.sh <base_url> --all [-r seconds]"
  echo "  jinfo.sh <base_url> -i ID [-r seconds]"
  echo "  jinfo.sh <base_url> -n NAME [-r seconds]"
  exit 1
}

[[ -z "$BASE_URL" ]] && usage

# ================= ARG PARSING =================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)
      ALL=true
      shift
      ;;
    -i)
      ID="${2:-}"
      shift 2
      ;;
    -n)
      NAME="${2:-}"
      shift 2
      ;;
    -r)
      REFRESH="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      usage
      ;;
  esac
done

# ================= VALIDATION =================
if ! $ALL && [[ -z "$ID" && -z "$NAME" ]]; then
  echo "ERROR: must specify --all, -i ID, or -n NAME"
  exit 1
fi

if $ALL && ([[ -n "$ID" ]] || [[ -n "$NAME" ]]); then
  echo "ERROR: --all cannot be combined with -i or -n"
  exit 1
fi

# ================= JQ DETECT =================
if [ -x "$BASE_DIR/lib/jq-linux64" ]; then
  JQ="$BASE_DIR/lib/jq-linux64"
elif command -v jq >/dev/null 2>&1; then
  JQ="$(command -v jq)"
else
  echo "ERROR: jq not found (expected $BASE_DIR/lib/jq-linux64 or jq in PATH)"
  exit 1
fi

# ================= COLORS =================
G="\e[32m"; R="\e[31m"; NC="\e[0m"

# ================= TABLE =================
print_top() {
  printf "${G}┌──────────┬──────────────────────────────────┬──────────────────────┬──────────────────────┬──────┬──────────┬──────────┬──────────┬────────┐${NC}\n"
}
print_sep() {
  printf "${G}├──────────┼──────────────────────────────────┼──────────────────────┼──────────────────────┼──────┼──────────┼──────────┼──────────┼────────┤${NC}\n"
}
print_bottom() {
  printf "${G}└──────────┴──────────────────────────────────┴──────────────────────┴──────────────────────┴──────┴──────────┴──────────┴──────────┴────────┘${NC}\n"
}

# ================= RENDER =================
render() {
  local json="$1"
  clear

  print_top
  printf "${G}│${NC} %-8s ${G}│${NC} %-32s ${G}│${NC} %-20s ${G}│${NC} %-20s ${G}│${NC} %-4s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC}\n" \
    "ID" "SOCKET_ID" "LOCAL_HOST" "REMOTE_HOST" "CONN" "UPTIME" "LAST_C" "LAST_D" "STATUS"
  print_sep

  $JQ -r '
    def fmt_duration(ms):
      (ms / 1000 | floor) as $s
      | ($s / 3600 | floor) as $h
      | (($s % 3600) / 60 | floor) as $m
      | ($s % 60) as $sec
      | if $h > 0 then "\($h)h\($m)m\($sec)s"
        elif $m > 0 then "\($m)m\($sec)s"
        else "\($sec)s"
        end;

    def fmt_since(ts):
      if ts > 0
      then fmt_duration((now * 1000 | floor) - ts)
      else "-"
      end;

    .result
    | sort_by(.id)
    | .[]
    | [
        .hashId,
        .id,
        (.localHost | select(. != "") // "-"),
        (.remoteHost | select(. != "") // "-"),
        (.active | tostring),
        fmt_since(.startTime),
        fmt_since(.lastConnect),
        fmt_since(.lastDisconnect),
        .status
      ]
    | @tsv
  ' <<<"$json" |
  while IFS=$'\t' read -r hashId id local remote conn uptime lc ld status; do
    conn_color="$G"
    [[ "$conn" == "0" ]] && conn_color="$R"

    IFS=',' read -ra REMOTES <<< "$remote"

    printf "${G}│${NC} %-8s ${G}│${NC} %-32s ${G}│${NC} %-20s ${G}│${NC} %-20s ${G}│${NC} ${conn_color}%-4s${NC} ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC}\n" \
      "$hashId" "$id" "$local" "${REMOTES[0]}" "$conn" "$uptime" "$lc" "$ld" "$status"

    for ((i=1; i<${#REMOTES[@]}; i++)); do
      printf "${G}│${NC} %-8s ${G}│${NC} %-32s ${G}│${NC} %-20s ${G}│${NC} %-20s ${G}│${NC} %-4s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-8s ${G}│${NC} %-6s ${G}│${NC}\n" \
        "" "" "${REMOTES[$i]}" "" "" "" "" ""
    done
  done

  print_bottom
}

# ================= MAIN =================
run_once() {
  if $ALL; then
    URL="$BASE_URL/socket/status?id=all"
  elif [[ -n "$ID" ]]; then
    URL="$BASE_URL/socket/status?id=$ID"
  elif [[ -n "$NAME" ]]; then
    URL="$BASE_URL/socket/status?name=$NAME"
  else
    usage
  fi

  JSON=$(curl -s "$URL" | sed '1s/^\xEF\xBB\xBF//')

  if ! $JQ -e 'has("result") and (.result)' <<<"$JSON" >/dev/null; then
    echo "ERROR: invalid JSON from $URL"
    echo "$JSON"
    exit 1
  fi

  render "$JSON"
}

if [[ "$REFRESH" -gt 0 ]]; then
  while true; do
    run_once
    sleep "$REFRESH"
  done
else
  run_once
fi
