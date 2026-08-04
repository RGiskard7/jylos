#!/usr/bin/env sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
EDITOR_DIR="$ROOT_DIR/jylos/editor-web"

if ! command -v npm >/dev/null 2>&1; then
    echo "npm is required to rebuild the CodeMirror editor bundle." >&2
    exit 1
fi

cd "$EDITOR_DIR"
npm ci
npm run build
