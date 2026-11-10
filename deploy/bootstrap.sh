#!/usr/bin/env bash
# Installs what a deployment needs. Does nothing for tools that are already there.
set -euo pipefail

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo -n"   # -n: fail with a clear error instead of waiting for a password
fi

have() { command -v "$1" >/dev/null 2>&1; }

if ! have curl || ! have rsync; then
  if have apt-get; then
    $SUDO apt-get update -y
    $SUDO apt-get install -y curl rsync ca-certificates
  elif have dnf; then
    $SUDO dnf install -y curl rsync
  else
    echo "Install curl and rsync on this server, then run the deploy again." >&2
    exit 1
  fi
fi

if ! have docker; then
  # Docker's official convenience installer; it also installs the compose plugin.
  curl -fsSL https://get.docker.com | $SUDO sh
fi

if [ -n "$SUDO" ] && ! id -nG | grep -qw docker; then
  $SUDO usermod -aG docker "$USER" || true
fi

mkdir -p "$HOME/manucore/deploy"
echo "bootstrap ok"