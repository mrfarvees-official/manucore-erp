#!/usr/bin/env bash
# Usage: remote-deploy.sh <registry-user> [service...]   (registry token on stdin)
# With no services it updates everything; with services it updates only those.
set -euo pipefail
cd "$(dirname "$0")/.."

DOCKER=(docker)
if ! docker info >/dev/null 2>&1; then
  DOCKER=(sudo docker)   # first run: the docker group is not active in this session yet
fi

COMPOSE=("${DOCKER[@]}" compose -f compose.prod.yml)
USER_NAME="$1"
shift

"${DOCKER[@]}" login ghcr.io -u "$USER_NAME" --password-stdin   # token comes from stdin

"${COMPOSE[@]}" config -q   # fails early on a syntax error or a missing variable

if [ "$#" -gt 0 ]; then
  "${COMPOSE[@]}" pull "$@"
  "${COMPOSE[@]}" up -d --no-build --no-deps "$@"
else
  "${COMPOSE[@]}" pull
  "${COMPOSE[@]}" up -d --no-build --remove-orphans
fi

"${DOCKER[@]}" image prune -f >/dev/null
"${COMPOSE[@]}" ps