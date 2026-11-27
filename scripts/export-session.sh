#!/usr/bin/env bash
set -euo pipefail

# Idempotent OpenCode session export into git-crypt protected session/ directory.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SESSION_ID="${1:-${OPENCODE_SESSION_ID:-}}"
if [ -z "$SESSION_ID" ]; then
  echo "ERROR: Pass session ID as first argument or set OPENCODE_SESSION_ID." >&2
  exit 1
fi

if ! grep -q 'session/\*\.json filter=git-crypt' .gitattributes; then
  echo "ERROR: .gitattributes does not protect session/*.json with git-crypt!" >&2
  exit 1
fi

OPENCODE_BIN="$(command -v opencode || echo /root/.opencode/bin/opencode)"
if [ ! -x "$OPENCODE_BIN" ]; then
  echo "ERROR: opencode binary not found." >&2
  exit 1
fi

mkdir -p session
OUT_FILE="session/${SESSION_ID}.json"
TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

"$OPENCODE_BIN" session export "$SESSION_ID" > "$TMP_FILE"
if [ ! -s "$TMP_FILE" ]; then
  echo "ERROR: Exported session file is empty." >&2
  exit 1
fi

mv "$TMP_FILE" "$OUT_FILE"
chmod 600 "$OUT_FILE"

echo "Exported session to $OUT_FILE ($(wc -c < "$OUT_FILE") bytes)."
echo "Verifying git-crypt encryption rule:"
git check-attr filter -- "$OUT_FILE"
