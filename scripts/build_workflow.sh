#!/usr/bin/env bash
# Assemble dist/Aeye.alfredworkflow (zip) from ./src.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d src/lib/cursor_usage || ! -d src/lib/claude_monitor ]]; then
  echo "src/lib missing — running bootstrap_lib.sh first"
  ./scripts/bootstrap_lib.sh
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# .cache holds the developer's own overview output when run outside Alfred — never ship it.
rsync -a --delete \
  --exclude '__pycache__' --exclude '*.pyc' --exclude 'prefs.plist' \
  --exclude '.cache' --exclude '.DS_Store' \
  src/ "$STAGE/"

# Optional: include vendor for audit inside the package
mkdir -p "$STAGE/vendor"
rsync -a --delete \
  --exclude '__pycache__' --exclude '*.pyc' --exclude '.venv' --exclude '.DS_Store' \
  --exclude '/*/build' --exclude '*.egg-info' \
  vendor/ "$STAGE/vendor/"

mkdir -p dist
OUT="$ROOT/dist/Aeye.alfredworkflow"
rm -f "$OUT"
(
  cd "$STAGE"
  zip -qr "$OUT" .
)

echo "Wrote $OUT ($(du -sh "$OUT" | awk '{print $1}'))"
