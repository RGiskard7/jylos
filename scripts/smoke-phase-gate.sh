#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
echo "== Jylos Phase Gate =="
echo "Root: $ROOT_DIR"

echo ""
echo "[1/3] Running unit/integration tests..."
mvn -f "$ROOT_DIR/jylos/pom.xml" clean test

echo ""
echo "[2/3] Building package..."
mvn -f "$ROOT_DIR/jylos/pom.xml" -DskipTests clean package

cat <<'EOF'

[3/3] Manual smoke checklist (required)
- Create/edit/save note
- Move note to trash and restore
- Create folder + subfolder
- Add/remove tags on note
- Change theme (light/dark/system)
- Open plugin manager

Mark this phase as DONE only if all items pass.
EOF

echo ""
echo "Phase gate automation completed successfully."
