#!/usr/bin/env bash
set -euo pipefail

BASE_URL="$1"
shift || true

ACTION="${1:-}"
shift || true

ID=""
NAME=""
ALL=false

usage() {
  echo "Usage:"
  echo "  jcontrol.sh <base_url> <start|stop|restart> [--all | -i ID | -n NAME]"
  exit 1
}

[[ -z "$ACTION" ]] && usage

case "$ACTION" in
  start|stop|restart) ;;
  *) echo "ERROR: invalid action '$ACTION'"; usage ;;
esac

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

# ---------- build URL ----------
ENDPOINT="/socket/$ACTION"

if $ALL; then
  URL="$ENDPOINT?id=all"
elif [[ -n "$ID" ]]; then
  URL="$ENDPOINT?id=$ID"
elif [[ -n "$NAME" ]]; then
  URL="$ENDPOINT?name=$NAME"
fi

echo "→ $ACTION socket(s)"
echo "→ $BASE_URL$URL"

curl -s -w "\nHTTP %{http_code}\n" "$BASE_URL$URL"
