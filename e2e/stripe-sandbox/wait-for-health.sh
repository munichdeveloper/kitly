#!/usr/bin/env bash
# Polls a health endpoint until it responds successfully or a timeout elapses.
# Usage: wait-for-health.sh <url> [timeout_seconds]
set -euo pipefail

URL="${1:?Usage: wait-for-health.sh <url> [timeout_seconds]}"
TIMEOUT="${2:-90}"

for ((i = 1; i <= TIMEOUT; i++)); do
  if curl --silent --fail --output /dev/null "$URL"; then
    echo "Backend healthy after ${i}s ($URL)"
    exit 0
  fi
  sleep 1
done

echo "::error::Backend did not become healthy within ${TIMEOUT}s ($URL)" >&2
exit 1
