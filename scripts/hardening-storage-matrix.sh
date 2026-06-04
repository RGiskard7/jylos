#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
POM="$ROOT_DIR/jylos/pom.xml"

echo "== Jylos Storage Matrix Gate =="
echo "Root: $ROOT_DIR"

echo ""
echo "[1/3] SQLite contract/integration tests..."
mvn -f "$POM" -Dtest=SQLiteDBIntegrationTest,NoteDAOSQLiteTest,FolderDAOSQLiteContractTest test

echo ""
echo "[2/3] FileSystem contract tests..."
mvn -f "$POM" -Dtest=FileSystemDAOContractTest,NoteDAOFileSystemTest test

cat <<'EOF'

[3/3] Manual storage smoke (required)
- Run app in SQLite mode and validate core flows
- Run app in FileSystem mode and validate same flows
- Confirm parity in: create/edit/save, trash/restore, folders/subfolders, tags
EOF

echo ""
echo "Storage matrix gate completed successfully."
