#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

HOME="${HOME:-/data/data/com.termux/files/home}"
REPO="$HOME/termux-studio"

echo "[AI APK Studio] Termux Studio Build Engine"
pkg update -y
pkg install -y git

if [ -d "$REPO/.git" ]; then
  git -C "$REPO" pull --ff-only
else
  rm -rf "$REPO"
  git clone https://github.com/poordevcode/termux-android-studio.git "$REPO"
fi

bash "$REPO/install.sh" -y
# shellcheck disable=SC1090
[ -f "$HOME/.bashrc" ] && source "$HOME/.bashrc" || true

if command -v studio >/dev/null 2>&1; then
  studio doctor
else
  echo "studio wurde nach der Installation nicht im PATH gefunden." >&2
  exit 1
fi
